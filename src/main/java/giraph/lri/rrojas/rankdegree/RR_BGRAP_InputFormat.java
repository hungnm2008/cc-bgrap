/**
 * Copyright 2014 Grafos.ml
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

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.giraph.edge.Edge;
import org.apache.giraph.edge.EdgeFactory;
import org.apache.giraph.io.formats.TextVertexInputFormat;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.TaskAttemptContext;

import com.google.common.collect.Lists;

import giraph.ml.grafos.okapi.spinner.EdgeValue;
import giraph.ml.grafos.okapi.spinner.VertexValue;

public class RR_BGRAP_InputFormat extends 
TextVertexInputFormat<IntWritable, VertexValue, EdgeValue> {
	private static final Pattern SEPARATOR = Pattern.compile("[\t ]");

	@Override
	public TextVertexReader createVertexReader(InputSplit split, TaskAttemptContext context)
			throws IOException {
		return new IntIntNullVertexReader();
	}

	public class IntIntNullVertexReader extends 
		TextVertexReaderFromEachLineProcessed<String[]> {
		/** Cached vertex id for the current line */
	    private IntWritable id;
	    
		@Override
		protected String[] preprocessLine(Text line) throws IOException {
			String[] data = SEPARATOR.split(line.toString());
		    id = new IntWritable(Integer.parseInt(data[0]));
		    return data;
		}

		@Override
		protected IntWritable getId(String[] data) throws IOException {
			return id;
		}

		@Override
		protected VertexValue getValue(String[] data) throws IOException {
			return new VertexValue();
		}
		
		@Override
	    protected Iterable<Edge<IntWritable, EdgeValue>> getEdges(
	        String[] tokens) throws IOException {
	      List<Edge<IntWritable, EdgeValue>> edges =
	          Lists.newArrayListWithCapacity(tokens.length - 1);
	      for (int n = 1; n < tokens.length; n++) {
	    		    	  
	        edges.add(EdgeFactory.create(
	            new IntWritable(Integer.parseInt(tokens[n])), new EdgeValue( (short)-1, (byte) 2, false)));
	      }
	      return edges;
	    }
	}
}
