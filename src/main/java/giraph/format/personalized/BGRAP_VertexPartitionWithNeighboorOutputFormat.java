package giraph.format.personalized;

import java.io.IOException;
import org.apache.giraph.edge.Edge;
import org.apache.giraph.graph.Vertex;
import org.apache.giraph.io.formats.TextVertexOutputFormat;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.TaskAttemptContext;

import giraph.ml.grafos.okapi.spinner.EdgeValue;
import giraph.ml.grafos.okapi.spinner.VertexValue;

/**
 * Simple text-based {@link org.apache.giraph.io.VertexInputFormat} for
 * unweighted graphs with long ids.
 *
 * Each line consists of: vertex_partition neighbor1 neighbor2 ...
 * (for example : 1250_1 189 1251  ...
 * 
 * @author Adnan EL MOUSSAWI
 *
 */
public class BGRAP_VertexPartitionWithNeighboorOutputFormat
		extends TextVertexOutputFormat<LongWritable, VertexValue, EdgeValue> {
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
		protected Text convertVertexToLine(Vertex<LongWritable, VertexValue, EdgeValue> vertex)
				throws IOException {
			StringBuffer sb = new StringBuffer(vertex.getId().get() + "_"
					+ vertex.getValue().getCurrentPartition()
					);
			/* store neighbor vertices without their partition id */
			for (Edge<LongWritable, EdgeValue> edge : vertex.getEdges()) {
				if(! edge.getValue().isVirtualEdge() )
					sb.append(delimiter).append(edge.getTargetVertexId().get());
			}
			

			return new Text(sb.toString());
		}
	}
}
