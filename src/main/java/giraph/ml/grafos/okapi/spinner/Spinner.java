/**
 * Copyright 2014 Grafos.ml
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
package giraph.ml.grafos.okapi.spinner;

import it.unimi.dsi.fastutil.shorts.ShortArrayList;

import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Random;
import org.apache.giraph.aggregators.DoubleSumAggregator;
import org.apache.giraph.aggregators.LongSumAggregator;
import org.apache.giraph.edge.Edge;
import org.apache.giraph.edge.EdgeFactory;
import org.apache.giraph.graph.AbstractComputation;
import org.apache.giraph.graph.Vertex;
import org.apache.giraph.master.DefaultMasterCompute;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.LongWritable;
import com.google.common.collect.Lists;

import giraph.format.personalized.PartitionedLongWritable;
import giraph.lri.aelmoussawi.partitioning.LPGPartitionner;
import giraph.lri.aelmoussawi.partitioning.LPGPartitionner.ComputeGraphPartitionStatistics;

/**
 * Implements the Spinner edge-based balanced k-way partitioning of a graph.
 * 
 * The algorithm partitions N vertices across k partitions, trying to keep the
 * number of edges in each partition similar. The algorithm is also able to
 * adapt a previous partitioning to a set of updates to the graph, e.g. adding
 * or removing vertices and edges. The algorithm can also adapt a previous
 * partitioning to updates to the number of partitions, e.g. adding or removing
 * partitions.
 * 
 * The algorithm works as follows:
 * 
 * 1) The vertices are assigned to partitions according to the following
 * heuristics: a) If we are computing a new partitioning, assign the vertex to a
 * random partition b) If we are adapting the partitioning to graph changes: (i)
 * If the vertex was previously partitioned, assign the previous label (ii) If
 * the vertex is new, assign a random partition c) If we are adapting to changes
 * to the number of partitions: (i) If we are adding partitions: assign the
 * vertex to one of the new partitions (uniformly at random) with probability p
 * (see paper), (ii) If we are removing partitions: assign vertices belonging to
 * the removed partitions to the other partitions uniformly at random. After the
 * vertices are initialized, they communicate their label to their neighbors,
 * and update the partition loads according to their assignments.
 * 
 * 2) Each vertex computes the score for each label based on loads and the
 * labels from incoming neighbors. If a new partition has higher score (or
 * depending on the heuristics used), the vertex decides to try to migrate
 * during the following superstep. Otherwise, it does nothing.
 * 
 * 3) Interested vertices try to migrate according to the ratio of vertices who
 * want to migrate to a partition i and the remaining capacity of partition i.
 * Vertices who succeed in the migration update the partition loads and
 * communicate their migration to their neighbors true a message.
 * 
 * 4) Point (2) and (3) keep on being alternating until convergence is reach,
 * hence till the global score of the partitioning does not change for a number
 * of times over a certain threshold.
 * 
 * Each vertex stores the position of its neighbors in the edge values, to avoid
 * re-communicating labels at each iteration also for non-migrating vertices.
 * 
 * Due to the random access to edges, this class performs much better when using
 * OpenHashMapEdges class provided with this code.
 * 
 * To use the partitioning computed by this class in Giraph, see
 * {@link PrefixHashPartitionerFactory}, {@link PrefixHashWorkerPartitioner},
 * and {@link PartitionedLongWritable}.
 * 
 * @author claudio
 * 
 */
@SuppressWarnings("unused")
public class Spinner extends LPGPartitionner {
	private static final String AGGREGATOR_LOAD_PREFIX = "AGG_LOAD_";
	private static final String AGGREGATOR_DEMAND_PREFIX = "AGG_DEMAND_";
	private static final String AGGREGATOR_STATE = "AGG_STATE";
	private static final String AGGREGATOR_MIGRATIONS = "AGG_MIGRATIONS";
	private static final String AGGREGATOR_LOCALS = "AGG_LOCALS";
	private static final String NUM_PARTITIONS = "spinner.numberOfPartitions";
	private static final int DEFAULT_NUM_PARTITIONS = 8;// 32;
	private static final String ADDITIONAL_CAPACITY = "spinner.additionalCapacity";
	private static final float DEFAULT_ADDITIONAL_CAPACITY = 0.05f;
	private static final String LAMBDA = "spinner.lambda";
	private static final float DEFAULT_LAMBDA = 1.0f;
	/**
	 * The maximum number of label propagation iteration
	 * <br> LP iteration = 2 Giraph super-steps
	 */
	private static final String MAX_ITERATIONS_LP = "spinner.MaxIterationsLP";
	private static final int DEFAULT_MAX_ITERATIONS = 290;// 10;//290;
	private static final String CONVERGENCE_THRESHOLD = "spinner.threshold";
	private static final float DEFAULT_CONVERGENCE_THRESHOLD = 0.001f;
	private static final String EDGE_WEIGHT = "spinner.weight";
	private static final byte DEFAULT_EDGE_WEIGHT = 1;
	private static final String REPARTITION = "spinner.repartition";
	private static final short DEFAULT_REPARTITION = 0;
	private static final String WINDOW_SIZE = "spinner.windowSize";
	private static final int DEFAULT_WINDOW_SIZE = 5;

	private static final String PARTITION_COUNTER_GROUP = "Partitioning Counters";
	private static final String MIGRATIONS_COUNTER = "Migrations";
	private static final String ITERATIONS_COUNTER = "Iterations";
	private static final String PCT_LOCAL_EDGES_COUNTER = "Local edges (%)";
	private static final String MAXMIN_UNBALANCE_COUNTER = "Maxmin unbalance (x1000)";
	private static final String MAX_NORMALIZED_UNBALANCE_COUNTER = "Max normalized unbalance (x1000)";
	private static final String SCORE_COUNTER = "Score (x1000)";
	
	// Adnan: some statistics about the partition
	private static final String AGG_EDGE_CUTS = "# Edge cuts";
	/*
	private static final String AGG_TOTAL_COMM_VOLUME = "Comm. Volume %";
	private static final String AGG_VERTEX_BALANCE = "Vertex Balance";
	private static final String AGG_UPPER_TOTAL_COMM_VOLUME = "# CV Upper Bound";
	private static final String PARTITION_VCAPACITY_PREFIX = "AGG_VERTEX_CAPACITY_";
	*/

	public static class ComputeNewPartition extends
			// AbstractComputation<LongWritable, VertexValue, EdgeValue, PartitionMessage,
			// NullWritable> {
			AbstractComputation<LongWritable, VertexValue, EdgeValue, PartitionMessage, LongWritable> {
		private ShortArrayList maxIndices = new ShortArrayList();
		private Random rnd = new Random();
		private String[] demandAggregatorNames;
		private int[] partitionFrequency;
		/**
		 * Adnan : Edge-count in each partition (
		 */
		private long[] loads;
		/**
		 * Adnan : Vertex-count in each partition (
		 */
		//private long[] vCount;
		/**
		 * Adnan: the balanced capacity of a partition |E|/K, |V|/K
		 */
		private long totalEdgeCapacity;
		private long totalVertexCapacity;
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
		 */
		private void requestMigration(Vertex<LongWritable, VertexValue, EdgeValue> vertex, int numberOfEdges,
				short currentPartition, short newPartition) {
			vertex.getValue().setNewPartition(newPartition);
			aggregate(demandAggregatorNames[newPartition], new LongWritable(numberOfEdges));
			loads[newPartition] += numberOfEdges;
			loads[currentPartition] -= numberOfEdges;
			
			//vCount[newPartition] += 1;
			//vCount[currentPartition] -= 1;
		}

		/*
		 * Update the neighbor labels when they migrate
		 */
		private void updateNeighborsPartitions(Vertex<LongWritable, VertexValue, EdgeValue> vertex,
				Iterable<PartitionMessage> messages) {
			for (PartitionMessage message : messages) {
				LongWritable otherId = new LongWritable(message.getSourceId());
				EdgeValue oldValue = vertex.getEdgeValue(otherId);
				vertex.setEdgeValue(otherId, new EdgeValue(message.getPartition(), oldValue.getWeight(),
						oldValue.isVirtualEdge()));
			}
		}

		/*
		 * Compute the occurrences of the labels in the neighborhood
		 */
		private int computeNeighborsLabels(Vertex<LongWritable, VertexValue, EdgeValue> vertex) {
			Arrays.fill(partitionFrequency, 0);			
			int totalLabels = 0,
					localEdges = 0,
					interEdges = 0;
			//long real=0;
			short partition;
			for (Edge<LongWritable, EdgeValue> e : vertex.getEdges()) {
				partition = e.getValue().getPartition();
				partitionFrequency[partition] += e.getValue().getWeight();
				totalLabels += e.getValue().getWeight();
				if (partition == vertex.getValue().getCurrentPartition()) {
					localEdges++;
				} else {
					interEdges++;
				}
				
				/*if (!e.getValue().isVirtualEdge()) {
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
		private short chooseRandomPartitionOrCurrent(short currentPartition) {
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
		private short chooseMinLabelPartition(short currentPartition) {
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
		private short chooseRandomPartition() {
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
		 */
		private short computeNewPartition(Vertex<LongWritable, VertexValue, EdgeValue> vertex, int totalLabels) {
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
		}

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
			totalEdgeCapacity = (long) Math.round(
					((double) getTotalNumEdges() * (1 + additionalCapacity) / (numberOfPartitions + repartition)));
			
			//vCount = new long[numberOfPartitions + repartition];
			totalVertexCapacity = (long) Math.round(
					((double) getTotalNumVertices() * (1 + additionalCapacity) / (numberOfPartitions + repartition)));
			
			// cache loads for the penalty function
			for (int i = 0; i < numberOfPartitions + repartition; i++) {
				demandAggregatorNames[i] = AGGREGATOR_DEMAND_PREFIX + i;
				loads[i] = ((LongWritable) getAggregatedValue(AGGREGATOR_LOAD_PREFIX + i)).get();
				//vCount[i] = ((LongWritable) getAggregatedValue(PARTITION_VCAPACITY_PREFIX + i)).get();
			}
		}
	}

	public static class ComputeMigration extends
			// AbstractComputation<LongWritable, VertexValue, EdgeValue, NullWritable,
			// PartitionMessage> {
			AbstractComputation<LongWritable, VertexValue, EdgeValue, LongWritable, PartitionMessage> {
		private Random rnd = new Random();
		private String[] loadAggregatorNames;
		private double[] migrationProbabilities;
		private short numberOfPartitions;
		private short repartition;
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
			aggregate(loadAggregatorNames[currentPartition], 
					new LongWritable(-numberOfEdges));
			aggregate(loadAggregatorNames[newPartition], 
					new LongWritable(numberOfEdges));
			
			// Adnan : update partition's vertices count
			//aggregate(vertexCountAggregatorNames[currentPartition], new LongWritable(-1));
			//aggregate(vertexCountAggregatorNames[newPartition], new LongWritable(1));
			
			aggregate(AGGREGATOR_MIGRATIONS, new LongWritable(1));
			// inform the neighbors
			PartitionMessage message = new PartitionMessage(vertex.getId().get(),
					newPartition);
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
			long totalEdgeCapacity = (long) Math.round(
					((double) getTotalNumEdges() * (1 + additionalCapacity) / (numberOfPartitions + repartition)));
			migrationProbabilities = new double[numberOfPartitions + repartition];
			loadAggregatorNames = new String[numberOfPartitions + repartition];
			//vertexCountAggregatorNames = new String[numberOfPartitions + repartition];
			// cache migration probabilities per destination partition
			for (int i = 0; i < numberOfPartitions + repartition; i++) {
				loadAggregatorNames[i] = AGGREGATOR_LOAD_PREFIX + i;
				//vertexCountAggregatorNames[i] = PARTITION_VCAPACITY_PREFIX + i;				
				
				long load = ((LongWritable) getAggregatedValue(loadAggregatorNames[i])).get();
				//long vCount = ((LongWritable) getAggregatedValue(vertexCountAggregatorNames[i])).get();
				
				long demand = ((LongWritable) getAggregatedValue(AGGREGATOR_DEMAND_PREFIX + i)).get();
				long remainingCapacity = totalEdgeCapacity - load;
				if (demand == 0 || remainingCapacity <= 0) {
					migrationProbabilities[i] = 0;
				} else {
					migrationProbabilities[i] = ((double) (remainingCapacity)) / demand;
				}
			}
		}
	}

	public static class Initializer
			extends AbstractComputation<LongWritable, VertexValue, EdgeValue, PartitionMessage, PartitionMessage> {
		private Random rnd = new Random();
		private String[] loadAggregatorNames;
		private int numberOfPartitions;
		
		/**
		 * Store the current vertices-count of each partition 
		 */
		//private String[] vertexCountAggregatorNames;

		@Override
		public void compute(Vertex<LongWritable, VertexValue, EdgeValue> vertex, Iterable<PartitionMessage> messages)
				throws IOException {
			short partition = vertex.getValue().getCurrentPartition();
			if (partition == -1) {
				partition = (short) rnd.nextInt(numberOfPartitions);
			}
			aggregate(loadAggregatorNames[partition], 
					new LongWritable(vertex.getNumEdges()));
			//aggregate(vertexCountAggregatorNames[partition], new LongWritable(1));
			vertex.getValue().setCurrentPartition(partition);
			vertex.getValue().setNewPartition(partition);
			PartitionMessage message = new PartitionMessage(vertex.getId().get(),
					partition);
			sendMessageToAllEdges(vertex, message);
		}

		@Override
		public void preSuperstep() {
			numberOfPartitions = getContext().getConfiguration().getInt(
					NUM_PARTITIONS, DEFAULT_NUM_PARTITIONS);
			loadAggregatorNames = new String[numberOfPartitions];
			//vertexCountAggregatorNames = new String[numberOfPartitions];
			for (int i = 0; i < numberOfPartitions; i++) {
				loadAggregatorNames[i] = AGGREGATOR_LOAD_PREFIX + i;
				//vertexCountAggregatorNames[i] = PARTITION_VCAPACITY_PREFIX + i;
			}
		}
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
		}

		@Override
		public void preSuperstep() {
			numberOfPartitions = getContext().getConfiguration().getInt(NUM_PARTITIONS, DEFAULT_NUM_PARTITIONS);
			repartition = (short) getContext().getConfiguration().getInt(REPARTITION, DEFAULT_REPARTITION);
			migrationProbability = ((double) repartition) / (repartition + numberOfPartitions);
			loadAggregatorNames = new String[numberOfPartitions + repartition];
			//vertexCountAggregatorNames = new String[numberOfPartitions + repartition];
			for (int i = 0; i < numberOfPartitions + repartition; i++) {
				loadAggregatorNames[i] = AGGREGATOR_LOAD_PREFIX + i;
				//vertexCountAggregatorNames[i] = PARTITION_VCAPACITY_PREFIX + i;
			}
		}
	}

	public static class PartitionerMasterCompute extends SuperPartitionerMasterCompute {
		/*private LinkedList<Double> states;
		private String[] loadAggregatorNames;
		private int maxIterations;
		private int numberOfPartitions;
		private double convergenceThreshold;
		private short repartition;
		private int windowSize;

		private long totalMigrations;
		private double maxMinLoad;
		private double maxNormLoad;
		private double score;
		// Added by Adnan
		private double edgeBalanceEntropy;
		private double vertexBalanceEntropy=0;
		//private String[] vertexCountAggregatorNames;
		 * */
		@Override
		public void initialize() throws InstantiationException, IllegalAccessException {

			// DEFAULT_NUM_PARTITIONS = getConf().getMaxWorkers()*getConf().get();

			numberOfPartitions = getContext().getConfiguration().getInt(
					NUM_PARTITIONS, DEFAULT_NUM_PARTITIONS);
			System.out.println("k="+numberOfPartitions);
			int weight = getContext().getConfiguration().getInt(
					EDGE_WEIGHT, DEFAULT_EDGE_WEIGHT);
			System.out.println("weight="+weight);
			convergenceThreshold = getContext().getConfiguration().getFloat(
					CONVERGENCE_THRESHOLD, DEFAULT_CONVERGENCE_THRESHOLD);
			repartition = (short) getContext().getConfiguration().getInt(
					REPARTITION, DEFAULT_REPARTITION);
			windowSize = (int) getContext().getConfiguration().getInt(
					WINDOW_SIZE, DEFAULT_WINDOW_SIZE);
			states = Lists.newLinkedList();
			// Create aggregators for each partition
			loadAggregatorNames = new String[numberOfPartitions + repartition];
			//vertexCountAggregatorNames = new String[numberOfPartitions + repartition];
			for (int i = 0; i < numberOfPartitions + repartition; i++) {
				loadAggregatorNames[i] = AGGREGATOR_LOAD_PREFIX + i;
				registerPersistentAggregator(loadAggregatorNames[i], LongSumAggregator.class);
				registerAggregator(AGGREGATOR_DEMAND_PREFIX + i, LongSumAggregator.class);

				//vertexCountAggregatorNames[i] = PARTITION_VCAPACITY_PREFIX + i;
				//registerPersistentAggregator(vertexCountAggregatorNames[i], LongSumAggregator.class);
				// registerAggregator(AGGREGATOR_DEMAND_PREFIX + i, LongSumAggregator.class);
			}
			registerAggregator(AGGREGATOR_STATE, DoubleSumAggregator.class);
			registerAggregator(AGGREGATOR_LOCALS, LongSumAggregator.class);
			registerAggregator(AGGREGATOR_MIGRATIONS, LongSumAggregator.class);

			// Added by Adnan
			registerAggregator(AGG_EDGE_CUTS, LongSumAggregator.class);
			registerPersistentAggregator(AGG_UPPER_TOTAL_COMM_VOLUME, LongSumAggregator.class);
			super.init();
		}

		protected void printStats(int superstep) {
			System.out.println("superstep " + superstep);
			long migrations = ((LongWritable) getAggregatedValue(
					AGGREGATOR_MIGRATIONS)).get();
			long localEdges = ((LongWritable) getAggregatedValue(
					AGGREGATOR_LOCALS)).get();

			// Local edges
			//long realLocalEdges = ((LongWritable) getAggregatedValue(AGG_REAL_LOCAL_EDGES)).get();
			//getContext().getCounter("current", "LE - "+getSuperstep()).increment(realLocalEdges);
			
			if (superstep > 2) {
				switch (superstep % 2) {
				case 0:
					System.out.println(((double) localEdges) / getTotalNumEdges() + " local edges");
					long minLoad = Long.MAX_VALUE;
					long maxLoad = -Long.MAX_VALUE;
					for (int i = 0; i < numberOfPartitions + repartition; i++) {
						long load = ((LongWritable) getAggregatedValue(loadAggregatorNames[i])).get();
						if (load < minLoad) {
							minLoad = load;
						}
						if (load > maxLoad) {
							maxLoad = load;
						}
					}
					double expectedLoad = ((double) getTotalNumEdges()) / (numberOfPartitions + repartition);
					System.out.println((((double) maxLoad) / minLoad) + " max-min unbalance");
					System.out.println((((double) maxLoad) / expectedLoad) + " maximum normalized load");
					break;
				case 1:
					System.out.println(migrations + " migrations");
					break;
				}
			}
		}

		protected void updateStats() {
			//if(getSuperstep()==0) return;
			totalMigrations += ((LongWritable) getAggregatedValue(AGGREGATOR_MIGRATIONS)).get();
		
			long minLoad = Long.MAX_VALUE;
			long maxLoad = -Long.MAX_VALUE;
			int k = numberOfPartitions + repartition;
			double expectedLoad = ((double) getTotalNumEdges()) / (k);
			
			//System.out.println(expectedLoad);
			
			double sumEdges = 0, sumVertex = 0;
			for (int i = 0; i < k; i++) {
				long load = ((LongWritable) getAggregatedValue(
						loadAggregatorNames[i])).get();
				//long compute = ((LongWritable) getAggregatedValue(vertexCountAggregatorNames[i])).get();
				if (load < minLoad) {
					minLoad = load;
				}
				if (load > maxLoad) {
					maxLoad = load;
				}
				
				double loadProb = ((double) load)/ getTotalNumEdges();
				sumEdges += -loadProb * Math.log(loadProb);
		
				//double computeProb = ((double) compute)/ getTotalNumVertices();
				//sumVertex += -computeProb * Math.log(computeProb);
			}
		
			maxMinLoad = ((double) maxLoad) / minLoad;
			maxNormLoad = ((double) maxLoad) / expectedLoad;
			score = ((DoubleWritable) getAggregatedValue(AGGREGATOR_STATE)).get();
		
			//edgeBalanceEntropy = sumEdges / Math.log(k);
			//vertexBalanceEntropy = sumVertex / Math.log(k);
		}

		protected boolean algorithmConverged(int superstep) {
			double newState = ((DoubleWritable) getAggregatedValue(AGGREGATOR_STATE)).get();
			boolean converged = false;
			if (superstep > 3 + windowSize) {
				double best = Collections.max(states);
				double step = Math.abs(1 - newState / best);
				converged = step < convergenceThreshold;
				System.out.println("BestState=" + best + " NewState=" + newState + " " + step);
				states.removeFirst();
			} else {
				System.out.println("BestState=" + newState + " NewState=" + newState + " " + 1.0);
			}
			states.addLast(newState);

			return converged;
		}

		private void setCounters() {
			long localEdges = ((LongWritable) getAggregatedValue(AGGREGATOR_LOCALS)).get();
			getContext().getCounter(PARTITION_COUNTER_GROUP, "#local edges")
					.increment(localEdges);
			long localEdgesPct = (long) (100 * ((double) localEdges) / getTotalNumEdges());
			getContext().getCounter(PARTITION_COUNTER_GROUP, MIGRATIONS_COUNTER)
					.increment(totalMigrations);
			getContext().getCounter(PARTITION_COUNTER_GROUP, ITERATIONS_COUNTER)
					.increment(getSuperstep());
			getContext().getCounter(PARTITION_COUNTER_GROUP, PCT_LOCAL_EDGES_COUNTER)
					.increment(localEdgesPct);
			getContext().getCounter(PARTITION_COUNTER_GROUP, MAXMIN_UNBALANCE_COUNTER)
					.increment((long) (1000 * maxMinLoad));
			getContext().getCounter(PARTITION_COUNTER_GROUP, MAX_NORMALIZED_UNBALANCE_COUNTER)
					.increment((long) (1000 * maxNormLoad));
			getContext().getCounter(PARTITION_COUNTER_GROUP, SCORE_COUNTER)
					.increment((long) (1000 * score));
			
			/*
			 * getContext().getCounter(COUNTER_GROUP, "|E(P0)|").setValue( ((LongWritable)
			 * getAggregatedValue(AGGREGATOR_LOAD_PREFIX + "0")).get() );
			 * getContext().getCounter(COUNTER_GROUP, "|E(P1)|").setValue( ((LongWritable)
			 * getAggregatedValue(AGGREGATOR_LOAD_PREFIX + "1")).get() );
			 */
			
			long edgeCut = ((LongWritable) getAggregatedValue(AGG_EDGE_CUTS)).get();
			long edgeCutPct = (long) (100 * ((double) edgeCut) / getTotalNumEdges());
			getContext().getCounter(PARTITION_COUNTER_GROUP, AGG_EDGE_CUTS).increment(edgeCut);
			getContext().getCounter(PARTITION_COUNTER_GROUP, "Edge Cuts (%)").increment(edgeCutPct);
			
			/*getContext().getCounter(PARTITION_COUNTER_GROUP, "Vertex balance entropy")
					.increment((long) (1000 * vertexBalanceEntropy));
			getContext().getCounter(PARTITION_COUNTER_GROUP, "Edge balance entropy")
					.increment((long) (1000 * edgeBalanceEntropy));*/
			
			
			
			long firstLPIteration = getContext().getCounter("Giraph Timers", "Superstep 3 ComputeNewPartition (ms)").getValue()
					+ getContext().getCounter("Giraph Timers", "Superstep 4 ComputeMigration (ms)").getValue();
			long totalIterationsTime = firstLPIteration;
			float avg=0;
			// compute the average superstep time 
			for (int i = 5; i < getSuperstep(); i=i+2) {
				totalIterationsTime += getContext().getCounter("Giraph Timers", "Superstep " + i + " ComputeNewPartition (ms)").getValue()
					+ getContext().getCounter("Giraph Timers", "Superstep " + (i+1) + " ComputeMigration (ms)").getValue();
			}
			avg = (float) totalIterationsTime/((getSuperstep()-3)/2);
			
			getContext().getCounter(PARTITION_COUNTER_GROUP, "Total LP (ms)")
			.increment((long) (totalIterationsTime));
		}
		

		private static boolean computeStates=false;
		private static int lastStep=Integer.MAX_VALUE;
		@Override
		public void compute() {
			int superstep = (int) getSuperstep();
			
			if(computeStates) {
				if(superstep==lastStep+1)
					setComputation(ComputeGraphPartitionStatistics.class);
				else {
					System.out.println("Finish stats.");
					haltComputation();
					updatePartitioningQuality();
					saveTimersStats(true, totalMigrations);
					
				}
			} else {
				if (superstep == 0) {
					setComputation(ConverterPropagate.class);
				} else if (superstep == 1) {
					setComputation(ConverterUpdateEdges.class);
				} else if (superstep == 2) {
					if (repartition != 0) {
						setComputation(Repartitioner.class);
					} else {
						setComputation(Initializer.class);
					}
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
				if (superstep > 3) {
					if (superstep % 2 == 0) {
						hasConverged = algorithmConverged(superstep);
					}
				}
				printStats(superstep);
				updateStats();
				// LP iteration = 2 super-steps, LP process start after 3 super-steps 
				System.out.println("maxIterations: "+maxIterations+"\n\n");
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
}
