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

import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.Random;
import org.apache.giraph.aggregators.LongSumAggregator;
import org.apache.giraph.edge.Edge;
import org.apache.giraph.edge.EdgeFactory;
import org.apache.giraph.graph.AbstractComputation;
import org.apache.giraph.graph.Vertex;
import org.apache.giraph.master.DefaultMasterCompute;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.mapred.Task;

import giraph.ml.grafos.okapi.spinner.EdgeValue;
import giraph.ml.grafos.okapi.spinner.PartitionMessage;
import giraph.ml.grafos.okapi.spinner.VertexValue;

/**
 *
 * 
 */
@SuppressWarnings("unused")
public class LPGPartitionner {

	protected static final String AGG_EGDES_LOAD_PREFIX = "AGG_LOAD_";
	protected static final String AGG_MIGRATION_DEMAND_PREFIX = "AGG_EDGE_MIGRATION_DEMAND_";
	protected static final String AGG_VERTEX_MIGRATION_DEMAND_PREFIX = "AGG_VERTEX_MIGRATION_DEMAND_";
	protected static final String AGGREGATOR_STATE = "AGG_STATE";
	protected static final String AGGREGATOR_MIGRATIONS = "AGG_MIGRATIONS";
	protected static final String AGGREGATOR_LOCALS = "AGG_LOCALS";
	public static final String NUM_PARTITIONS = "spinner.numberOfPartitions";
	public static final String SAVE_STATS = "partition.SaveStatsIntoFile";
	protected static final int DEFAULT_NUM_PARTITIONS = 8;// 32;
	protected static final String ADDITIONAL_CAPACITY = "spinner.additionalCapacity";
	protected static final float DEFAULT_ADDITIONAL_CAPACITY = 0.05f;
	protected static final String LAMBDA = "spinner.lambda";
	protected static final float DEFAULT_LAMBDA = 1.0f;
	/**
	 * The maximum number of label propagation iteration <br>
	 * LP iteration = 2 Giraph super-steps
	 */
	public static final String MAX_ITERATIONS_LP = "spinner.MaxIterationsLP";
	protected static final int DEFAULT_MAX_ITERATIONS = 290;// 10;//290;
	protected static final String CONVERGENCE_THRESHOLD = "spinner.threshold";
	protected static final float DEFAULT_CONVERGENCE_THRESHOLD = 0.001f;
	public static final String EDGE_WEIGHT = "spinner.weight";
	public static final byte DEFAULT_EDGE_WEIGHT = 1;
	protected static final String REPARTITION = "spinner.repartition";
	protected static final short DEFAULT_REPARTITION = 0;
	protected static final String WINDOW_SIZE = "spinner.windowSize";
	protected static final int DEFAULT_WINDOW_SIZE = 5;

	protected static final String COUNTER_GROUP = "Partitioning Counters";
	protected static final String MIGRATIONS_COUNTER = "Migrations";
	protected static final String ITERATIONS_COUNTER = "Iterations";
	protected static final String PCT_LOCAL_EDGES_COUNTER = "Local edges (%)";
	protected static final String MAXMIN_UNBALANCE_COUNTER = "Maxmin unbalance (x1000)";
	protected static final String MAX_NORMALIZED_UNBALANCE_COUNTER = "Max normalized unbalance (x1000)";
	protected static final String SCORE_COUNTER = "Score (x1000)";

	protected static final String PARTITION_COUNTER_GROUP = "Partitioning Counters";
	// Adnan
	protected static long totalVertexNumber;
	// Adnan : ration Communication Computation
	protected static int outDegreeThreshold;

	// Adnan: some statistics about the initialization
	protected static final String AGG_INITIALIZED_VERTICES = "Initialized vertex %";
	protected static final String AGG_UPDATED_VERTICES = "Updated vertex %";
	// Adnan : the outgoing edges of initialized vertices
	protected static final String AGG_FIRST_LOADED_EDGES = "First Loaded Edges %";

	// Adnan: some statistics about the partition
	// global stat
	protected static final String AGG_EDGE_CUTS = "# Edge cuts";
	protected static final String AGG_UPPER_TOTAL_COMM_VOLUME = "# CV Upper Bound";
	protected static final String TOTAL_DIRECTED_OUT_EDGES = "#Total Directed Out Edges";
	// local stat (per partition)
	protected static final String FINAL_AGG_VERTEX_COUNT_PREFIX = "FINAL_AGG_VERTEX_CAPACITY_";
	protected static final String AGG_VERTEX_COUNT_PREFIX = "AGG_VERTEX_CAPACITY_";
	protected static final String AGG_AVG_DEGREE_PREFIX = "AGG_AVG_DEGREE_CAPACITY_";

	public static final String DEBUG = "graph.debug";

	public static final String GRAPH_DIRECTED = "graph.directed";
	public static final boolean DEFAULT_GRAPH_DIRECTED = false;
	public static final String KAPPA = "partition.edgeBalanceWeight.kappa";
	protected static final float DEFAULT_KAPPA = 0.5f;

	public static final String COMPUTE_OUTDEGREE_THRESHOLD = "partition.OUTDEGREE_THRESHOLD";
	protected static final boolean DEFAULT_COMPUTE_OUTDEGREE_THRESHOLD = true;
	public static final String OUTDEGREE_THRESHOLD = "partition.OUTDEGREE_THRESHOLD";
	protected static final int DEFAULT_OUTDEGREE_Threshold = 1;
	public static final String MIN_OUTDEGREE_THRESHOLD = "partition.MIN_OUTDEGREE_THRESHOLD";
	protected static final int DEFAULT_MIN_OUTDEGREE_Threshold = 25;

	protected static final String AGG_REAL_LOAD_PREFIX = "AGG_REAL_LOAD_";
	protected static final String AGG_VIRTUAL_LOAD_PREFIX = "AGG_VIRTUAL_LOAD_";

	protected static final String AGG_REAL_LOCAL_EDGES = "AGG_REAL_LOCALS";
	protected static final String AGG_VIRTUAL_LOCALS = "AGG_VIRTUAL_LOCALS";

	// Adnan: some statistics about the partition
	protected static final String AGG_REAL_EDGE_CUTS = "#REAL Edge cuts";
	protected static final String AGG_VIRTUAL_EDGE_CUTS = "#VIRTUAL Edge cuts";

	protected static final String AGG_REAL_TOTAL_COMM_VOLUME = "REAL Communication Volume";
	protected static final String AGG_VIRTUAL_TOTAL_COMM_VOLUME = "VIRTUAL Communication Volume";

	protected static final String AGG_REAL_UPPER_TOTAL_COMM_VOLUME = "#REAL CV Upper Bound";
	private static final String AGG_VIRTUAL_UPPER_TOTAL_COMM_VOLUME = "#VIRTUAL CV Upper Bound";

	public static final String Vertex_Balance_JSD_COUNTER = "Vertex Balance JSD";
	public static final String Edge_Balance_JSD_COUNTER = "Edge Balance JSD";

	public static final String OUTDEGREE_FREQUENCY_COUNTER = "OUTDEGREE_FREQUENCY_COUNTER_";

	public static class ConverterPropagate
			extends AbstractComputation<LongWritable, VertexValue, EdgeValue, LongWritable, LongWritable> {

		@Override
		public void compute(Vertex<LongWritable, VertexValue, EdgeValue> vertex, Iterable<LongWritable> messages)
				throws IOException {
			aggregate(TOTAL_DIRECTED_OUT_EDGES, new LongWritable(vertex.getNumEdges()));
			vertex.getValue().setRealOutDegree(vertex.getNumEdges());
			sendMessageToAllEdges(vertex, vertex.getId());
		}
	}

	public static class ConverterUpdateEdges
			extends AbstractComputation<LongWritable, VertexValue, EdgeValue, LongWritable, PartitionMessage> {
		private byte edgeWeight;
		private String[] outDegreeFrequency;
		private int avgDegree;

		@Override
		public void compute(Vertex<LongWritable, VertexValue, EdgeValue> vertex, Iterable<LongWritable> messages)
				throws IOException {

			if (vertex.getNumEdges() >= 100) {
				aggregate(outDegreeFrequency[100], new LongWritable(1));
			} else {
				aggregate(outDegreeFrequency[vertex.getNumEdges()], new LongWritable(1));
			}

			int inter = 0;
			for (LongWritable other : messages) {
				EdgeValue edgeValue = vertex.getEdgeValue(other);
				if (edgeValue == null || edgeValue.isVirtualEdge()) {
					// if (edgeValue == null) {
					edgeValue = new EdgeValue();
					edgeValue.setWeight((byte) 1);
					edgeValue.setVirtualEdge(true);
					Edge<LongWritable, EdgeValue> edge = EdgeFactory.create(new LongWritable(other.get()), edgeValue);
					vertex.addEdge(edge);
					// System.out.println(1);

					// aggregate(AGG_REAL_LOCAL_EDGES, new LongWritable(1));

				} else {
					edgeValue = new EdgeValue();
					// edgeValue.setWeight(2);
					// System.out.println(edgeWeight);
					edgeValue.setWeight(edgeWeight);
					edgeValue.setVirtualEdge(false);
					vertex.setEdgeValue(other, edgeValue);
				}
				inter++;
			}
			vertex.getValue().setRealInDegree(inter);
		}

		@Override
		public void preSuperstep() {
			edgeWeight = (byte) getContext().getConfiguration().getInt(EDGE_WEIGHT, DEFAULT_EDGE_WEIGHT);
			outDegreeFrequency = new String[101];
			for (int i = 0; i < 101; i++) {
				outDegreeFrequency[i] = OUTDEGREE_FREQUENCY_COUNTER + i;
			}
		}
	}

	public static class PotentialVerticesInitializer
			extends AbstractComputation<LongWritable, VertexValue, EdgeValue, PartitionMessage, PartitionMessage> {
		protected String[] loadAggregatorNames;
		protected int numberOfPartitions;
		protected Random rnd = new Random();

		/**
		 * Store the current vertices-count of each partition
		 */
		protected String[] vertexCountAggregatorNames;
		protected long totalNumEdges;
		protected boolean directedGraph;
		private boolean debug;
		private int minOutDegreeThreshold;
		private int degreeThreshold;

		@Override
		public void compute(Vertex<LongWritable, VertexValue, EdgeValue> vertex, Iterable<PartitionMessage> messages)
				throws IOException {
			short partition = vertex.getValue().getCurrentPartition();
			// long numEdges = vertex.getNumEdges();
			long numOutEdges = vertex.getNumEdges();
			if (directedGraph) {
				numOutEdges = vertex.getValue().getRealOutDegree();
			}

			if (partition == -1 && vertex.getValue().getRealOutDegree() > outDegreeThreshold) {
				// initialize only hub vertices
				partition = (short) rnd.nextInt(numberOfPartitions);

				// }
				// necessary to the label
				// if (partition != -1) {

				aggregate(loadAggregatorNames[partition], new LongWritable(numOutEdges));
				aggregate(vertexCountAggregatorNames[partition], new LongWritable(1));

				vertex.getValue().setCurrentPartition(partition);
				vertex.getValue().setNewPartition(partition);
				PartitionMessage message = new PartitionMessage(vertex.getId().get(), partition);
				sendMessageToAllEdges(vertex, message);

				aggregate(AGG_INITIALIZED_VERTICES, new LongWritable(1));
				aggregate(AGG_FIRST_LOADED_EDGES, new LongWritable(numOutEdges));
			}

			aggregate(AGG_UPPER_TOTAL_COMM_VOLUME, new LongWritable(Math.min(numberOfPartitions, numOutEdges)));
		}

		@Override
		public void preSuperstep() {
			directedGraph = getContext().getConfiguration().getBoolean(GRAPH_DIRECTED, DEFAULT_GRAPH_DIRECTED);

			totalNumEdges = ((LongWritable) getAggregatedValue(TOTAL_DIRECTED_OUT_EDGES)).get();
			totalVertexNumber = getTotalNumVertices();
			

			degreeThreshold = getContext().getConfiguration().getInt(OUTDEGREE_THRESHOLD, DEFAULT_OUTDEGREE_Threshold);
			minOutDegreeThreshold = getContext().getConfiguration().getInt(MIN_OUTDEGREE_THRESHOLD, DEFAULT_MIN_OUTDEGREE_Threshold);

			if (getContext().getConfiguration().getBoolean(COMPUTE_OUTDEGREE_THRESHOLD,
					DEFAULT_COMPUTE_OUTDEGREE_THRESHOLD)) {
				outDegreeThreshold = (int) Math.ceil((double) totalNumEdges / (double) totalVertexNumber);
			} else {
				outDegreeThreshold = degreeThreshold;
			}

			numberOfPartitions = getContext().getConfiguration().getInt(NUM_PARTITIONS, DEFAULT_NUM_PARTITIONS);
			loadAggregatorNames = new String[numberOfPartitions];
			vertexCountAggregatorNames = new String[numberOfPartitions];
			for (int i = 0; i < numberOfPartitions; i++) {
				loadAggregatorNames[i] = AGG_EGDES_LOAD_PREFIX + i;
				vertexCountAggregatorNames[i] = AGG_VERTEX_COUNT_PREFIX + i;
			}
			debug = getContext().getConfiguration().getBoolean(DEBUG, false);
			System.out.println("PreSuperstep Initializer : outDegreeThreshold=" + outDegreeThreshold);
			//aggregate(OUTDEGREE_THRESHOLD, new LongWritable(outDegreeThreshold));
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
		private String[] vertexCountAggregatorNames;
		private boolean directedGraph;
		private long totalNumEdges;

		@Override
		public void compute(Vertex<LongWritable, VertexValue, EdgeValue> vertex, Iterable<PartitionMessage> messages)
				throws IOException {
			short partition;
			short currentPartition = vertex.getValue().getCurrentPartition();

			long numEdges;
			if (directedGraph)
				numEdges = vertex.getValue().getRealOutDegree();
			else
				numEdges = vertex.getNumEdges();
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
			aggregate(loadAggregatorNames[partition], new LongWritable(numEdges));
			aggregate(vertexCountAggregatorNames[partition], new LongWritable(1));

			vertex.getValue().setCurrentPartition(partition);
			vertex.getValue().setNewPartition(partition);

			aggregate(AGG_UPPER_TOTAL_COMM_VOLUME,
					new LongWritable(Math.min(numberOfPartitions + repartition, numEdges)));

			PartitionMessage message = new PartitionMessage(vertex.getId().get(), partition);
			sendMessageToAllEdges(vertex, message);
		}

		@Override
		public void preSuperstep() {
			directedGraph = getContext().getConfiguration().getBoolean(GRAPH_DIRECTED, DEFAULT_GRAPH_DIRECTED);

			if (directedGraph)
				totalNumEdges = ((LongWritable) getAggregatedValue(TOTAL_DIRECTED_OUT_EDGES)).get();
			else
				totalNumEdges = getTotalNumEdges();

			numberOfPartitions = getContext().getConfiguration().getInt(NUM_PARTITIONS, DEFAULT_NUM_PARTITIONS);
			repartition = (short) getContext().getConfiguration().getInt(REPARTITION, DEFAULT_REPARTITION);
			migrationProbability = ((double) repartition) / (repartition + numberOfPartitions);
			loadAggregatorNames = new String[numberOfPartitions + repartition];
			vertexCountAggregatorNames = new String[numberOfPartitions + repartition];
			for (int i = 0; i < numberOfPartitions + repartition; i++) {
				loadAggregatorNames[i] = AGG_EGDES_LOAD_PREFIX + i;
				vertexCountAggregatorNames[i] = AGG_VERTEX_COUNT_PREFIX + i;
			}
		}
	}

	public static class ResetPartition
			// AbstractComputation<LongWritable, VertexValue, EdgeValue, LongWritable,
			// PartitionMessage>
			extends AbstractComputation<LongWritable, VertexValue, EdgeValue, LongWritable, PartitionMessage> {
		@Override
		public void compute(Vertex<LongWritable, VertexValue, EdgeValue> vertex, Iterable<LongWritable> messages)
				throws IOException {
			short partition = -1;

			vertex.getValue().setCurrentPartition(partition);
			vertex.getValue().setNewPartition(partition);

			PartitionMessage message = new PartitionMessage(vertex.getId().get(), partition);
			sendMessageToAllEdges(vertex, message);
		}
	}

	public static class ComputeNewPartition
			extends AbstractComputation<LongWritable, VertexValue, EdgeValue, PartitionMessage, LongWritable> {

		@Override
		public void compute(Vertex<LongWritable, VertexValue, EdgeValue> vertex, Iterable<PartitionMessage> messages)
				throws IOException {
			// TODO Auto-generated method stub

		}

	}

	public static class ComputeMigration
			extends AbstractComputation<LongWritable, VertexValue, EdgeValue, LongWritable, PartitionMessage> {

		@Override
		public void compute(Vertex<LongWritable, VertexValue, EdgeValue> vertex, Iterable<LongWritable> messages)
				throws IOException {
			// TODO Auto-generated method stub

		}
	}

	public static class ComputeGraphPartitionStatistics
			extends AbstractComputation<LongWritable, VertexValue, EdgeValue, PartitionMessage, LongWritable> {
		private byte edgeWeight;
		private boolean graphDirected;
		private byte newReverseEdgesWeight;

		private ShortArrayList maxIndices = new ShortArrayList();
		private Random rnd = new Random();
		//
		private String[] demandAggregatorNames;
		private int[] partitionFrequency;
		/**
		 * connected partitions
		 */
		private ShortArrayList pConnect;

		/**
		 * Adnan: the balanced capacity of a partition |E|/K, |V|/K
		 */
		private long totalEdgeCapacity;
		private long totalVertexCapacity;
		private short totalNbPartitions;
		private double additionalCapacity;
		private long totalNumEdges;
		private boolean directedGraph;
		private String[] realLoadAggregatorNames;
		private String[] finalVertexCounts;
		private String[] virtualLoadAggregatorNames;
		private boolean debug;

		@Override
		public void compute(Vertex<LongWritable, VertexValue, EdgeValue> vertex, Iterable<PartitionMessage> messages)
				throws IOException {
			short p = vertex.getValue().getCurrentPartition();
			long realLocalEdges = 0, realInterEdges = 0, virtualLocalEdges = 0, virtualInterEdges = 0, realCV = 0;
			short p2;

			pConnect = new ShortArrayList();

			if (debug) {
				System.out.println(vertex.getId() + "\t" + vertex.getValue().getRealOutDegree() + "\t"
						+ vertex.getValue().getRealInDegree());
			}

			// Adnan : update partition's vertices count
			aggregate(finalVertexCounts[p], new LongWritable(1));
			aggregate(realLoadAggregatorNames[p], new LongWritable(vertex.getValue().getRealOutDegree()));
			aggregate(virtualLoadAggregatorNames[p], new LongWritable(vertex.getNumEdges()));

			for (Edge<LongWritable, EdgeValue> e : vertex.getEdges()) {
				p2 = e.getValue().getPartition();

				if (debug) {
					System.out.println(vertex.getId() + "-->" + e.getTargetVertexId() + "\t"
							+ e.getValue().isVirtualEdge() + "\t" + e.getValue().getWeight());
				}

				if (p2 == p) {
					if (!e.getValue().isVirtualEdge()) {
						realLocalEdges++;
					}
					virtualLocalEdges++;
				} else {
					if (!pConnect.contains(p2)) {
						pConnect.add(p2);
						if (!e.getValue().isVirtualEdge())
							realCV++;
					}
				}

			}
			realInterEdges = vertex.getValue().getRealOutDegree() - realLocalEdges;
			virtualInterEdges = vertex.getNumEdges() - virtualLocalEdges;
			// update cut edges stats
			aggregate(AGG_REAL_LOCAL_EDGES, new LongWritable(realLocalEdges));
			// ADNAN : update Total Comm Vol. State
			aggregate(AGG_REAL_EDGE_CUTS, new LongWritable(realInterEdges));
			// ADNAN : update Total Comm Vol. State
			aggregate(AGG_REAL_TOTAL_COMM_VOLUME, new LongWritable(realCV));

			aggregate(AGG_REAL_UPPER_TOTAL_COMM_VOLUME,
					new LongWritable(Math.min(totalNbPartitions, vertex.getValue().getRealOutDegree())));

			// update cut edges stats
			aggregate(AGG_VIRTUAL_LOCALS, new LongWritable(virtualLocalEdges));
			// ADNAN : update Total Comm Vol. State
			aggregate(AGG_VIRTUAL_EDGE_CUTS, new LongWritable(virtualInterEdges));
			// ADNAN : update Total Comm Vol. State
			aggregate(AGG_VIRTUAL_TOTAL_COMM_VOLUME, new LongWritable(pConnect.size()));

			aggregate(AGG_VIRTUAL_UPPER_TOTAL_COMM_VOLUME,
					new LongWritable(Math.min(totalNbPartitions, vertex.getNumEdges())));
		}

		@Override
		public void preSuperstep() {
			directedGraph = getContext().getConfiguration().getBoolean(GRAPH_DIRECTED, DEFAULT_GRAPH_DIRECTED);
			debug = getContext().getConfiguration().getBoolean(DEBUG, false);

			additionalCapacity = getContext().getConfiguration().getFloat(ADDITIONAL_CAPACITY,
					DEFAULT_ADDITIONAL_CAPACITY);
			totalNbPartitions = (short) (getContext().getConfiguration().getInt(NUM_PARTITIONS, DEFAULT_NUM_PARTITIONS)
					+ getContext().getConfiguration().getInt(REPARTITION, DEFAULT_REPARTITION));

			realLoadAggregatorNames = new String[totalNbPartitions];
			virtualLoadAggregatorNames = new String[totalNbPartitions];
			finalVertexCounts = new String[totalNbPartitions];
			for (int i = 0; i < totalNbPartitions; i++) {
				realLoadAggregatorNames[i] = AGG_REAL_LOAD_PREFIX + i;
				virtualLoadAggregatorNames[i] = AGG_VIRTUAL_LOAD_PREFIX + i;
				finalVertexCounts[i] = FINAL_AGG_VERTEX_COUNT_PREFIX + i;

			}
		}
	}

	/**
	 * The core of the partitioning algorithm executed on the Master
	 * @author Adnan
	 *
	 */
	public static class SuperPartitionerMasterCompute extends DefaultMasterCompute {
		protected LinkedList<Double> states;
		protected String[] loadAggregatorNames;
		protected int maxIterations;
		protected int numberOfPartitions;
		protected double convergenceThreshold;
		protected short repartition;
		protected int windowSize;
		protected double maxMinLoad;
		protected double maxNormLoad;
		protected double score;
		// Added by Adnan
		protected long totalMigrations;

		protected String[] realLoadAggregatorNames;
		protected String[] virtualLoadAggregatorNames;
		protected String[] vertexCountAggregatorNames;
		protected String[] finalVertexCounts;
		protected String[] outDegreeFrequency;

		protected long[] realLoads;
		protected long[] virtualLoads;
		protected long[] vCounts;
		protected double[] realAvgDegrees;
		protected double[] virtualAvgDegrees;

		protected double realMaxMinLoad;
		protected double realMaxNormLoad;
		protected double realEdgeBalanceJSD;

		protected double virtualMaxMinLoad;
		protected double virtualMaxNormLoad;
		protected double virtualEdgeBalanceJSD;

		protected double realMaxMinAvgDegree;
		protected double realMaxNormAvgDegree;
		protected double realAvgDegreeBalanceJSD;

		protected double virtualMaxMinAvgDegree;
		protected double virtualMaxNormAvgDegree;
		protected double virtualAvgDegreeBalanceJSD;

		protected double vertexMaxMinLoad;
		protected double vertexMaxNormLoad;
		protected double vertexBalanceJSD;
		
		/**
		 * if True, the statistics of the partition will be stored
		 * in a local file.
		 * (Please set the file name in saveXXX() methods)
		 */
		private boolean isSaveStatsIntoFile;

		@Override
		public void initialize() throws InstantiationException, IllegalAccessException {

		}
		
		/**
		 * Initialize the algorithm parameters (number of partitions, etc.)
		 * 
		 * Initialize the Aggregators (global/shared variables) needed to store 
		 * the state of the partition (edge cuts, number of edges/vertices, etc.).
		 * 
		 * @throws InstantiationException
		 * @throws IllegalAccessException
		 */
		protected void init() throws InstantiationException, IllegalAccessException {
			// DEFAULT_NUM_PARTITIONS = getConf().getMaxWorkers()*getConf().get();
			maxIterations = getContext().getConfiguration().getInt(MAX_ITERATIONS_LP, DEFAULT_MAX_ITERATIONS);

			numberOfPartitions = getContext().getConfiguration().getInt(NUM_PARTITIONS, DEFAULT_NUM_PARTITIONS);

			repartition = (short) getContext().getConfiguration().getInt(REPARTITION, DEFAULT_REPARTITION);

			isSaveStatsIntoFile = (boolean) getContext().getConfiguration().getBoolean(SAVE_STATS, false);

			// Create aggregators for each partition
			realLoadAggregatorNames = new String[numberOfPartitions + repartition];
			virtualLoadAggregatorNames = new String[numberOfPartitions + repartition];
			vertexCountAggregatorNames = new String[numberOfPartitions + repartition];
			finalVertexCounts = new String[numberOfPartitions + repartition];

			for (int i = 0; i < numberOfPartitions + repartition; i++) {
				realLoadAggregatorNames[i] = AGG_REAL_LOAD_PREFIX + i;
				registerPersistentAggregator(realLoadAggregatorNames[i], LongSumAggregator.class);

				virtualLoadAggregatorNames[i] = AGG_VIRTUAL_LOAD_PREFIX + i;
				registerPersistentAggregator(virtualLoadAggregatorNames[i], LongSumAggregator.class);

				vertexCountAggregatorNames[i] = AGG_VERTEX_COUNT_PREFIX + i;
				registerPersistentAggregator(vertexCountAggregatorNames[i], LongSumAggregator.class);
				registerPersistentAggregator(virtualLoadAggregatorNames[i], LongSumAggregator.class);

				finalVertexCounts[i] = FINAL_AGG_VERTEX_COUNT_PREFIX + i;
				registerPersistentAggregator(finalVertexCounts[i], LongSumAggregator.class);
			}

			// Added by Adnan
			registerPersistentAggregator(AGG_REAL_UPPER_TOTAL_COMM_VOLUME, LongSumAggregator.class);
			registerPersistentAggregator(AGG_VIRTUAL_UPPER_TOTAL_COMM_VOLUME, LongSumAggregator.class);

			registerPersistentAggregator(TOTAL_DIRECTED_OUT_EDGES, LongSumAggregator.class);

			registerAggregator(AGG_REAL_TOTAL_COMM_VOLUME, LongSumAggregator.class);
			registerAggregator(AGG_VIRTUAL_TOTAL_COMM_VOLUME, LongSumAggregator.class);

			registerAggregator(AGG_REAL_EDGE_CUTS, LongSumAggregator.class);
			registerAggregator(AGG_VIRTUAL_EDGE_CUTS, LongSumAggregator.class);

			registerAggregator(AGG_REAL_LOCAL_EDGES, LongSumAggregator.class);
			registerAggregator(AGG_VIRTUAL_LOCALS, LongSumAggregator.class);

			outDegreeFrequency = new String[101];
			for (int i = 0; i < 101; i++) {
				outDegreeFrequency[i] = OUTDEGREE_FREQUENCY_COUNTER + i;
				registerPersistentAggregator(outDegreeFrequency[i], LongSumAggregator.class);

			}
		}

		/**
		 * Print partition Stat. at a given superstep
		 * @param superstep superstep number
		 */
		protected void printStats(int superstep) {
			System.out.println("superstep " + superstep);
			if (superstep > 2) {
				long migrations = ((LongWritable) getAggregatedValue(AGGREGATOR_MIGRATIONS)).get();
				long localEdges = ((LongWritable) getAggregatedValue(AGGREGATOR_LOCALS)).get();

				// Local edges
				// long realLocalEdges = ((LongWritable)
				// getAggregatedValue(AGG_REAL_LOCAL_EDGES)).get();
				// getContext().getCounter("current", "LE -
				// "+getSuperstep()).increment(realLocalEdges);

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

		/**
		 * Update the partition Stat.
		 * @param superstep
		 */
		protected void updateStats() {
			totalMigrations += ((LongWritable) getAggregatedValue(AGGREGATOR_MIGRATIONS)).get();
			long minLoad = Long.MAX_VALUE;
			long maxLoad = -Long.MAX_VALUE;
			int k = numberOfPartitions + repartition;
			double expectedLoad = ((double) getTotalNumEdges()) / (k);

			double sumEdges = 0, sumVertex = 0;
			for (int i = 0; i < k; i++) {
				long load = ((LongWritable) getAggregatedValue(loadAggregatorNames[i])).get();
				// long compute = ((LongWritable)
				// getAggregatedValue(vertexCountAggregatorNames[i])).get();
				if (load < minLoad) {
					minLoad = load;
				}
				if (load > maxLoad) {
					maxLoad = load;
				}

				double loadProb = ((double) load) / getTotalNumEdges();
				sumEdges += -loadProb * Math.log(loadProb);

				// double computeProb = ((double) compute)/ getTotalNumVertices();
				// sumVertex += -computeProb * Math.log(computeProb);
			}

			maxMinLoad = ((double) maxLoad) / minLoad;
			maxNormLoad = ((double) maxLoad) / expectedLoad;
			score = ((DoubleWritable) getAggregatedValue(AGGREGATOR_STATE)).get();
		}

		/**
		 * check if the algorithm has converged
		 * @param superstep the number of the current superstep
		 * @return
		 */
		protected boolean algorithmConverged(int superstep) {
			double newState = ((DoubleWritable) getAggregatedValue(AGGREGATOR_STATE)).get();
			boolean converged = false;
			if (superstep > 5 + windowSize) {
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

		protected static boolean computeStates = false;
		protected static int lastStep = Integer.MAX_VALUE;

		@Override
		public void compute() {
		}

		/**
		 * Update the partition quality measures (evaluations measures)
		 */
		protected void updatePartitioningQuality() {
			int k = numberOfPartitions + repartition;

			long totalNumRealEdges = ((LongWritable) getAggregatedValue(TOTAL_DIRECTED_OUT_EDGES)).get();

			long realMinLoad = Long.MAX_VALUE;
			long realMaxLoad = -Long.MAX_VALUE;
			double realExpectedLoad = ((double) totalNumRealEdges) / (k);

			long virtualMinLoad = Long.MAX_VALUE;
			long virtualMaxLoad = -Long.MAX_VALUE;
			double virtualExpectedLoad = ((double) getTotalNumEdges()) / (k);

			double realMinAvgDegree = Double.MAX_VALUE;
			double realMaxAvgDegree = -Double.MAX_VALUE;
			double realExpectedAvgDegree = ((double) totalNumRealEdges) / getTotalNumVertices();

			double virtualMinAvgDegree = Double.MAX_VALUE;
			double virtualMaxAvgDegree = -Double.MAX_VALUE;
			double virtualExpectedAvgDegree = ((double) getTotalNumEdges()) / getTotalNumVertices();

			long vertexMinLoad = Long.MAX_VALUE;
			long vertexMaxLoad = -Long.MAX_VALUE;
			double vertexExpectedLoad = ((double) getTotalNumVertices()) / (k);

			realEdgeBalanceJSD = 0;
			virtualEdgeBalanceJSD = 0;
			realAvgDegreeBalanceJSD = 0;
			virtualAvgDegreeBalanceJSD = 0;
			vertexBalanceJSD = 0;

			double u = ((double) 1 / k), m;

			double avgrealnorm = 0;
			double avgvirnorm = 0;
			realLoads = new long[k];
			virtualLoads = new long[k];
			vCounts = new long[k];
			realAvgDegrees = new double[k];
			virtualAvgDegrees = new double[k];

			for (int i = 0; i < k; i++) {

				realLoads[i] = ((LongWritable) getAggregatedValue(realLoadAggregatorNames[i])).get();
				virtualLoads[i] = ((LongWritable) getAggregatedValue(virtualLoadAggregatorNames[i])).get();
				vCounts[i] = ((LongWritable) getAggregatedValue(finalVertexCounts[i])).get();

				realAvgDegrees[i] = ((double) realLoads[i] / vCounts[i]);
				virtualAvgDegrees[i] = ((double) virtualLoads[i] / vCounts[i]);

				avgrealnorm += realAvgDegrees[i];
				avgvirnorm += virtualAvgDegrees[i];
			}

			for (int i = 0; i < k; i++) {

				if (realLoads[i] < realMinLoad) {
					realMinLoad = realLoads[i];
				}
				if (realLoads[i] > realMaxLoad) {
					realMaxLoad = realLoads[i];
				}

				if (virtualLoads[i] < virtualMinLoad) {
					virtualMinLoad = virtualLoads[i];
				}
				if (virtualLoads[i] > virtualMaxLoad) {
					virtualMaxLoad = virtualLoads[i];
				}

				// avg
				if (realAvgDegrees[i] < realMinAvgDegree) {
					realMinAvgDegree = realAvgDegrees[i];
				}
				if (realAvgDegrees[i] > realMaxAvgDegree) {
					realMaxAvgDegree = realAvgDegrees[i];
				}

				if (virtualAvgDegrees[i] < virtualMinAvgDegree) {
					virtualMinAvgDegree = virtualAvgDegrees[i];
				}
				if (virtualAvgDegrees[i] > virtualMaxAvgDegree) {
					virtualMaxAvgDegree = virtualAvgDegrees[i];
				}

				if (vCounts[i] < vertexMinLoad) {
					vertexMinLoad = vCounts[i];
				}
				if (vCounts[i] > vertexMaxLoad) {
					vertexMaxLoad = vCounts[i];
				}

				/*
				 * System.out.println("partition " + i); System.out.println("realAvgDegree " +
				 * realAvgDegree); System.out.println("virtualAvgDegree " + virtualAvgDegree);
				 * System.out.println("realLoad " + realLoad); System.out.println("virtualLoad "
				 * + virtualLoad); System.out.println("vertexCount " + vCount);
				 */

				double realLoadProb = ((double) realLoads[i]) / totalNumRealEdges;
				m = (realLoadProb + u) / 2;
				realEdgeBalanceJSD += realLoadProb * Math.log(realLoadProb / m) + u * Math.log(u / m);

				double virtualLoadProb = ((double) virtualLoads[i]) / getTotalNumEdges();
				m = (virtualLoadProb + u) / 2;
				virtualEdgeBalanceJSD += virtualLoadProb * Math.log(virtualLoadProb / m) + u * Math.log(u / m);

				// avg
				double realAvgDegreeProb = ((double) realAvgDegrees[i]) / (avgrealnorm);
				m = (realAvgDegreeProb + u) / 2;
				realAvgDegreeBalanceJSD += realAvgDegreeProb * Math.log(realAvgDegreeProb / m) + u * Math.log(u / m);

				double virtualAvgDegreeLoadProb = ((double) virtualAvgDegrees[i]) / (avgvirnorm);
				m = (virtualAvgDegreeLoadProb + u) / 2;
				virtualAvgDegreeBalanceJSD += virtualAvgDegreeLoadProb * Math.log(virtualAvgDegreeLoadProb / m)
						+ u * Math.log(u / m);

				// JensenShannonDivergence
				double vCountProb = ((double) vCounts[i]) / getTotalNumVertices();
				m = (vCountProb + u) / 2;
				vertexBalanceJSD += vCountProb * Math.log(vCountProb / m) + u * Math.log(u / m);
			}

			realMaxNormLoad = ((double) realMaxLoad) / realExpectedLoad;
			realMaxMinLoad = ((double) realMaxLoad) / realMinLoad;
			realEdgeBalanceJSD = realEdgeBalanceJSD / (2 * Math.log(2));

			virtualMaxNormLoad = ((double) virtualMaxLoad) / virtualExpectedLoad;
			virtualMaxMinLoad = ((double) virtualMaxLoad) / virtualMinLoad;
			virtualEdgeBalanceJSD = virtualEdgeBalanceJSD / (2 * Math.log(2));

			// avg
			realMaxNormAvgDegree = ((double) realMaxAvgDegree) / realExpectedAvgDegree;
			realMaxMinAvgDegree = ((double) realMaxAvgDegree) / realMinAvgDegree;
			realAvgDegreeBalanceJSD = realAvgDegreeBalanceJSD / (2 * Math.log(2));

			virtualMaxNormAvgDegree = ((double) virtualMaxAvgDegree) / virtualExpectedAvgDegree;
			virtualMaxMinAvgDegree = ((double) virtualMaxAvgDegree) / virtualMinAvgDegree;
			virtualAvgDegreeBalanceJSD = virtualAvgDegreeBalanceJSD / (2 * Math.log(2));

			vertexMaxNormLoad = ((double) vertexMaxLoad) / vertexExpectedLoad;
			vertexMaxMinLoad = ((double) vertexMaxLoad) / vertexMinLoad;
			vertexBalanceJSD = vertexBalanceJSD / (2 * Math.log(2));

			saveRealStats();
			saveVirtualStats();
			savePartitionStats();
		}

		@SuppressWarnings("unchecked")
		/**
		 * A method to store the computational time stat.
		 * 
		 * @param isSpinner a flag to distinguish between our Method 
		 * and Spinner method
		 * 
		 * @param totalMigrations
		 */
		protected void saveTimersStats(boolean isSpinner, long totalMigrations) {
			long firstLPIteration;
			long initializingLPTime;

			if (isSpinner) {// Spinner algo.
				if (repartition == 0)
					initializingLPTime = getContext().getCounter("Giraph Timers", "Superstep 2 Initializer (ms)")
							.getValue();
				else
					initializingLPTime = getContext().getCounter("Giraph Timers", "Superstep 2 Repartitioner (ms)")
							.getValue();
				firstLPIteration = getContext().getCounter("Giraph Timers", "Superstep 3 ComputeNewPartition (ms)")
						.getValue()
						+ getContext().getCounter("Giraph Timers", "Superstep 4 ComputeMigration (ms)").getValue();
			} else {// Our algo.
				if (repartition == 0)
					initializingLPTime = getContext()
							.getCounter("Giraph Timers", "Superstep 2 PotentialVerticesInitializer (ms)").getValue();
				else
					initializingLPTime = getContext().getCounter("Giraph Timers", "Superstep 2 Repartitioner (ms)")
							.getValue();

				firstLPIteration = getContext().getCounter("Giraph Timers", "Superstep 3 ComputeFirstPartition (ms)")
						.getValue()
						+ getContext().getCounter("Giraph Timers", "Superstep 4 ComputeFirstMigration (ms)").getValue();
			}

			long totalIterationsTime = firstLPIteration;
			float avg = 0;
			long totalLPSupersteps = getSuperstep() - 4;

			// compute the average superstep time
			for (int i = 5; i < getSuperstep() - 1; i = i + 2) {
				totalIterationsTime += getContext()
						.getCounter("Giraph Timers", "Superstep " + i + " ComputeNewPartition (ms)").getValue()
						+ getContext().getCounter("Giraph Timers", "Superstep " + (i + 1) + " ComputeMigration (ms)")
								.getValue();
			}
			avg = (float) totalIterationsTime / (totalLPSupersteps / 2);

			getContext().getCounter(PARTITION_COUNTER_GROUP, "Total LP (ms)").increment((long) (totalIterationsTime));
			getContext().getCounter(PARTITION_COUNTER_GROUP, MIGRATIONS_COUNTER).increment(totalMigrations);
			getContext().getCounter(PARTITION_COUNTER_GROUP, SCORE_COUNTER).increment((long) (1000 * score));
			long totalRealEdges = ((LongWritable) getAggregatedValue(TOTAL_DIRECTED_OUT_EDGES)).get();

			String date = new SimpleDateFormat("yyyy-MM-dd--hh:mm:ss").format(new Date());
			if (isSaveStatsIntoFile) {
				try {
					String[] str = getContext().getJobName().split(";");
					String filename = "/users/lahdak/adnanmoe/PaperResult/"+str[1]+"-TimeComparison.csv";
					FileWriter file = new FileWriter(filename, true);
					file.write(getContext().getJobID() + ";");
					file.write(date + ";");
					file.write(getContext().getJobName() + ";");
					file.write(getTotalNumVertices() + ";");
					file.write(getTotalNumEdges() + ";");
					file.write(totalRealEdges + ";");
					file.write(String.format("%.3f", ((float) getTotalNumEdges()) / totalRealEdges) + ";");
					file.write(getConf().getMaxWorkers() + ";");
					file.write(numberOfPartitions + repartition + ";");
					file.write((int) (totalLPSupersteps) / 2 + ";");
					file.write(initializingLPTime + ";");
					file.write(firstLPIteration + ";");
					file.write(totalIterationsTime + ";");
					file.write(getContext().getCounter("Giraph Timers", "Input superstep (ms)").getValue() + ";");
					file.write((long) avg + ";");
					file.write(totalMigrations + ";");
					file.write(getContext().getCounter("Giraph Stats", "Aggregate sent messages").getValue() + ";");
					file.write(getContext().getCounter(Task.Counter.VIRTUAL_MEMORY_BYTES).getValue() + ";");
					file.write(getContext().getCounter(Task.Counter.PHYSICAL_MEMORY_BYTES).getValue() + ";");
					file.write(getContext().getCounter(Task.Counter.COMMITTED_HEAP_BYTES).getValue() + ";");
					file.write(getContext().getCounter(Task.Counter.MAP_INPUT_BYTES).getValue() + ";");
					file.write(getContext().getCounter(Task.Counter.CPU_MILLISECONDS).getValue() + ";");				
					file.write(outDegreeThreshold + "\n");				
					file.flush();
					file.close();

					// rsync --partial --progress --rsh=ssh [source] [user]@[host]:[destination]
					Runtime.getRuntime().exec("rsync --rsh=ssh " + filename + " adnanmoe@129.175.25.75:~/PaperResult/");

				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				;
			}
		}
		
		/**
		 * A method to store the partition stat. based on real edges
		 */
		protected void saveRealStats() {
			long totalRealEdges = ((LongWritable) getAggregatedValue(TOTAL_DIRECTED_OUT_EDGES)).get();

			getContext().getCounter(PARTITION_COUNTER_GROUP, "R Maxmin unbalance (x1000)")
					.increment((long) (1000 * realMaxMinLoad));
			getContext().getCounter(PARTITION_COUNTER_GROUP, "R MaxNorm unbalance (x1000)")
					.increment((long) (1000 * realMaxNormLoad));

			// Local edges
			long realLocalEdges = ((LongWritable) getAggregatedValue(AGG_REAL_LOCAL_EDGES)).get();
			getContext().getCounter(PARTITION_COUNTER_GROUP, "#real LE").increment(realLocalEdges);
			double realLocalEdgesPct = ((double) realLocalEdges) / totalRealEdges;
			getContext().getCounter(PARTITION_COUNTER_GROUP, "#real LE (%%)")
					.increment((long) (100 * realLocalEdgesPct));

			// Edge cuts
			long realEdgeCut = ((LongWritable) getAggregatedValue(AGG_REAL_EDGE_CUTS)).get();
			getContext().getCounter(PARTITION_COUNTER_GROUP, "#real EC").increment(realEdgeCut);
			double realEdgeCutPct = ((double) realEdgeCut) / totalRealEdges;
			getContext().getCounter(PARTITION_COUNTER_GROUP, "#real EC (%%)").increment((long) (100 * realEdgeCutPct));

			// Communication volume
			long realComVolume = ((LongWritable) getAggregatedValue(AGG_REAL_TOTAL_COMM_VOLUME)).get();
			long realComVolumeNormalisationFactor = ((LongWritable) getAggregatedValue(
					AGG_REAL_UPPER_TOTAL_COMM_VOLUME)).get();
			double realComVolumePct = ((double) realComVolume) / realComVolumeNormalisationFactor;
			getContext().getCounter(PARTITION_COUNTER_GROUP, "real CV (%%)").increment((long) (100 * realComVolumePct));

			getContext().getCounter(PARTITION_COUNTER_GROUP, Vertex_Balance_JSD_COUNTER)
					.increment((long) (1000 * vertexBalanceJSD));

			getContext().getCounter(PARTITION_COUNTER_GROUP, Edge_Balance_JSD_COUNTER)
					.increment((long) (1000 * realEdgeBalanceJSD));

			if (isSaveStatsIntoFile) {
				try {

					String[] str = getContext().getJobName().split(";");
					String filename = "/users/lahdak/adnanmoe/PaperResult/"+str[1]+"-RealCounters.csv";
					FileWriter file = new FileWriter(filename, true);
					file.write(getContext().getJobID() + ";");
					file.write(getContext().getJobName() + ";");
					file.write(numberOfPartitions + repartition + ";");
					file.write(String.format("%.3f", realMaxNormLoad) + ";");
					file.write(String.format("%.3f", realMaxMinLoad) + ";");
					file.write(String.format("%.6f", realEdgeBalanceJSD) + ";");
					file.write(String.format("%.6f", divergenceKLU(realLoads)) + ";");

					file.write(String.format("%.3f", vertexMaxNormLoad) + ";");
					file.write(String.format("%.3f", vertexMaxMinLoad) + ";");
					file.write(String.format("%.6f", vertexBalanceJSD) + ";");
					file.write(String.format("%.6f", divergenceKLU(vCounts)) + ";");

					file.write(String.format("%.3f", realMaxNormAvgDegree) + ";");
					file.write(String.format("%.3f", realMaxMinAvgDegree) + ";");
					file.write(String.format("%.6f", realAvgDegreeBalanceJSD) + ";");
					file.write(String.format("%.6f", divergenceKLU(realAvgDegrees)) + ";");

					file.write(String.format("%.3f", realEdgeCutPct) + ";");
					file.write(String.format("%.3f", realComVolumePct) + ";");
					file.write(String.format("%.3f", realLocalEdgesPct) + ";");
					file.write(realEdgeCut + ";");
					file.write(realComVolume + ";");
					file.write(realLocalEdges + ";");
					file.write(realComVolumeNormalisationFactor + ";");
					file.write(score + ";");
					file.write("\n");
					file.flush();
					file.close();

					// rsync --partial --progress --rsh=ssh [source] [user]@[host]:[destination]
					Runtime.getRuntime().exec("rsync --rsh=ssh " + filename + " adnanmoe@129.175.25.75:~/PaperResult/");

				} catch (IOException e) { // TODO Auto-generated catch block
					e.printStackTrace();
				}
				;
			}
		}

		/**
		 * A method to store the partition stat. based on virtual edges
		 */
		protected void saveVirtualStats() {
			long totalRealEdges = ((LongWritable) getAggregatedValue(TOTAL_DIRECTED_OUT_EDGES)).get();

			getContext().getCounter(PARTITION_COUNTER_GROUP, "V Maxmin unbalance (x1000)")
					.increment((long) (1000 * virtualMaxMinLoad));
			getContext().getCounter(PARTITION_COUNTER_GROUP, "V MaxNorm unbalance (x1000)")
					.increment((long) (1000 * virtualMaxNormLoad));

			// Local edges
			long virtualLocalEdges = ((LongWritable) getAggregatedValue(AGG_VIRTUAL_LOCALS)).get();
			getContext().getCounter(PARTITION_COUNTER_GROUP, "#virtual local E").increment(virtualLocalEdges);
			double virtualLocalEdgesPct = ((double) virtualLocalEdges) / getTotalNumEdges();
			getContext().getCounter(PARTITION_COUNTER_GROUP, "virtual local E (%%)")
					.increment((long) (100 * virtualLocalEdgesPct));

			// Edge cuts
			long virtualEdgeCut = ((LongWritable) getAggregatedValue(AGG_VIRTUAL_EDGE_CUTS)).get();
			getContext().getCounter(PARTITION_COUNTER_GROUP, "#virtrual EC").increment(virtualEdgeCut);
			double virtualEdgeCutPct = ((double) virtualEdgeCut) / getTotalNumEdges();
			getContext().getCounter(PARTITION_COUNTER_GROUP, "virtrual EC (%%)")
					.increment((long) (100 * virtualEdgeCutPct));

			// Communication volume
			long virtualComVolume = ((LongWritable) getAggregatedValue(AGG_VIRTUAL_TOTAL_COMM_VOLUME)).get();
			long virtualComVolumeNormalisationFactor = ((LongWritable) getAggregatedValue(
					AGG_VIRTUAL_UPPER_TOTAL_COMM_VOLUME)).get();
			double virtualComVolumePct = ((double) virtualComVolume) / virtualComVolumeNormalisationFactor;
			getContext().getCounter(PARTITION_COUNTER_GROUP, "virtrual CV (%%)")
					.increment((long) (100 * virtualComVolumePct));

			getContext().getCounter(PARTITION_COUNTER_GROUP, Vertex_Balance_JSD_COUNTER)
					.increment((long) (1000 * vertexBalanceJSD));

			getContext().getCounter(PARTITION_COUNTER_GROUP, Edge_Balance_JSD_COUNTER)
					.increment((long) (1000 * virtualEdgeBalanceJSD));

			if (isSaveStatsIntoFile) {
				try {
					String[] str = getContext().getJobName().split(";");
					String filename = "/users/lahdak/adnanmoe/PaperResult/"+str[1]+"-VirtualCounters.csv";
					FileWriter file = new FileWriter(filename, true);
					file.write(getContext().getJobID() + ";");
					file.write(getContext().getJobName() + ";");
					file.write(numberOfPartitions + repartition + ";");
					file.write(String.format("%.3f", virtualMaxNormLoad) + ";");
					file.write(String.format("%.3f", virtualMaxMinLoad) + ";");
					file.write(String.format("%.6f", virtualEdgeBalanceJSD) + ";");
					file.write(String.format("%.6f", divergenceKLU(virtualLoads)) + ";");

					file.write(String.format("%.3f", vertexMaxNormLoad) + ";");
					file.write(String.format("%.3f", vertexMaxMinLoad) + ";");
					file.write(String.format("%.6f", vertexBalanceJSD) + ";");
					file.write(String.format("%.6f", divergenceKLU(vCounts)) + ";");

					file.write(String.format("%.3f", virtualMaxNormAvgDegree) + ";");
					file.write(String.format("%.3f", virtualMaxMinAvgDegree) + ";");
					file.write(String.format("%.6f", virtualAvgDegreeBalanceJSD) + ";");
					file.write(String.format("%.6f", divergenceKLU(virtualAvgDegrees)) + ";");

					file.write(String.format("%.3f", virtualEdgeCutPct) + ";");
					file.write(String.format("%.3f", virtualComVolumePct) + ";");
					file.write(String.format("%.3f", virtualLocalEdgesPct) + ";");
					file.write(virtualEdgeCut + ";");
					file.write(virtualComVolume + ";");
					file.write(virtualLocalEdges + ";");
					file.write(virtualComVolumeNormalisationFactor + ";");
					file.write("\n");
					file.flush();
					file.close();

					// rsync --partial --progress --rsh=ssh [source] [user]@[host]:[destination]
					Runtime.getRuntime().exec("rsync --rsh=ssh " + filename + " adnanmoe@129.175.25.75:~/PaperResult/");

				} catch (IOException e) { // TODO Auto-generated catch block
					e.printStackTrace();
				}
				;
			}
		}
		
		/**
		 * A method to store aditional stat.
		 */
		protected void savePartitionStats() {
			if (isSaveStatsIntoFile) {
				int k = numberOfPartitions + repartition;
				String str1 = "", str2 = "", str3 = "";
				for (int i = 0; i < k; i++) {
					str1 += realLoads[i] + ";";
					str2 += virtualLoads[i] + ";";
					str3 += vCounts[i] + ";";
				}

				try {
					String[] str = getContext().getJobName().split(";");
					String filename = "/users/lahdak/adnanmoe/PaperResult/" + str[1] + ".csv";
					FileWriter file = new FileWriter(filename, true);
					file.write(str[0] + "\n");
					file.write("partition = " + k + "\n");
					file.write("realLoads: \n");
					file.write(str1 + "\n");
					file.write("virtualLoads:\n");
					file.write(str2 + "\n");
					file.write("vCounts\n");
					file.write(str3 + "\n");
					file.write("Kullback-Leibler divergence from U: \n");
					file.write((float) divergenceKLU(realLoads) + "\n");
					file.write((float) divergenceKLU(virtualLoads) + "\n");
					file.write((float) divergenceKLU(vCounts) + "\n");
					file.write("KL realAvgDegrees:\n");
					file.write((float) divergenceKLU(realAvgDegrees) + "\n");
					file.write("KL virtualAvgDegrees:\n");
					file.write((float) divergenceKLU(virtualAvgDegrees) + "\n");
					file.write("\n");
					file.flush();
					file.close();

					// rsync --partial --progress --rsh=ssh [source] [user]@[host]:[destination]
					Runtime.getRuntime().exec("rsync --rsh=ssh " + filename + " adnanmoe@129.175.25.75:~/PaperResult/");

				} catch (IOException e) { // TODO Auto-generated catch block
					e.printStackTrace();
				}
				;
			}
		}

		public double divergenceKLU(double[] p) {
			double distance = 0;
			double sum = sum(p);

			double u = ((double) 1) / (p.length);
			for (int i = 0; i < p.length; i++) {
				if (p[i] != 0) {
					double a = p[i] / sum;
					distance += a * Math.log(a / u);
				} else {
					return -1;
				}
			}
			return Math.abs(distance);
		}

		private double divergenceKLU(long[] p) {
			double distance = 0;

			long sum = sum(p);
			double u = ((double) 1) / (p.length);

			for (int i = 0; i < p.length; i++) {
				if (p[i] != 0) {
					double a = ((double) p[i]) / sum;
					distance += a * Math.log(a / u);
				} else {
					return -1;
				}
			}
			return distance / Math.log(p.length);
		}

		private long sum(long[] p) {
			long sum = 0;
			for (int i = 0; i < p.length; i++) {
				sum += p[i];
			}
			return sum;
		}

		private double sum(double[] p) {
			double sum = 0;
			for (int i = 0; i < p.length; i++) {
				sum += p[i];
			}
			return sum;
		}
	}

}
