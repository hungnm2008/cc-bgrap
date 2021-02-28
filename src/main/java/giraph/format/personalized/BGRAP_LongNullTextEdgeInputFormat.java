package giraph.format.personalized;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.giraph.io.EdgeReader;
import org.apache.giraph.io.formats.TextEdgeInputFormat;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.TaskAttemptContext;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Simple text-based {@link org.apache.giraph.io.EdgeInputFormat} for unweighted
 * graphs with int ids.
 *
 * Each line consists of: source_vertex, target_vertex
 */
public class BGRAP_LongNullTextEdgeInputFormat extends TextEdgeInputFormat<BGRAP_LongWritable, NullWritable> {
	/** Splitter for endpoints */
	// private static final Pattern SEPARATOR = Pattern.compile("[\t ]");
	private static final Pattern SEPARATOR = Pattern.compile("\t");

	@Override
	public EdgeReader<BGRAP_LongWritable, NullWritable> createEdgeReader(InputSplit split, TaskAttemptContext context)
			throws IOException {
		return new LongNullTextEdgeReader();
	}

	/**
	 * {@link org.apache.giraph.io.EdgeReader} associated with
	 * {@link IntNullTextEdgeInputFormat}.
	 */
	public class LongNullTextEdgeReader extends TextEdgeReaderFromEachLineProcessed<String[]> {
		@Override
		protected String[] preprocessLine(Text line) throws IOException {
			//System.out.println(line.toString());
			return SEPARATOR.split(line.toString());
		}

		@Override
		protected BGRAP_LongWritable getSourceVertexId(String[] tokens) throws IOException {
			return new BGRAP_LongWritable(Long.parseLong(tokens[0]));
		}

		@Override
		protected BGRAP_LongWritable getTargetVertexId(String[] tokens) throws IOException {
			return new BGRAP_LongWritable(Long.parseLong(tokens[1]));
		}

		@Override
		protected NullWritable getValue(String[] tokens) throws IOException {
			return NullWritable.get();
		}
	}
}
