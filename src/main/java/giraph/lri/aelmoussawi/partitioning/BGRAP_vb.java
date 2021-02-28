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
package giraph.lri.aelmoussawi.partitioning;

import it.unimi.dsi.fastutil.shorts.ShortArrayList;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedList;
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
 * Balance the partitioning w.r.t. the number of vertices while 
 * minimizing the edge cut 
 * to update label : check the partition that minimize the global EC
 * 
 */
@SuppressWarnings("unused")
public class BGRAP_vb extends LPGPartitionner{

	/**
	 * check the labels in the neighbor of the vertex 
	 * then compute the frequency of each label while 
	 * considering the vertex balance and the edge cut
	 * @author Adnan
	 *
	 */
	public static class ComputeNewPartition extends
			// AbstractComputation<LongWritable, VertexValue, EdgeValue, PartitionMessage,
			// NullWritable> {
			AbstractComputation<LongWritable, VertexValue, EdgeValue, PartitionMessage, LongWritable> {
		protected ShortArrayList maxIndices = new ShortArrayList();
		protected Random rnd = new Random();
		//
		protected String[] vDemandAggregatorNames;
		protected int[] partitionFrequency;
		/**
		 * connected partitions
		 */
		protected ShortArrayList pConnect;
		/**
		 * Adnan : verteex Edge-cut holden in each partition (
		 */
		protected long[] vertexEcCount;
		/**
		 * Adnan : total Edge-cut holden in each partition (
		 */
		//protected long[] eCutsPerPartition;
		/**
		 * Adnan : Vertex-count in each partition (
		 */
		protected long[] vCount;
		/**
		 * Adnan: the balanced capacity of a partition |E|/K, |V|/K
		 */
		protected long totalEdgeCapacity;
		protected long totalECutsCapacity;
		protected long totalVertexCapacity;
		protected short numberOfPartitions;
		protected short repartition;
		protected double additionalCapacity;
		protected double lambda;
		protected long totalNumEdges;
		protected float kappa;
		protected boolean directedGraph;
		protected long externalEdges, localEdges;
		
		/**
		 * Adnan : compute the ec balance penalty function : Load/capacity
		 * 
		 * @param newPartition
		 * @return
		 */
		protected double computeECutsBalance(int newPartition, long numOutEdges) {
			//numOutEdges could be zero => Exception
			if(numOutEdges==0) return 0;
			return	(double) (vertexEcCount[newPartition]) / numOutEdges;
			//return	(double) (vertexEcCount[newPartition]) / (numberOfPartitions + repartition);
		
		}

		/**
		 * Adnan : compute the vertex balance penalty function Load/capacity
		 * 
		 * @param newPartition
		 * @return
		 */
		protected double computeVertexBalance(int newPartition) {
			return new BigDecimal(((double) vCount[newPartition]) / getTotalNumVertices()).setScale(3, BigDecimal.ROUND_CEILING)
					.doubleValue();
			/*return new BigDecimal(((double) vCount[newPartition]) / totalVertexCapacity).setScale(3, BigDecimal.ROUND_CEILING)
					.doubleValue();*/
		}

		/*
		 * Request migration to a new partition
		 * Adnan : update in order to recompute the number of vertex
		 */
		private void requestMigration(Vertex<LongWritable, VertexValue, EdgeValue> vertex, int numberOfEdges,
				short currentPartition, short newPartition) {
			vertex.getValue().setNewPartition(newPartition);
			
			aggregate(vDemandAggregatorNames[newPartition], new LongWritable(1));
			/*
			eCutsPerPartition[newPartition] += vertexEcCount[currentPartition] 
					-vertexEcCount[newPartition];
			eCutsPerPartition[currentPartition] += vertexEcCount[newPartition]
					-vertexEcCount[currentPartition];*/
			
			vCount[newPartition] += 1;
			vCount[currentPartition] -= 1;
		}

		/*
		 * Update the neighbor labels when they migrate
		 */
		protected void updateNeighborsPartitions(Vertex<LongWritable, VertexValue, EdgeValue> vertex,
				Iterable<PartitionMessage> messages) {
			for (PartitionMessage message : messages) {
				LongWritable otherId = new LongWritable(message.getSourceId());
				EdgeValue oldValue = vertex.getEdgeValue(otherId);
				
				vertex.setEdgeValue(otherId, new EdgeValue(message.getPartition(), 
						oldValue.getWeight(), oldValue.isVirtualEdge()));
			}
		}

		/*
		 * Compute the occurrences of the labels in the neighborhood Adnan : could we
		 * use an heuristic that also consider in how many edge/vertex are present the
		 * label?
		 */
		private int computeNeighborsLabels(Vertex<LongWritable, VertexValue, EdgeValue> vertex) {
			Arrays.fill(partitionFrequency, 0);
			Arrays.fill(vertexEcCount, 0);
			int totalLabels = 0;
			localEdges = 0;
			externalEdges = 0;
			short p2;
			pConnect = new ShortArrayList();
			for (Edge<LongWritable, EdgeValue> e : vertex.getEdges()) {
				p2 = e.getValue().getPartition();
				partitionFrequency[p2] += e.getValue().getWeight();
				totalLabels += e.getValue().getWeight();
				
				if(directedGraph && e.getValue().isVirtualEdge()){
					continue;
				}
				
				//if is a local edge
				if (p2 == vertex.getValue().getCurrentPartition()) {
					localEdges++;
				}
				//if is an edgeCut 
				else {
					if( ! pConnect.contains(p2)) {
						pConnect.add(p2);
						//vertexCV[partition] += e.getValue().getWeight();
					}
					vertexEcCount[p2] ++;
				}
			}
			externalEdges = vertex.getValue().getRealOutDegree() - localEdges;
			
			vertexEcCount[vertex.getValue().getCurrentPartition()] = localEdges; 
			
			// update cut edges stats
			aggregate(AGGREGATOR_LOCALS, new LongWritable(localEdges));
			// ADNAN : update Total Comm Vol. State
			aggregate(AGG_EDGE_CUTS, new LongWritable(externalEdges));

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

		/**
		 * Compute the new partition according to the neighborhood labels and the
		 * balance/EC constraints
		 */
		protected short computeNewPartition(Vertex<LongWritable, VertexValue, EdgeValue> vertex, int totalLabels) {
			short currentPartition = vertex.getValue().getCurrentPartition();
			short newPartition = -1;
			double bestState = -Double.MAX_VALUE;
			double currentState = 0;
			long ec = ((LongWritable) getAggregatedValue(AGG_EDGE_CUTS)).get();
			long numOutEdges = vertex.getValue().getRealOutDegree();
			maxIndices.clear();
			for (short i = 0; i < numberOfPartitions + repartition; i++) {
				// original LPA
				double LPA = ((double) partitionFrequency[i]) / totalLabels;
				// penalty function
				double PF1 = computeECutsBalance(i, numOutEdges);
				//double PF1 = computeECutsBalance(i, localEdges);
				//System.out.println("pf1 " +PF1);
				//System.out.println("ecCount " +vertexEcCount[i]);
				//System.out.println("enum " +numOutEdges);
				double PF2 = computeVertexBalance(i);
				//System.out.println("pf2 " +PF2);
				//System.out.println("lpa " +LPA);
				// compute the rank and make sure the result is > 0
				double H = lambda + LPA + lambda*(kappa*PF1 - (1-kappa)*PF2);
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
		/**
		 * the computation step
		 */
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

		
		@Override
		public void preSuperstep() {
			directedGraph = getContext().getConfiguration().getBoolean(GRAPH_DIRECTED, DEFAULT_GRAPH_DIRECTED);

			if(directedGraph)
				totalNumEdges = ((LongWritable) getAggregatedValue(TOTAL_DIRECTED_OUT_EDGES)).get();
			else
				totalNumEdges = getTotalNumEdges();
			
			kappa = getContext().getConfiguration().getFloat(KAPPA, DEFAULT_KAPPA);
			
			additionalCapacity = getContext().getConfiguration().getFloat(ADDITIONAL_CAPACITY,
					DEFAULT_ADDITIONAL_CAPACITY);
			numberOfPartitions = (short) getContext().getConfiguration().getInt(NUM_PARTITIONS, DEFAULT_NUM_PARTITIONS);
			repartition = (short) getContext().getConfiguration().getInt(REPARTITION, DEFAULT_REPARTITION);
			lambda = getContext().getConfiguration().getFloat(LAMBDA, DEFAULT_LAMBDA);
			
			partitionFrequency = new int[numberOfPartitions + repartition];
			
			//eCutsPerPartition = new long[numberOfPartitions + repartition];
			vCount = new long[numberOfPartitions + repartition];
			vertexEcCount = new long[numberOfPartitions + repartition];
			
			vDemandAggregatorNames = new String[numberOfPartitions + repartition];			
						
			totalECutsCapacity = (long) Math.round(
					((double) totalNumEdges * (1 + additionalCapacity) / (numberOfPartitions + repartition)));
			
			totalVertexCapacity = (long) Math.round(
					((double) getTotalNumVertices() * (1 + additionalCapacity) / (numberOfPartitions + repartition)));
			// cache loads for the penalty function
			// Adnan : get the Vertex balance penality
			for (int i = 0; i < numberOfPartitions + repartition; i++) {
				vDemandAggregatorNames[i] = AGG_VERTEX_MIGRATION_DEMAND_PREFIX + i;
				vCount[i] = ((LongWritable) getAggregatedValue(AGG_VERTEX_COUNT_PREFIX + i)).get();
				//eCutsPerPartition[i] = ((LongWritable) getAggregatedValue(AGG_EC_COUNT_PREFIX + i)).get();
			}
		}
	}

	public static class ComputeMigration extends
			// AbstractComputation<LongWritable, VertexValue, EdgeValue, NullWritable,
			// PartitionMessage> {
			AbstractComputation<LongWritable, VertexValue, EdgeValue, LongWritable, PartitionMessage> {
		private Random rnd = new Random();
		protected String[] loadAggregatorNames;
		protected double[] migrationProbabilities;
		protected short numberOfPartitions;
		protected short repartition;
		protected double additionalCapacity;
		
		/**
		 * Store the current vertices-count of each partition 
		 */
		protected String[] vertexCountAggregatorNames;
		
		/**
		 * Store the current ec-count of each partition 
		 */
		//private String[] ecAggregatorNames;
		
		private long totalNumEdges;
		private boolean directedGraph;
		private float kappa;

		private void migrate(Vertex<LongWritable, VertexValue, EdgeValue> vertex, short currentPartition,
				short newPartition) {
			vertex.getValue().setCurrentPartition(newPartition);
			// update partitions loads
			//int numberOfEdges = vertex.getNumEdges();
			long numberOfEdges = vertex.getValue().getRealOutDegree();
			
			aggregate(loadAggregatorNames[currentPartition], new LongWritable(-numberOfEdges));
			aggregate(loadAggregatorNames[newPartition], new LongWritable(numberOfEdges));
			
			// Adnan : update partition's vertices count
			aggregate(vertexCountAggregatorNames[currentPartition], new LongWritable(-1));
			aggregate(vertexCountAggregatorNames[newPartition], new LongWritable(1));
			
			// Adnan : update partition's vertices count
			//aggregate(ecAggregatorNames[currentPartition], new LongWritable(-1));
			//aggregate(ecAggregatorNames[newPartition], new LongWritable(1));

			
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
			//migrate(vertex, currentPartition, newPartition);
			
			if (rnd.nextDouble() < migrationProbability) {
				migrate(vertex, currentPartition, newPartition);
			} else {
				vertex.getValue().setNewPartition(currentPartition);
			}
		}

		@Override
		public void preSuperstep() {
			directedGraph = getContext().getConfiguration().getBoolean(GRAPH_DIRECTED, DEFAULT_GRAPH_DIRECTED);

			if(directedGraph)
				totalNumEdges = ((LongWritable) getAggregatedValue(TOTAL_DIRECTED_OUT_EDGES)).get();
			else
				totalNumEdges = getTotalNumEdges();
			
			kappa = getContext().getConfiguration().getFloat(KAPPA, DEFAULT_KAPPA);
			
			additionalCapacity = getContext().getConfiguration().getFloat(ADDITIONAL_CAPACITY,
					DEFAULT_ADDITIONAL_CAPACITY);
			numberOfPartitions = (short) getContext().getConfiguration().getInt(NUM_PARTITIONS, DEFAULT_NUM_PARTITIONS);
			repartition = (short) getContext().getConfiguration().getInt(REPARTITION, DEFAULT_REPARTITION);
			long totalEdgeCapacity = (long) Math.round(
					((double) totalNumEdges * (1 + additionalCapacity) / (numberOfPartitions + repartition)));
			
			long totalVertexCapacity = (long) Math.round(
					((double) getTotalNumVertices() * (1 + additionalCapacity) / (numberOfPartitions + repartition)));
			
			migrationProbabilities = new double[numberOfPartitions + repartition];
			loadAggregatorNames = new String[numberOfPartitions + repartition];
			vertexCountAggregatorNames = new String[numberOfPartitions + repartition];
			//ecAggregatorNames = new String[numberOfPartitions + repartition];
			
			// cache migration probabilities per destination partition
			for (int i = 0; i < numberOfPartitions + repartition; i++) {
				loadAggregatorNames[i] = AGG_EGDES_LOAD_PREFIX + i;
				vertexCountAggregatorNames[i] = AGG_VERTEX_COUNT_PREFIX + i;
				//ecAggregatorNames [i] = AGG_EC_COUNT_PREFIX + i;
				
				long load = ((LongWritable) getAggregatedValue(loadAggregatorNames[i])).get();
				long vCount = ((LongWritable) getAggregatedValue(vertexCountAggregatorNames[i])).get();
				//long ecCount = ((LongWritable) getAggregatedValue(ecAggregatorNames[i])).get();
				
				
				long demand = ((LongWritable) getAggregatedValue(AGG_VERTEX_MIGRATION_DEMAND_PREFIX + i)).get();
				long remainingEcCapacity;//??
				long remainingVertexCapacity = totalVertexCapacity - vCount;
				if (demand == 0 || remainingVertexCapacity <= 0) {
					migrationProbabilities[i] = 0;
				} else {
					//
					migrationProbabilities[i] = ((double) (remainingVertexCapacity)) / demand;
				}
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
			windowSize = (int) getContext().getConfiguration().getInt(WINDOW_SIZE, DEFAULT_WINDOW_SIZE);
			states = Lists.newLinkedList();
			// Create aggregators for each partition
			loadAggregatorNames = new String[numberOfPartitions + repartition];
			vertexCountAggregatorNames = new String[numberOfPartitions + repartition];
			for (int i = 0; i < numberOfPartitions + repartition; i++) {
				loadAggregatorNames[i] = AGG_EGDES_LOAD_PREFIX + i;
				registerPersistentAggregator(loadAggregatorNames[i], LongSumAggregator.class);
				registerAggregator(AGG_VERTEX_MIGRATION_DEMAND_PREFIX + i, LongSumAggregator.class);

				vertexCountAggregatorNames[i] = AGG_VERTEX_COUNT_PREFIX + i;
				registerPersistentAggregator(vertexCountAggregatorNames[i], LongSumAggregator.class);
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
		
		
		private static boolean computeStates = false;
		private static int lastStep = Integer.MAX_VALUE;
		@Override
		public void compute() {
			int superstep = (int) getSuperstep();
			if (computeStates) {
				if (superstep == lastStep + 1)
					setComputation(ComputeGraphPartitionStatistics.class);
				else {
					System.out.println("Finish stats.");
					haltComputation();
					updatePartitioningQuality();
					saveTimersStats(false, totalMigrations);
				}
			} else {
				if (superstep == 0) {
					// at this stage #edges , #vertices is not known
					setComputation(ConverterPropagate.class);
				} else if (superstep == 1) {
					// if(numberOfPartitions > getTotalNumVertices())
					// getContext().getConfiguration().setInt(NUM_PARTITIONS, (int)
					// getTotalNumVertices());

					// System.out.println("before stp1 "+ getTotalNumEdges());
					setComputation(ConverterUpdateEdges.class);
				} else if (superstep == 2) {
					// System.out.println("before stp2 "+ getTotalNumEdges());
					if (repartition != 0) {
						setComputation(Repartitioner.class);
					} else {
						setComputation(PotentialVerticesInitializer.class);
					}
				} else if (superstep == 3) {
					// System.out.println("after stp2 "+ getTotalNumEdges());
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
				printStats(superstep);
				updateStats();
				
				// LP iteration = 2 super-steps, LP process start after 3 super-steps 
				if (hasConverged || superstep >= (maxIterations*2+2)) {
					System.out.println("Halting computation: " + hasConverged);
					computeStates = true;
					lastStep = superstep;
				}
			}
		}
	}

	public static class ComputeFirstPartition extends ComputeNewPartition{
		/*
		 * Request migration to a new partition Adnan : update in order to recompute the
		 * number of vertex
		 */
		private void requestMigration(Vertex<LongWritable, VertexValue, EdgeValue> vertex, long numberOfEdges,
				short currentPartition, short newPartition) {
			vertex.getValue().setNewPartition(newPartition);
			aggregate(vDemandAggregatorNames[newPartition], new LongWritable(1));
			vCount[newPartition] += 1;
			//eCutsPerPartition[newPartition] += vertexEcCount[currentPartition]-vertexEcCount[newPartition];
			
			if (currentPartition != -1) {
				vCount[currentPartition] -= 1;
				//eCutsPerPartition[currentPartition] += vertexEcCount[newPartition]-vertexEcCount[currentPartition];
			}
		}
		/*
		 * Compute the occurrences of the labels in the neighborhood Adnan : could we
		 * use an heuristic that also consider in how many edge/vertex are present the
		 * label?
		 */
		private int computeNeighborsLabels(Vertex<LongWritable, VertexValue, EdgeValue> vertex) {
			Arrays.fill(partitionFrequency, 0);
			Arrays.fill(vertexEcCount, 0);
			int totalLabels = 0,
					localEdges = 0;
			externalEdges = 0;
			short p2;
			pConnect = new ShortArrayList();
			for (Edge<LongWritable, EdgeValue> e : vertex.getEdges()) {
				p2 = e.getValue().getPartition();

				if(p2==-1) continue;

				partitionFrequency[p2] += e.getValue().getWeight();
				totalLabels += e.getValue().getWeight();
				
				if(directedGraph && e.getValue().isVirtualEdge()){
					continue;
				}
				
				//if is a local edge
				if (p2 == vertex.getValue().getCurrentPartition()) {
					localEdges++;
				}
				//if is an edgeCut 
				else {
					if( ! pConnect.contains(p2)) {
						pConnect.add(p2);
						//vertexCV[partition] += e.getValue().getWeight();
					}
					vertexEcCount[p2] ++;
				}
			}
			externalEdges = vertex.getValue().getRealOutDegree() - localEdges;
			
			if(vertex.getValue().getCurrentPartition()!=-1)
				vertexEcCount[vertex.getValue().getCurrentPartition()] = localEdges; 
			
			// update cut edges stats
			aggregate(AGGREGATOR_LOCALS, new LongWritable(localEdges));
			// ADNAN : update EC. State
			aggregate(AGG_EDGE_CUTS, new LongWritable(externalEdges));

			return totalLabels;
		}

		@Override
		public void compute(Vertex<LongWritable, VertexValue, EdgeValue> vertex, Iterable<PartitionMessage> messages)
				throws IOException {
			boolean isActive = messages.iterator().hasNext();
			short currentPartition = vertex.getValue().getCurrentPartition();

			long numberOfEdges = vertex.getValue().getRealOutDegree();

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

	public static class ComputeFirstMigration extends ComputeMigration{
		private Random rnd = new Random();

		private void migrate(Vertex<LongWritable, VertexValue, EdgeValue> vertex, short currentPartition,
				short newPartition) {
			vertex.getValue().setCurrentPartition(newPartition);
			// update partitions loads
			//int numberOfEdges = vertex.getNumEdges();
			long numberOfEdges = vertex.getValue().getRealOutDegree();
			if (currentPartition != -1) {
				aggregate(loadAggregatorNames[currentPartition], new LongWritable(-numberOfEdges));
				aggregate(vertexCountAggregatorNames[currentPartition], new LongWritable(-1));
			}
			
			aggregate(loadAggregatorNames[newPartition], new LongWritable(numberOfEdges));
			aggregate(vertexCountAggregatorNames[newPartition], new LongWritable(1));
			
			
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
