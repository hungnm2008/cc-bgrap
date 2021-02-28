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

import org.apache.giraph.io.formats.TextVertexValueInputFormat;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.TaskAttemptContext;

public class SpinnerVertexValueInputFormat extends TextVertexValueInputFormat<LongWritable, VertexValue, EdgeValue> {
	private static final Pattern SEPARATOR = Pattern.compile("[\001\t ]");

	@Override
	public LongShortTextVertexValueReader createVertexValueReader(InputSplit split, TaskAttemptContext context)
			throws IOException {
		return new LongShortTextVertexValueReader();
	}

	public class LongShortTextVertexValueReader extends TextVertexValueReaderFromEachLineProcessed<String[]> {

		@Override
		protected String[] preprocessLine(Text line) throws IOException {
			return SEPARATOR.split(line.toString());
		}

		@Override
		protected LongWritable getId(String[] data) throws IOException {
			return new LongWritable(Long.parseLong(data[0]));
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
