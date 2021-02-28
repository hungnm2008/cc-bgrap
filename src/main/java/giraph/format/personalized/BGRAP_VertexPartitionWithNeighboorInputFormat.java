package giraph.format.personalized;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.giraph.edge.Edge;
import org.apache.giraph.edge.EdgeFactory;
import org.apache.giraph.io.formats.TextVertexInputFormat;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.TaskAttemptContext;

import com.google.common.collect.Lists;

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
public class BGRAP_VertexPartitionWithNeighboorInputFormat
		extends TextVertexInputFormat<LongWritable, VertexValueWithPartition, DoubleWritable> {
	private static final Pattern SEPARATOR = Pattern.compile("[\t ]");
	private static final String PARTITION_DELIMITER = "_";

	@Override
	public TextVertexReader createVertexReader(InputSplit split, TaskAttemptContext context) throws IOException {
		return new LongPartitionDoubleDoubleVertexReader();
	}

	public class LongPartitionDoubleDoubleVertexReader extends TextVertexReaderFromEachLineProcessed<String[]> {
		//cached id and partition
		private long id;
		private short partition=-1;
		
		@Override
		protected String[] preprocessLine(Text line) throws IOException {
			String[] data = SEPARATOR.split(line.toString());
			String[] tokens = data[0].split(PARTITION_DELIMITER);
			id = Long.parseLong(tokens[0]);
			if(tokens.length==2) partition = Short.parseShort(tokens[1]);
			return data;
		}

		@Override
		protected LongWritable getId(String[] data) throws IOException {			
			return new LongWritable(id);
		}

		@Override
		protected VertexValueWithPartition getValue(String[] data) throws IOException {
			return new VertexValueWithPartition(partition);
		}

		@Override
	    protected List<Edge<LongWritable, DoubleWritable>> getEdges(
	        String[] tokens) throws IOException {
	      List<Edge<LongWritable, DoubleWritable>> edges =
	          Lists.newArrayListWithCapacity(tokens.length - 1);
	      for (int n = 1; n < tokens.length; n++) {
	    	  edges.add(EdgeFactory.create( new LongWritable(Long.parseLong(tokens[n])), 
	    			  new DoubleWritable(1)));
	      }
	      return edges;
	    }
	}
}
