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

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

import org.apache.giraph.aggregators.LongSumAggregator;
import org.apache.giraph.edge.Edge;
import org.apache.giraph.edge.EdgeFactory;
import org.apache.giraph.graph.AbstractComputation;
import org.apache.giraph.graph.Vertex;
import org.apache.giraph.io.formats.TextVertexInputFormat;
import org.apache.giraph.io.formats.TextVertexOutputFormat;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.TaskAttemptContext;

import com.google.common.collect.Lists;
import giraph.ml.grafos.okapi.spinner.EdgeValue;
import giraph.ml.grafos.okapi.spinner.PartitionMessage;
import giraph.ml.grafos.okapi.spinner.VertexValue;

@SuppressWarnings("unused")
public class PartitionQualityComputation extends LPGPartitionner {
	private static final String AGG_REAL_LOAD_PREFIX = "AGG_REAL_LOAD_";
	private static final String AGG_VIRTUAL_LOAD_PREFIX = "AGG_VIRTUAL_LOAD_";

	private static final String AGGREGATOR_DEMAND_PREFIX = "AGG_DEMAND_";

	private static final String AGG_REAL_LOCALS = "AGG_REAL_LOCALS";
	private static final String AGG_VIRTUAL_LOCALS = "AGG_VIRTUAL_LOCALS";

	private static final String NUM_PARTITIONS = "spinner.numberOfPartitions";
	private static final int DEFAULT_NUM_PARTITIONS = 8;// 32;

	private static final String ADDITIONAL_CAPACITY = "spinner.additionalCapacity";
	private static final float DEFAULT_ADDITIONAL_CAPACITY = 0.05f;

	private static final String DIRECTED_EDGE_WEIGHT = "edge.weight";
	private static final byte DEFAULT_EDGE_WEIGHT = 1;

	private static final String REPARTITION = "spinner.repartition";
	private static final short DEFAULT_REPARTITION = 0;

	private static final String PARTITION_COUNTER_GROUP = "Partitioning Counters";
	private static final String PCT_LOCAL_EDGES_COUNTER = "Local edges (%)";
	private static final String MAXMIN_UNBALANCE_COUNTER = "Maxmin unbalance (x1000)";
	private static final String MAX_NORMALIZED_UNBALANCE_COUNTER = "Max normalized unbalance (x1000)";
	private static final String SCORE_COUNTER = "Score (x1000)";

	// Adnan
	private static long totalVertexNumber;
	// Adnan : ration Communication Computation
	private static double ratioCC;

	// Adnan: some statistics about the partition
	private static final String AGG_REAL_EDGE_CUTS = "#REAL Edge cuts";
	private static final String AGG_VIRTUAL_EDGE_CUTS = "#VIRTUAL Edge cuts";

	private static final String AGG_REAL_TOTAL_COMM_VOLUME = "REAL Communication Volume";
	private static final String AGG_VIRTUAL_TOTAL_COMM_VOLUME = "VIRTUAL Communication Volume";

	private static final String TOTAL_COMM_VOLUME_PCT = "Comm. Volume %";
	private static final String TOTAL_REAL_COMM_VOLUME_PCT = "Real Comm. Volume %";
	private static final String AGG_VERTEX_BALANCE = "Vertex Balance";
	private static final String TOTAL_OUT_EDGES = "#Total Out Edges";

	private static final String AGG_REAL_UPPER_TOTAL_COMM_VOLUME = "# CV Upper Bound";
	private static final String AGG_VIRTUAL_UPPER_TOTAL_COMM_VOLUME = "#VIRTUAL CV Upper Bound";

	private static final String PARTITION_VCAPACITY_PREFIX = "AGG_VERTEX_CAPACITY_";

	// Adnan : weighting the two terms of the penality function
	public static final String KAPPA = "partition.edgeBalanceWeight.kappa";
	private static final float DEFAULT_KAPPA = 0.5f;
	public static final String GRAPH_DIRECTED = "graph.directed";
	private static final boolean DEFAULT_GRAPH_DIRECTED = false;
	private static final String NEW_REVERSE_EDGE_WEIGHT = "newReverseEdge.weight";
	private static final byte DEFAULT_NEW_REVERSE_EDGE_WEIGHT = 1;
	public static final String LOCAL_EDGE_COUNTER = "#local edges";
	public static final String EDGE_CUTS_PCT = "Edge Cuts (%)";
	public static final String REAL_EDGE_CUTS_PCT = "Real Edge Cuts (%)";
	public static final String Vertex_Balance_Entropy = "Vertex Balance Entropy";
	public static final String Edge_Balance_Entropy = "Edge Balance Entropy";

	private static Random rnd = new Random();

	public static class PropagateGraphPartition
			extends AbstractComputation<LongWritable, VertexValue, EdgeValue, LongWritable, LongWritable> {

		@Override
		public void compute(Vertex<LongWritable, VertexValue, EdgeValue> vertex, Iterable<LongWritable> messages)
				throws IOException {
			vertex.getValue().setRealOutDegree(vertex.getNumEdges());
			aggregate(TOTAL_OUT_EDGES, new LongWritable(vertex.getNumEdges()));
			//System.out.println(vertex.getId().get() + " " + vertex.getValue().getCurrentPartition());
			sendMessageToAllEdges(vertex, new LongWritable(vertex.getId().get()));

		}

	}

	public static class AddVirtualEdges
			extends AbstractComputation<LongWritable, VertexValue, EdgeValue, LongWritable, PartitionMessage> {
		private byte edgeWeight;
		private boolean graphDirected;
		private byte newReverseEdgesWeight;

		@Override
		public void compute(Vertex<LongWritable, VertexValue, EdgeValue> vertex, Iterable<LongWritable> messages)
				throws IOException {
			int incoming = 0;
			for (LongWritable other : messages) {
				EdgeValue edgeValue = vertex.getEdgeValue(new LongWritable(other.get()));

				if (edgeValue == null || edgeValue.isVirtualEdge()) {
					edgeValue = new EdgeValue();
					edgeValue.setWeight(newReverseEdgesWeight);
					edgeValue.setVirtualEdge(true);
					Edge<LongWritable, EdgeValue> edge = EdgeFactory.create(new LongWritable(other.get()), edgeValue);
					vertex.addEdge(edge);
				} else {
					edgeValue.setWeight(edgeWeight);
					vertex.setEdgeValue(other, edgeValue);
				}
				incoming++;
			}
			VertexValue value = vertex.getValue();
			value.setRealInDegree(incoming);
			vertex.setValue(value);

			sendMessageToAllEdges(vertex,
					new PartitionMessage(vertex.getId().get(), vertex.getValue().getCurrentPartition()));
		}

		@Override
		public void preSuperstep() {
			// default = 1
			graphDirected = getContext().getConfiguration().getBoolean(GRAPH_DIRECTED, DEFAULT_GRAPH_DIRECTED);

			newReverseEdgesWeight = (byte) getContext().getConfiguration().getInt(NEW_REVERSE_EDGE_WEIGHT,
					DEFAULT_NEW_REVERSE_EDGE_WEIGHT);
			edgeWeight = (graphDirected) ? 2
					: (byte) getContext().getConfiguration().getInt(DIRECTED_EDGE_WEIGHT, DEFAULT_EDGE_WEIGHT);
		}
	}

	public static class PartitionerMasterCompute extends SuperPartitionerMasterCompute {

		@Override
		public void initialize() throws InstantiationException, IllegalAccessException {
			maxIterations = getContext().getConfiguration().getInt(
					MAX_ITERATIONS_LP, DEFAULT_MAX_ITERATIONS);

			// DEFAULT_NUM_PARTITIONS = getConf().getMaxWorkers()*getConf().get();

			numberOfPartitions = getContext().getConfiguration().getInt(
					NUM_PARTITIONS, DEFAULT_NUM_PARTITIONS);
			//System.out.println("k="+numberOfPartitions);
			int weight = getContext().getConfiguration().getInt(
					EDGE_WEIGHT, DEFAULT_EDGE_WEIGHT);
			//System.out.println("weight="+weight);
			convergenceThreshold = getContext().getConfiguration().getFloat(
					CONVERGENCE_THRESHOLD, DEFAULT_CONVERGENCE_THRESHOLD);
			repartition = (short) getContext().getConfiguration().getInt(
					REPARTITION, DEFAULT_REPARTITION);
			windowSize = getContext().getConfiguration().getInt(
					WINDOW_SIZE, DEFAULT_WINDOW_SIZE);
			states = Lists.newLinkedList();
			// Create aggregators for each partition
			
			registerAggregator(AGGREGATOR_LOCALS, LongSumAggregator.class);
			registerAggregator(AGGREGATOR_MIGRATIONS, LongSumAggregator.class);

			// Added by Adnan
			registerAggregator(AGG_EDGE_CUTS, LongSumAggregator.class);
			registerPersistentAggregator(AGG_UPPER_TOTAL_COMM_VOLUME, LongSumAggregator.class);
			super.init();
		}

		@Override
		public void compute() {
			int superstep = (int) getSuperstep();
			// System.out.println("#E : " + totalNumEdges);
			if (superstep == 0) {
				// at this stage #edges , #vertices is not known
				setComputation(ConverterPropagate.class);
			} else if (superstep == 1) {
				// System.out.println("before stp1 "+ totalNumEdges);
				setComputation(ConverterUpdateEdges.class);
			} else if (superstep == 2) {
				setComputation(ComputeGraphPartitionStatistics.class);
				
			} else {
				System.out.println("Finish stats.");
				haltComputation();
				super.updatePartitioningQuality();
				saveRealStats();
				saveVirtualStats();
				savePartitionStats();
			}
		}
		

		protected void savePartitionStats() {
			int k = numberOfPartitions + repartition;
			String str1="",
					str2="", str3="";
			for (int i = 0; i < k; i++) {
				long realLoad = ((LongWritable) getAggregatedValue(realLoadAggregatorNames[i])).get();
				long virtualLoad = ((LongWritable) getAggregatedValue(virtualLoadAggregatorNames[i])).get();
				long vCount = ((LongWritable) getAggregatedValue(finalVertexCounts[i])).get();
				str1 += realLoad+";";
				str2 += virtualLoad+";";
				str3 += vCount+";";
			}

			try {
				String[] str = getContext().getJobName().split("-");
				String filename = "/users/lahdak/adnanmoe/PaperResult/"+str[1]+".csv";
				FileWriter file = new FileWriter(filename, true);
				file.write(str[0] + "\n");
				file.write("partition = "+k+ "\n");
				file.write(str1+ "\n");
				file.write(str2+ "\n");
				file.write(str3+ "\n");			
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

			try {
				String filename = "/users/lahdak/adnanmoe/PaperResult/RealCounters.csv";
				FileWriter file = new FileWriter(filename, true);
				file.write(getContext().getJobName().replace('-', ';') + ";");
				file.write(getTotalNumVertices() + ";");
				file.write(getTotalNumEdges() + ";");
				file.write(totalRealEdges + ";");
				file.write(String.format("%.3f", ((float) getTotalNumEdges()) / totalRealEdges) + ";");
				file.write(getConf().getMaxWorkers() + ";");
				file.write(numberOfPartitions + repartition + ";");
				file.write(String.format("%.3f", realMaxNormLoad) + ";");
				file.write(String.format("%.3f", realMaxMinLoad) + ";");
				file.write(String.format("%.8f", realEdgeBalanceJSD) + ";");
				file.write(String.format("%.3f", vertexMaxNormLoad) + ";");
				file.write(String.format("%.3f", vertexMaxMinLoad) + ";");
				file.write(String.format("%.8f", vertexBalanceJSD) + ";");
				file.write(String.format("%.3f", realMaxNormAvgDegree) + ";");
				file.write(String.format("%.3f", realMaxMinAvgDegree) + ";");
				file.write(String.format("%.8f", realAvgDegreeBalanceJSD) + ";");
				file.write(String.format("%.3f", realEdgeCutPct) + ";");
				file.write(String.format("%.3f", realComVolumePct) + ";");
				file.write(String.format("%.3f", realLocalEdgesPct) + ";");
				file.write(realEdgeCut + ";");
				file.write(realComVolume + ";");
				file.write(realLocalEdges + ";");
				file.write(realComVolumeNormalisationFactor + ";");
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

			try {
				String filename = "/users/lahdak/adnanmoe/PaperResult/VirtualCounters.csv";
				FileWriter file = new FileWriter(filename, true);
				file.write(getContext().getJobName().replace('-', ';') + ";");
				file.write(getTotalNumVertices() + ";");
				file.write(getTotalNumEdges() + ";");
				file.write(totalRealEdges + ";");
				file.write(String.format("%.2f", ((float) getTotalNumEdges() / totalRealEdges)) + ";");
				file.write(getConf().getMaxWorkers() + ";");
				file.write(numberOfPartitions + repartition + ";");
				file.write(String.format("%.3f", virtualMaxNormLoad) + ";");
				file.write(String.format("%.3f", virtualMaxMinLoad) + ";");
				file.write(String.format("%.8f", virtualEdgeBalanceJSD) + ";");
				file.write(String.format("%.3f", vertexMaxNormLoad) + ";");
				file.write(String.format("%.3f", vertexMaxMinLoad) + ";");
				file.write(String.format("%.8f", vertexBalanceJSD) + ";");
				file.write(String.format("%.3f", virtualMaxNormAvgDegree) + ";");
				file.write(String.format("%.3f", virtualMaxMinAvgDegree) + ";");
				file.write(String.format("%.8f", virtualAvgDegreeBalanceJSD) + ";");
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

	public static class OutputFormat<I extends LongWritable, V extends Writable, E extends Writable>
			extends TextVertexOutputFormat<I, V, E> {
		/** Specify the output delimiter */
		public static final String LINE_TOKENIZE_VALUE = "output.delimiter";
		/** Default output delimiter */
		public static final String LINE_TOKENIZE_VALUE_DEFAULT = "\t";

		@Override
		public TextVertexWriter createVertexWriter(TaskAttemptContext context) {
			return new VertexValueWriter();
		}

		protected class VertexValueWriter extends TextVertexWriterToEachLine {
			/** Saved delimiter */
			private String delimiter;

			@Override
			public void initialize(TaskAttemptContext context) throws IOException, InterruptedException {
				super.initialize(context);
				Configuration conf = context.getConfiguration();
				delimiter = conf.get(LINE_TOKENIZE_VALUE, LINE_TOKENIZE_VALUE_DEFAULT);
			}

			@Override
			protected Text convertVertexToLine(Vertex<I, V, E> vertex) throws IOException {

				StringBuilder str = new StringBuilder();

				str.append(vertex.getId());
				str.append(delimiter);
				VertexValue v = (VertexValue) vertex.getValue();
				str.append(v.getCurrentPartition());
				str.append(delimiter);
				str.append(v.getRealOutDegree());
				str.append(delimiter);
				str.append(v.getRealInDegree());

				return new Text(str.toString());
			}
		}
	}

	public static class EmptyOutputFormat<I extends LongWritable, V extends Writable, E extends Writable>
			extends TextVertexOutputFormat<I, V, E> {
		/** Specify the output delimiter */
		public static final String LINE_TOKENIZE_VALUE = "output.delimiter";
		/** Default output delimiter */
		public static final String LINE_TOKENIZE_VALUE_DEFAULT = "\t";

		@Override
		public TextVertexWriter createVertexWriter(TaskAttemptContext context) {
			return new VertexValueWriter();
		}

		protected class VertexValueWriter extends TextVertexWriterToEachLine {
			/** Saved delimiter */
			private String delimiter;

			@Override
			public void initialize(TaskAttemptContext context) throws IOException, InterruptedException {
				super.initialize(context);
				Configuration conf = context.getConfiguration();
				delimiter = conf.get(LINE_TOKENIZE_VALUE, LINE_TOKENIZE_VALUE_DEFAULT);
			}

			@Override
			protected Text convertVertexToLine(Vertex<I, V, E> vertex) throws IOException {
				return new Text("");
			}
		}
	}

	public static class InputFormat extends TextVertexInputFormat<LongWritable, VertexValue, EdgeValue> {
		private static final Pattern SEPARATOR = Pattern.compile("[\t ]");

		@Override
		public TextVertexReader createVertexReader(InputSplit split, TaskAttemptContext context) throws IOException {
			return new SpinnerLongDoubleDoubleVertexReader();
		}

		public class SpinnerLongDoubleDoubleVertexReader extends TextVertexReaderFromEachLineProcessed<String[]> {
			private long id;
			private short partition;

			@Override
			protected String[] preprocessLine(Text line) throws IOException {
				String[] data = SEPARATOR.split(line.toString());
				String[] id = data[0].split("_");
				this.id = Long.parseLong(id[0]);
				this.partition = Short.parseShort(id[1]);
				return data;
			}

			@Override
			protected LongWritable getId(String[] data) throws IOException {
				return new LongWritable(id);
			}

			@Override
			protected VertexValue getValue(String[] data) throws IOException {
				return new VertexValue(partition, partition);
			}

			@Override
			protected List<Edge<LongWritable, EdgeValue>> getEdges(String[] tokens) throws IOException {
				List<Edge<LongWritable, EdgeValue>> edges = Lists.newArrayListWithCapacity(tokens.length - 1);
				for (int n = 1; n < tokens.length; n++) {
					edges.add(EdgeFactory.create(
							new LongWritable(Long.parseLong(tokens[n])), new EdgeValue( (short)-1, (byte) 2, false)));
							
				}
				return edges;
			}
		}
	}
}
