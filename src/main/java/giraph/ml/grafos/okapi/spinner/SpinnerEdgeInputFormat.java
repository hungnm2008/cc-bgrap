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

package giraph.ml.grafos.okapi.spinner;

import java.io.IOException;
import java.util.regex.Pattern;

import org.apache.giraph.io.EdgeReader;
import org.apache.giraph.io.formats.TextEdgeInputFormat;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.TaskAttemptContext;

public class SpinnerEdgeInputFormat extends TextEdgeInputFormat<LongWritable, EdgeValue> {
	/** Splitter for endpoints */
	private static final Pattern SEPARATOR = Pattern.compile("[\001\t ]");
	// private static final Pattern SEPARATOR = Pattern.compile("[\t ]");

	@Override
	public EdgeReader<LongWritable, EdgeValue> createEdgeReader(InputSplit split, TaskAttemptContext context)
			throws IOException {
		return new SpinnerEdgeReader();
	}

	public class SpinnerEdgeReader extends TextEdgeReaderFromEachLineProcessed<String[]> {
		@Override
		protected String[] preprocessLine(Text line) throws IOException {
			return SEPARATOR.split(line.toString());
		}

		@Override
		protected LongWritable getSourceVertexId(String[] endpoints) throws IOException {
			return new LongWritable(Long.parseLong(endpoints[0]));
		}

		@Override
		protected LongWritable getTargetVertexId(String[] endpoints) throws IOException {
			return new LongWritable(Long.parseLong(endpoints[1]));
		}

		@Override
		protected EdgeValue getValue(String[] endpoints) throws IOException {
			EdgeValue value = new EdgeValue();
			if (endpoints.length == 3) {
				value.setWeight((byte) Byte.parseByte(endpoints[2]));
			}
			return value;
		}
	}
}
