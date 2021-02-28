package giraph.format.personalized;

import java.io.IOException;
import org.apache.giraph.edge.Edge;
import org.apache.giraph.graph.Vertex;
import org.apache.giraph.io.formats.TextVertexOutputFormat;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.TaskAttemptContext;

/**
 * Simple text-based {@link org.apache.giraph.io.VertexInputFormat} for
 * unweighted graphs with long ids.
 *
 * Each line consists of: vertex neighbor1 neighbor2 ...
 * (for example : 1250 189 1251 ...
 * 
 * @author Adnan EL MOUSSAWI
 *
 */
public class VertexNeighboorListOutputFormat
		extends TextVertexOutputFormat<LongWritable, NullWritable, NullWritable> {
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
		protected Text convertVertexToLine(Vertex<LongWritable, NullWritable, NullWritable> vertex)
				throws IOException {
			if(vertex.getNumEdges()>0) {
				StringBuffer sb = new StringBuffer(""+vertex.getId().get());
				/* store vertices with their partition id */
				for (Edge<LongWritable, NullWritable> edge : vertex.getEdges()) {
					sb.append(delimiter).append(
							edge.getTargetVertexId().get()
							);
				}
				return new Text(sb.toString());
			}

			return new Text();
		}
	}
}
