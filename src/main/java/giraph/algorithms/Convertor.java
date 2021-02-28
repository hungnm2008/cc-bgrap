package giraph.algorithms;

import java.io.IOException;

import org.apache.giraph.graph.AbstractComputation;
import org.apache.giraph.graph.Vertex;
import org.apache.hadoop.io.LongWritable;

import giraph.ml.grafos.okapi.spinner.EdgeValue;
import giraph.ml.grafos.okapi.spinner.VertexValue;

public class Convertor
			extends AbstractComputation<LongWritable, VertexValue, EdgeValue, LongWritable, LongWritable> {

		@Override
		public void compute(Vertex<LongWritable, VertexValue, EdgeValue> vertex, Iterable<LongWritable> messages)
				throws IOException {
			vertex.voteToHalt();
		}
	}