/**
 * Copyright 2018 SpinnerPlusPlus.ml
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package giraph.lri.rrojas.rankdegree;

import it.unimi.dsi.fastutil.shorts.ShortArrayList;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Random;
import org.apache.giraph.aggregators.DoubleSumAggregator;
import org.apache.giraph.aggregators.LongSumAggregator;
import org.apache.giraph.edge.Edge;
import org.apache.giraph.graph.AbstractComputation;
import org.apache.giraph.graph.Vertex;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.LongWritable;
import com.google.common.collect.Lists;

import giraph.ml.grafos.okapi.spinner.EdgeValue;
import giraph.ml.grafos.okapi.spinner.PartitionMessage;
import giraph.ml.grafos.okapi.spinner.VertexValue;

/**
 * Extends the Spinner edge-based balanced k-way partitioning of a graph with
 * an optimized initialization.
 * 
 * @author adnan
 * 
 */
//@SuppressWarnings("unused")
public class Spinner_BGRAP_init extends LPGPartitionner {
	public static class ComputeNewPartition extends
			// AbstractComputation<LongWritable, VertexValue, EdgeValue, PartitionMessage,
			// NullWritable> {
			AbstractComputation<LongWritable, VertexValue, EdgeValue, PartitionMessage, LongWritable> {
		private ShortArrayList maxIndices = new ShortArrayList();
		private Random rnd = new Random();
		//
		protected String[] demandAggregatorNames;
		protected int[] partitionFrequency;
		/**
		 * connected partitions
		 */
		private ShortArrayList pConnect;
		/**
		 * Adnan : Edge-count in each partition (
		 */
		protected long[] loads;
		/**
		 * Adnan : Vertex-count in each partition (
		 */
		//private long[//vCountnt;
		/**
		 * Adnan: the balanced capacity of a partition |E|/K, |V|/K
		 */
		private long totalEdgeCapacity;
		private short numberOfPartitions;
		private short repartition;
		private double additionalCapacity;
		private double lambda;

		/**
		 * Adnan : compute the penalty function Load/EdgeBCapacity
		 * 
		 * @param newPartition
		 * @return
		 */
		private double computeW(int newPartition) {
			return new BigDecimal(((double) loads[newPartition]) / totalEdgeCapacity).setScale(3, BigDecimal.ROUND_CEILING)
					.doubleValue();
		}

		/*
		 * Request migration to a new partition
		 * Adnan : update in order to recompute the number of vertex
		 */
		private void requestMigration(Vertex<LongWritable, VertexValue, EdgeValue> vertex, int numberOfEdges,
				short currentPartition, short newPartition) {
			vertex.getValue().setNewPartition(newPartition);
			
			aggregate(demandAggregatorNames[newPartition], new LongWritable(numberOfEdges));
			loads[newPartition] += numberOfEdges;
			loads[currentPartition] -= numberOfEdges;
			
			//aggregate(demandAggregatorNames[newPartition], new LongWritable(1));
			//vCount[newPartition] += 1;
			//vCount[currentPartition] -= 1;
		}

		/*
		 * Update the neighbor labels when they migrate
		 */
		protected void updateNeighborsPartitions(Vertex<LongWritable, VertexValue, EdgeValue> vertex,
				Iterable<PartitionMessage> messages) {
			for (PartitionMessage message : messages) {
				LongWritable otherId = new LongWritable(message.getSourceId());
				EdgeValue oldValue = vertex.getEdgeValue(otherId);
				vertex.setEdgeValue(otherId, new EdgeValue(message.getPartition(), oldValue.getWeight(), 
						oldValue.isVirtualEdge()));
			}
		}

		/*
		 * Compute the occurrences of the labels in the neighborhood Adnan : could we
		 * use an heuristic that also consider in how many edge/vertex are present the
		 * label?
		 */
		private int computeNeighborsLabels(Vertex<LongWritable, VertexValue, EdgeValue> vertex) {
			Arrays.fill(partitionFrequency, 0);
			int totalLabels = 0,
					localEdges = 0,
					interEdges = 0;
			short partition;
			pConnect = new ShortArrayList();
			//long  real=0;
			for (Edge<LongWritable, EdgeValue> e : vertex.getEdges()) {
				partition = e.getValue().getPartition();
				partitionFrequency[e.getValue().getPartition()] += e.getValue().getWeight();
				totalLabels += e.getValue().getWeight();
				if (e.getValue().getPartition() == vertex.getValue().getCurrentPartition()) {
					localEdges++;
				} else {
					if( ! pConnect.contains(partition))
						pConnect.add(partition);
					interEdges++;
				}

				/*
				if (!e.getValue().isVirtualEdge()) {
					real++;						
				}*/

			}
			//aggregate(AGG_REAL_LOCAL_EDGES, new LongWritable(real));
			// update cut edges stats
			aggregate(AGGREGATOR_LOCALS, new LongWritable(localEdges));
			// ADNAN : update Total Comm Vol. State
			aggregate(AGG_EDGE_CUTS, new LongWritable(interEdges));

			return totalLabels;
		}

		/*
		 * Choose a random partition with preference to the current
		 */
		protected short chooseRandomPartitionOrCurrent(short currentPartition) {
			short newPartition;
			if (maxIndices.size() == 1) {
				newPartition = maxIndices.get(0);
			} else {
				// break ties randomly unless current
				if (maxIndices.contains(currentPartition)) {
					newPartition = currentPartition;
				} else {
					newPartition = maxIndices.get(rnd.nextInt(maxIndices.size()));
				}
			}
			return newPartition;
		}

		/*
		 * Choose deterministically on the label with preference to the current
		 */
		protected short chooseMinLabelPartition(short currentPartition) {
			short newPartition;
			if (maxIndices.size() == 1) {
				newPartition = maxIndices.get(0);
			} else {
				if (maxIndices.contains(currentPartition)) {
					newPartition = currentPartition;
				} else {
					newPartition = maxIndices.get(0);
				}
			}
			return newPartition;
		}

		/*
		 * Choose a random partition regardless
		 */
		protected short chooseRandomPartition() {
			short newPartition;
			if (maxIndices.size() == 1) {
				newPartition = maxIndices.get(0);
			} else {
				newPartition = maxIndices.get(rnd.nextInt(maxIndices.size()));
			}
			return newPartition;
		}

		/*
		 * Compute the new partition according to the neighborhood labels and the
		 * partitions' loads
		 * Adnan : to update
		 */
		protected short computeNewPartition(Vertex<LongWritable, VertexValue, EdgeValue> vertex, int totalLabels) {
			short currentPartition = vertex.getValue().getCurrentPartition();
			short newPartition = -1;
			double bestState = -Double.MAX_VALUE;
			double currentState = 0;
			maxIndices.clear();
			for (short i = 0; i < numberOfPartitions + repartition; i++) {
				// original LPA
				double LPA = ((double) partitionFrequency[i]) / totalLabels;
				// penalty function
				double PF = lambda * computeW(i);
				// compute the rank and make sure the result is > 0
				double H = lambda + LPA - PF;
				if (i == currentPartition) {
					currentState = H;
				}
				if (H > bestState) {
					bestState = H;
					maxIndices.clear();
					maxIndices.add(i);
				} else if (H == bestState) {
					maxIndices.add(i);
				}
			}
			newPartition = chooseRandomPartitionOrCurrent(currentPartition);
			// update state stats
			aggregate(AGGREGATOR_STATE, new DoubleWritable(currentState));

			return newPartition;
		}

		@Override
		public void compute(Vertex<LongWritable, VertexValue, EdgeValue> vertex, Iterable<PartitionMessage> messages)
				throws IOException {
			boolean isActive = messages.iterator().hasNext();
			short currentPartition = vertex.getValue().getCurrentPartition();
			int numberOfEdges = vertex.getNumEdges();

			// update neighbors partitions
			updateNeighborsPartitions(vertex, messages);

			// count labels occurrences in the neighborhood
			int totalLabels = computeNeighborsLabels(vertex);

			// compute the most attractive partition
			short newPartition = computeNewPartition(vertex, totalLabels);

			// request migration to the new destination
			if (newPartition != currentPartition && isActive) {
				requestMigration(vertex, numberOfEdges, currentPartition, newPartition);
			}
			// System.out.println("Vertex: " + vertex.getId().get() + " cpart: " +
			// currentPartition + " npart: " + newPartition);

		}

		/**
		 * Adnan : to add the max VBalance, AGG_VLoad, etc.
		 */
		@Override
		public void preSuperstep() {
			additionalCapacity = getContext().getConfiguration().getFloat(ADDITIONAL_CAPACITY,
					DEFAULT_ADDITIONAL_CAPACITY);
			numberOfPartitions = (short) getContext().getConfiguration().getInt(NUM_PARTITIONS, DEFAULT_NUM_PARTITIONS);
			repartition = (short) getContext().getConfiguration().getInt(REPARTITION, DEFAULT_REPARTITION);
			lambda = getContext().getConfiguration().getFloat(LAMBDA, DEFAULT_LAMBDA);
			partitionFrequency = new int[numberOfPartitions + repartition];
			loads = new long[numberOfPartitions + repartition];
			demandAggregatorNames = new String[numberOfPartitions + repartition];			
			totalEdgeCapacity = Math.round(
					(getTotalNumEdges() * (1 + additionalCapacity) / (numberOfPartitions + repartition)));

			// cache loads for the penalty function
			// Adnan : get the Vertex balance penality
			for (int i = 0; i < numberOfPartitions + repartition; i++) {
				demandAggregatorNames[i] = AGG_MIGRATION_DEMAND_PREFIX + i;
				loads[i] = ((LongWritable) getAggregatedValue(AGG_EGDES_LOAD_PREFIX + i)).get();
				//vCount[i] = ((LongWritable) getAggregatedValue(AGG_VERTEX_COUNT_PREFIX + i)).get();
			}
		}
	}

	public static class ComputeMigration extends
			// AbstractComputation<LongWritable, VertexValue, EdgeValue, NullWritable,
			// PartitionMessage> {
			AbstractComputation<LongWritable, VertexValue, EdgeValue, LongWritable, PartitionMessage> {
		private Random rnd = new Random();
		protected String[] loadAggregatorNames;
		private double[] migrationProbabilities;
		protected short numberOfPartitions;
		protected short repartition;
		private double additionalCapacity;
		
		/**
		 * Store the current vertices-count of each partition 
		 */
		//private String[] vertexCountAggregatorNames;

		private void migrate(Vertex<LongWritable, VertexValue, EdgeValue> vertex, short currentPartition,
				short newPartition) {
			vertex.getValue().setCurrentPartition(newPartition);
			// update partitions loads
			int numberOfEdges = vertex.getNumEdges();
			aggregate(loadAggregatorNames[currentPartition], new LongWritable(-numberOfEdges));
			aggregate(loadAggregatorNames[newPartition], new LongWritable(numberOfEdges));
			
			// Adnan : update partition's vertices count
			//aggregate(vertexCountAggregatorNames[currentPartition], new LongWritable(-1));
			//aggregate(vertexCountAggregatorNames[newPartition], new LongWritable(1));
			
			// Adnan : to tell other that 'i am migrating'
			// Adnan : increment the total number of migration'
			aggregate(AGGREGATOR_MIGRATIONS, new LongWritable(1));
			// inform the neighbors
			PartitionMessage message = new PartitionMessage(vertex.getId().get(), newPartition);
			sendMessageToAllEdges(vertex, message);
		}

		@Override
		public void compute(Vertex<LongWritable, VertexValue, EdgeValue> vertex,
				// Iterable<NullWritable> messages) throws IOException {
				Iterable<LongWritable> messages) throws IOException {
			if (messages.iterator().hasNext()) {
				throw new RuntimeException("messages in the migration step!");
			}
			short currentPartition = vertex.getValue().getCurrentPartition();
			short newPartition = vertex.getValue().getNewPartition();
			if (currentPartition == newPartition) {
				return;
			}
			double migrationProbability = migrationProbabilities[newPartition];
			if (rnd.nextDouble() < migrationProbability) {
				migrate(vertex, currentPartition, newPartition);
			} else {
				vertex.getValue().setNewPartition(currentPartition);
			}
		}

		@Override
		public void preSuperstep() {
			additionalCapacity = getContext().getConfiguration().getFloat(ADDITIONAL_CAPACITY,
					DEFAULT_ADDITIONAL_CAPACITY);
			numberOfPartitions = (short) getContext().getConfiguration().getInt(NUM_PARTITIONS, DEFAULT_NUM_PARTITIONS);
			repartition = (short) getContext().getConfiguration().getInt(REPARTITION, DEFAULT_REPARTITION);
			long totalEdgeCapacity = Math.round(
					(getTotalNumEdges() * (1 + additionalCapacity) / (numberOfPartitions + repartition)));
			migrationProbabilities = new double[numberOfPartitions + repartition];
			loadAggregatorNames = new String[numberOfPartitions + repartition];
			//vertexCountAggregatorNames = new String[numberOfPartitions + repartition];
			// cache migration probabilities per destination partition
			for (int i = 0; i < numberOfPartitions + repartition; i++) {
				loadAggregatorNames[i] = AGG_EGDES_LOAD_PREFIX + i;
				//vertexCountAggregatorNames[i] = AGG_VERTEX_COUNT_PREFIX + i;				
				
				long load = ((LongWritable) getAggregatedValue(loadAggregatorNames[i])).get();
				//long vCount = ((LongWritable) getAggregatedValue(vertexCountAggregatorNames[i])).get();
				
				long demand = ((LongWritable) getAggregatedValue(AGG_MIGRATION_DEMAND_PREFIX + i)).get();
				long remainingCapacity = totalEdgeCapacity - load;
				if (demand == 0 || remainingCapacity <= 0) {
					migrationProbabilities[i] = 0;
				} else {
					migrationProbabilities[i] = ((double) (remainingCapacity)) / demand;
				}
			}
		}
	}

	public static class PotentialVerticesInitializer
			extends LPGPartitionner.PotentialVerticesInitializer {
		

/*
		public void compute(Vertex<LongWritable, VertexValue, EdgeValue> vertex, Iterable<PartitionMessage> messages)
				throws IOException {
			short partition = vertex.getValue().getCurrentPartition();
			if (partition == -1 && vertex.getValue().getRealOutDegree() > outDegreeThreshold) {
				// initialize only hub vertices
				partition = (short) rnd.nextInt(numberOfPartitions);

				// }
				// necessary to the label
				// if (partition != -1) {

				aggregate(loadAggregatorNames[partition], new LongWritable(vertex.getNumEdges()));
				//aggregate(vertexCountAggregatorNames[partition], new LongWritable(1));
				
				vertex.getValue().setCurrentPartition(partition);
				vertex.getValue().setNewPartition(partition);
				PartitionMessage message = new PartitionMessage(vertex.getId().get(), partition);
				sendMessageToAllEdges(vertex, message);

				aggregate(AGG_INITIALIZED_VERTICES, new LongWritable(1));
				aggregate(AGG_FIRST_LOADED_EDGES, new LongWritable(vertex.getNumEdges()));
			}

			aggregate(AGG_UPPER_TOTAL_COMM_VOLUME, new LongWritable(
					Math.min(numberOfPartitions, vertex.getNumEdges())
					));
		}*/
	}

	public static class Repartitioner
			extends AbstractComputation<LongWritable, VertexValue, EdgeValue, PartitionMessage, PartitionMessage> {
		private Random rnd = new Random();
		private String[] loadAggregatorNames;
		private int numberOfPartitions;
		private short repartition;
		private double migrationProbability;

		/**
		 * Store the current vertices-count of each partition 
		 */
		//private String[] vertexCountAggregatorNames;

		@Override
		public void compute(Vertex<LongWritable, VertexValue, EdgeValue> vertex, Iterable<PartitionMessage> messages)
				throws IOException {
			short partition;
			short currentPartition = vertex.getValue().getCurrentPartition();
			// down-scale
			if (repartition < 0) {
				if (currentPartition >= numberOfPartitions + repartition) {
					partition = (short) rnd.nextInt(numberOfPartitions + repartition);
				} else {
					partition = currentPartition;
				}
				// up-scale
			} else if (repartition > 0) {
				if (rnd.nextDouble() < migrationProbability) {
					partition = (short) (numberOfPartitions + rnd.nextInt(repartition));
				} else {
					partition = currentPartition;
				}
			} else {
				throw new RuntimeException("Repartitioner called with " + REPARTITION + " set to 0");
			}
			aggregate(loadAggregatorNames[partition], new LongWritable(vertex.getNumEdges()));
			//aggregate(vertexCountAggregatorNames[partition], new LongWritable(1));
			
			vertex.getValue().setCurrentPartition(partition);
			vertex.getValue().setNewPartition(partition);
			PartitionMessage message = new PartitionMessage(vertex.getId().get(), partition);
			sendMessageToAllEdges(vertex, message);			

			aggregate(AGG_UPPER_TOTAL_COMM_VOLUME, new LongWritable(
					Math.min(numberOfPartitions+repartition, vertex.getNumEdges())
					));
		}

		@Override
		public void preSuperstep() {
			numberOfPartitions = getContext().getConfiguration().getInt(NUM_PARTITIONS, DEFAULT_NUM_PARTITIONS);
			repartition = (short) getContext().getConfiguration().getInt(REPARTITION, DEFAULT_REPARTITION);
			migrationProbability = ((double) repartition) / (repartition + numberOfPartitions);
			loadAggregatorNames = new String[numberOfPartitions + repartition];
			//vertexCountAggregatorNames = new String[numberOfPartitions + repartition];
			for (int i = 0; i < numberOfPartitions + repartition; i++) {
				loadAggregatorNames[i] = AGG_EGDES_LOAD_PREFIX + i;
				//vertexCountAggregatorNames[i] = AGG_VERTEX_COUNT_PREFIX + i;
			}
		}
	}

	public static class PartitionerMasterCompute extends SuperPartitionerMasterCompute {

		@Override
		public void initialize() throws InstantiationException, IllegalAccessException {
			maxIterations = getContext().getConfiguration().getInt(MAX_ITERATIONS_LP, DEFAULT_MAX_ITERATIONS);

			// DEFAULT_NUM_PARTITIONS = getConf().getMaxWorkers()*getConf().get();

			numberOfPartitions = getContext().getConfiguration().getInt(NUM_PARTITIONS, DEFAULT_NUM_PARTITIONS);
			convergenceThreshold = getContext().getConfiguration().getFloat(CONVERGENCE_THRESHOLD,
					DEFAULT_CONVERGENCE_THRESHOLD);
			repartition = (short) getContext().getConfiguration().getInt(REPARTITION, DEFAULT_REPARTITION);
			windowSize = getContext().getConfiguration().getInt(WINDOW_SIZE, DEFAULT_WINDOW_SIZE);
			states = Lists.newLinkedList();
			// Create aggregators for each partition
			loadAggregatorNames = new String[numberOfPartitions + repartition];
			//vertexCountAggregatorNames = new String[numberOfPartitions + repartition];
			for (int i = 0; i < numberOfPartitions + repartition; i++) {
				loadAggregatorNames[i] = AGG_EGDES_LOAD_PREFIX + i;
				registerPersistentAggregator(loadAggregatorNames[i], LongSumAggregator.class);
				registerAggregator(AGG_MIGRATION_DEMAND_PREFIX + i, LongSumAggregator.class);

				//vertexCountAggregatorNames[i] = AGG_VERTEX_COUNT_PREFIX + i;
				//registerPersistentAggregator(vertexCountAggregatorNames[i], LongSumAggregator.class);
				// registerAggregator(AGGREGATOR_DEMAND_PREFIX + i, LongSumAggregator.class);
			}
			registerAggregator(AGGREGATOR_STATE, DoubleSumAggregator.class);
			registerAggregator(AGGREGATOR_LOCALS, LongSumAggregator.class);
			registerAggregator(AGGREGATOR_MIGRATIONS, LongSumAggregator.class);

			// Added by Adnan
			registerAggregator(AGG_UPDATED_VERTICES, LongSumAggregator.class);
			registerAggregator(AGG_INITIALIZED_VERTICES, LongSumAggregator.class);
			registerAggregator(AGG_FIRST_LOADED_EDGES, LongSumAggregator.class);
			registerPersistentAggregator(AGG_UPPER_TOTAL_COMM_VOLUME, LongSumAggregator.class);
			registerAggregator(AGG_EDGE_CUTS, LongSumAggregator.class);
			
			super.init();
		}

		private static boolean computeStates=false;
		private static int lastStep=Integer.MAX_VALUE;
		@Override
		public void compute() {
			int superstep = (int) getSuperstep();
			if(computeStates) {
				//run additional step to get some graph stat
				if(superstep==lastStep+1)
					setComputation(ComputeGraphPartitionStatistics.class);
				else {
					System.out.println("Finish stats.");
					haltComputation();
					updatePartitioningQuality();
					saveTimersStats(false, totalMigrations);
				}
			} else {
				if (superstep == 0) {
					//at this stage #edges , #vertices is not known
					setComputation(ConverterPropagate.class);
				} else if (superstep == 1) {
					//if(numberOfPartitions > getTotalNumVertices())
					//getContext().getConfiguration().setInt(NUM_PARTITIONS, (int) getTotalNumVertices());
					
					//System.out.println("before stp1 "+ getTotalNumEdges());
					setComputation(ConverterUpdateEdges.class);
				} else if (superstep == 2) {
					//System.out.println("before stp2 "+ getTotalNumEdges());			
					if (repartition != 0) {
						setComputation(Repartitioner.class);
					} else {
						setComputation(PotentialVerticesInitializer.class);
					}
				} else if (superstep == 3) {
					//System.out.println("after stp2 "+ getTotalNumEdges());
					getContext().getCounter("Partitioning Initialization", AGG_INITIALIZED_VERTICES)
							.increment(((LongWritable) getAggregatedValue(AGG_INITIALIZED_VERTICES)).get());
					setComputation(ComputeFirstPartition.class);
				} else if (superstep == 4) {
	
					getContext().getCounter("Partitioning Initialization", AGG_UPDATED_VERTICES)
							.increment(((LongWritable) getAggregatedValue(AGG_UPDATED_VERTICES)).get());
					setComputation(ComputeFirstMigration.class);
				} else {
					switch (superstep % 2) {
					case 0:
						setComputation(ComputeMigration.class);
						break;
					case 1:
						setComputation(ComputeNewPartition.class);
						break;
					}
				}
	
				boolean hasConverged = false;
				if (superstep > 5) {
					if (superstep % 2 == 0) {
						hasConverged = algorithmConverged(superstep);
					}
				}
				super.printStats(superstep);
				super.updateStats();
				// LP iteration = 2 super-steps, LP process start after 3 super-steps 
				if (hasConverged || superstep >= (maxIterations*2+2)) {
					System.out.println("Halting computation: " + hasConverged);
					computeStates=true;
					//haltComputation();
					//setCounters();
					lastStep = superstep;
				}
			}
		}
	}

	public static class ComputeFirstPartition extends ComputeNewPartition {

		/*
		 * Request migration to a new partition Adnan : update in order to recompute the
		 * number of vertex
		 */
		private void requestMigration(Vertex<LongWritable, VertexValue, EdgeValue> vertex, int numberOfEdges,
				short currentPartition, short newPartition) {
			vertex.getValue().setNewPartition(newPartition);
			aggregate(demandAggregatorNames[newPartition], new LongWritable(numberOfEdges));
			loads[newPartition] += numberOfEdges;
			if (currentPartition != -1)
				loads[currentPartition] -= numberOfEdges;
		}

		/*
		 * Compute the occurrences of the labels in the neighborhood Adnan : could we
		 * use an heuristic that also consider in how many edge/vertex are present the
		 * label?
		 */
		private int computeNeighborsLabels(Vertex<LongWritable, VertexValue, EdgeValue> vertex) {
			Arrays.fill(partitionFrequency, 0);
			int totalLabels = 0;
			int localEdges = 0;
			int interEdges = 0;
			for (Edge<LongWritable, EdgeValue> e : vertex.getEdges()) {
				if (e.getValue().getPartition() == -1)
					continue;

				partitionFrequency[e.getValue().getPartition()] += e.getValue().getWeight();
				totalLabels += e.getValue().getWeight();

				if (e.getValue().getPartition() == vertex.getValue().getCurrentPartition()) {
					localEdges++;
				} else {
					interEdges++;
				}

			}
			// update cut edges stats
			aggregate(AGG_EDGE_CUTS, new LongWritable(interEdges));
			aggregate(AGGREGATOR_LOCALS, new LongWritable(localEdges));

			return totalLabels;
		}

		@Override
		public void compute(Vertex<LongWritable, VertexValue, EdgeValue> vertex, Iterable<PartitionMessage> messages)
				throws IOException {
			boolean isActive = messages.iterator().hasNext();
			short currentPartition = vertex.getValue().getCurrentPartition();

			int numberOfEdges = vertex.getNumEdges();

			// update neighbors partitions
			updateNeighborsPartitions(vertex, messages);
			short newPartition = currentPartition;
			// if (currentPartition == -1) {
			// count labels occurrences in the neighborhood
			int totalLabels = computeNeighborsLabels(vertex);
			if (totalLabels > 0) {
				// compute the most attractive partition
				newPartition = computeNewPartition(vertex, totalLabels);

				// request migration to the new destination
				if (newPartition != currentPartition && isActive) {
					requestMigration(vertex, numberOfEdges, currentPartition, newPartition);
					aggregate(AGG_UPDATED_VERTICES, new LongWritable(1));
				}
			}
			// }
			// System.out.println("Vertex: " + vertex.getId().get() + " cpart: " +
			// currentPartition + " npart: " + newPartition);
		}
	}

	public static class ComputeFirstMigration extends ComputeMigration {
		private Random rnd = new Random();

		private void migrate(Vertex<LongWritable, VertexValue, EdgeValue> vertex, short currentPartition,
				short newPartition) {
			vertex.getValue().setCurrentPartition(newPartition);
			// update partitions loads
			int numberOfEdges = vertex.getNumEdges();
			if (currentPartition != -1) {
				aggregate(loadAggregatorNames[currentPartition], new LongWritable(-numberOfEdges));
				//aggregate(vertexCountAggregatorNames[currentPartition], new LongWritable(-1));
			}
			
			aggregate(loadAggregatorNames[newPartition], new LongWritable(numberOfEdges));
			//aggregate(vertexCountAggregatorNames[newPartition], new LongWritable(1));
			
			
			aggregate(AGGREGATOR_MIGRATIONS, new LongWritable(1));
			// inform the neighbors
			// Adnan : to tell other that 'i am migrating'
			PartitionMessage message = new PartitionMessage(vertex.getId().get(), newPartition);
			sendMessageToAllEdges(vertex, message);
		}

		@Override
		public void compute(Vertex<LongWritable, VertexValue, EdgeValue> vertex,
				// Iterable<NullWritable> messages) throws IOException {
				Iterable<LongWritable> messages) throws IOException {
			if (messages.iterator().hasNext()) {
				throw new RuntimeException("messages in the migration step!");
			}
			short currentPartition = vertex.getValue().getCurrentPartition();
			short newPartition = vertex.getValue().getNewPartition();
			
			if(newPartition == -1) {
				newPartition = (short) rnd.nextInt(numberOfPartitions+repartition);
				vertex.getValue().setNewPartition(newPartition);
			}
			if (currentPartition == newPartition) {
				return;
			}
			
			migrate(vertex, currentPartition, newPartition);
			// System.out.println("Vertex: " + vertex.getId().get() + " cpart: " +
			// currentPartition + " npart: " + newPartition);
		}
	}
}
