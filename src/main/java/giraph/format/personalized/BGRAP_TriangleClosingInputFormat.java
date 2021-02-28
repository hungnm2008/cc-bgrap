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

import giraph.algorithms.SimpleTriangleClosingComputation.BGRAP_LongWritableArrayListWritable;

public class BGRAP_TriangleClosingInputFormat
		extends TextVertexInputFormat<BGRAP_LongWritable, BGRAP_LongWritableArrayListWritable, DoubleWritable> {
	private static final Pattern SEPARATOR = Pattern.compile("[\t ]");

	@Override
	public TextVertexReader createVertexReader(InputSplit split, TaskAttemptContext context) throws IOException {
		return new SpinnerLongDoubleDoubleVertexReader();
	}

	public class SpinnerLongDoubleDoubleVertexReader extends TextVertexReaderFromEachLineProcessed<String[]> {
		@Override
		protected String[] preprocessLine(Text line) throws IOException {
			return SEPARATOR.split(line.toString());
		}

		@Override
		protected BGRAP_LongWritable getId(String[] data) throws IOException {			
			return new BGRAP_LongWritable(data[0]);
		}

		@Override
		protected BGRAP_LongWritableArrayListWritable getValue(String[] data) throws IOException {
			return new BGRAP_LongWritableArrayListWritable();
		}

		@Override
	    protected List<Edge<BGRAP_LongWritable, DoubleWritable>> getEdges(
	        String[] tokens) throws IOException {
	      List<Edge<BGRAP_LongWritable, DoubleWritable>> edges =
	          Lists.newArrayListWithCapacity(tokens.length - 1);
	      for (int n = 1; n < tokens.length; n++) {
	    	  //SpinnerLongWritable target = new SpinnerLongWritable(tokens[n]);
	    	  edges.add(EdgeFactory.create( new BGRAP_LongWritable(tokens[n]), 
	    			  new DoubleWritable(1)));
	      }
	      return edges;
	    }
	}
}
