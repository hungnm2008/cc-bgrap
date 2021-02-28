package giraph.format.personalized;

import java.io.IOException;
import java.util.regex.Pattern;

import org.apache.giraph.io.formats.TextVertexValueInputFormat;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.TaskAttemptContext;

import giraph.ml.grafos.okapi.spinner.EdgeValue;
import giraph.ml.grafos.okapi.spinner.VertexValue;

public class PartitioningVertexInputFormat
		extends TextVertexValueInputFormat<PartitionedLongWritable, VertexValue, EdgeValue> {
	private static final Pattern SEPARATOR = Pattern.compile("[\t ]");

	@Override
	public LongShortTextVertexValueReader createVertexValueReader(InputSplit split, TaskAttemptContext context)
			throws IOException {
		return new LongShortTextVertexValueReader();
	}

	public class LongShortTextVertexValueReader extends TextVertexValueReaderFromEachLineProcessed<String[]> {
		private PartitionedLongWritable id;

		@Override
		protected String[] preprocessLine(Text line) throws IOException {
			id = new PartitionedLongWritable(line.toString());
			return SEPARATOR.split(line.toString());
		}

		@Override
		protected PartitionedLongWritable getId(String[] data) throws IOException {
			return id;
		}

		@Override
		protected VertexValue getValue(String[] data) throws IOException {
			VertexValue value = new VertexValue();
			if (data.length > 1) {
				short partition = Short.parseShort(data[1]);
				value.setCurrentPartition(partition);
				value.setNewPartition(partition);
			}
			return value;
		}
	}
}
