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
package giraph.format.personalized;

import com.google.common.collect.ImmutableList;

import org.apache.giraph.edge.Edge;
import org.apache.giraph.io.formats.TextVertexInputFormat;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.TaskAttemptContext;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Simple text-based {@link org.apache.giraph.io.VertexInputFormat} for
 * unweighted graphs without edges or values, just vertices with ids.
 *
 * Each line is just simply the vertex id.
 */
public class BGRAP_VertexIdsInputFormat
		extends TextVertexInputFormat<LongWritable, VertexValueWithPartition, NullWritable> {
	protected static final Pattern SEPARATOR = Pattern.compile("[\t ]");
	protected static final String PARTITION_DELIMITER = "_";
	
	@Override
	public TextVertexReader createVertexReader(InputSplit split, TaskAttemptContext context) throws IOException {
		return new LongPartitionNullNullVertexReader();
	}

	/**
	 * Reader for this InputFormat.
	 */
	public class LongPartitionNullNullVertexReader extends TextVertexReaderFromEachLineProcessed<String> {
		//cached id and partition
		private long id;
		private short partition=-1;

		@Override
		protected String preprocessLine(Text line) throws IOException {
			String[] tokens = line.toString().split(PARTITION_DELIMITER);
			id = Long.parseLong(tokens[0]);
			if(tokens.length==2) partition = Short.parseShort(tokens[1]);
			return line.toString();
		}

		@Override
		protected LongWritable getId(String data) throws IOException {			
			return new LongWritable(id);
		}

		@Override
		protected VertexValueWithPartition getValue(String data) throws IOException {
			return new VertexValueWithPartition(partition);
		}

		@Override
		protected Iterable<Edge<LongWritable, NullWritable>> getEdges(String data) throws IOException {
			return ImmutableList.of();
		}
	}
}
