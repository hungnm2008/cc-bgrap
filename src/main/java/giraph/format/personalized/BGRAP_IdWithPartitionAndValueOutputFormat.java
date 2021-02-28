package giraph.format.personalized;

import java.io.IOException;
import org.apache.giraph.graph.Vertex;
import org.apache.giraph.io.formats.TextVertexOutputFormat;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.mapreduce.TaskAttemptContext;


/**
 * Simple text-based {@link org.apache.giraph.io.VertexOutputFormat} for
 * unweighted graphs with long ids, short partitions and double values.
 *
 * Each line consists of: vertex partition value
 */
public class BGRAP_IdWithPartitionAndValueOutputFormat<I extends BGRAP_LongWritable, V extends Writable, E extends Writable>
		extends TextVertexOutputFormat<I, V, E> {
	/** Specify the output delimiter */
	public static final String LINE_TOKENIZE_VALUE = "output.delimiter";
	/** Default output delimiter */
	public static final String LINE_TOKENIZE_VALUE_DEFAULT = "\t";

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

			str.append(vertex.getId().getId());
			str.append(delimiter);
			str.append(vertex.getId().getPartition());
			str.append(delimiter);
			str.append(vertex.getValue().toString());

			return new Text(str.toString());
		}
	}
}

/*
 * package giraph.format.personalized;
 * 
 * import java.io.IOException; import org.apache.giraph.edge.Edge; import
 * org.apache.giraph.graph.Vertex; import
 * org.apache.giraph.io.formats.TextVertexOutputFormat; import
 * org.apache.hadoop.io.LongWritable; import
 * org.apache.hadoop.conf.Configuration; import org.apache.hadoop.io.Text;
 * import org.apache.hadoop.mapreduce.TaskAttemptContext;
 * 
 * import spinnertest.Spinner.EdgeValue; import spinnertest.Spinner.VertexValue;
 * 
 * public class SpinnerPartitionedVertexOutputFormat extends
 * TextVertexOutputFormat<SpinnerLongWritable, SpinnerVertexValue,
 * SpinnerEdgeValue> {
 *//** Specify the output delimiter */
/*
 * public static final String LINE_TOKENIZE_VALUE = "output.delimiter";
 *//** Default output delimiter */
/*
 * public static final String LINE_TOKENIZE_VALUE_DEFAULT = "\t";
 * 
 * public TextVertexWriter createVertexWriter(TaskAttemptContext context) {
 * return new VertexValueWriter(); }
 * 
 * protected class VertexValueWriter extends TextVertexWriterToEachLine {
 *//** Saved delimiter *//*
							 * private String delimiter;
							 * 
							 * @Override public void initialize(TaskAttemptContext context) throws
							 * IOException, InterruptedException { super.initialize(context); Configuration
							 * conf = context.getConfiguration(); delimiter = conf.get(LINE_TOKENIZE_VALUE,
							 * LINE_TOKENIZE_VALUE_DEFAULT); }
							 * 
							 * @Override protected Text convertVertexToLine(Vertex<SpinnerLongWritable,
							 * SpinnerVertexValue, SpinnerEdgeValue> vertex) throws IOException {
							 * StringBuffer sb = new StringBuffer(vertex.getId().getId() + "_" +
							 * vertex.getValue().getCurrentPartition() ); sb.append(delimiter);
							 * 
							 * for (Edge<SpinnerLongWritable, EdgeValue> edge : vertex.getEdges()) {
							 * sb.append(delimiter).append(edge.getTargetVertexId().getId() + "_" +
							 * vertex.getValue().getCurrentPartition() ); }
							 * 
							 * return new Text(sb.toString()); } } }
							 */