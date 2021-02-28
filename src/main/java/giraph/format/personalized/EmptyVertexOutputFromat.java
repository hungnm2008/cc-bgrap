package giraph.format.personalized;

import java.io.IOException;

import org.apache.giraph.graph.Vertex;
import org.apache.giraph.io.formats.TextVertexOutputFormat;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.mapreduce.TaskAttemptContext;


public class EmptyVertexOutputFromat <I extends LongWritable, V extends Writable, E extends Writable>
	extends TextVertexOutputFormat<I, V, E> {
/** Specify the output delimiter */
public static final String LINE_TOKENIZE_VALUE = "output.delimiter";
/** Default output delimiter */
public static final String LINE_TOKENIZE_VALUE_DEFAULT = "\t";

public TextVertexWriter createVertexWriter(TaskAttemptContext context) {
	return new VertexValueWriter();
}

protected class VertexValueWriter extends TextVertexWriterToEachLine {
	@Override
	public void initialize(TaskAttemptContext context) throws IOException, InterruptedException {
		super.initialize(context);
		Configuration conf = context.getConfiguration();
		conf.get(LINE_TOKENIZE_VALUE, LINE_TOKENIZE_VALUE_DEFAULT);
	}

	@Override
	protected Text convertVertexToLine(Vertex<I, V, E> vertex) throws IOException {
		return new Text("");
	}
}
}
