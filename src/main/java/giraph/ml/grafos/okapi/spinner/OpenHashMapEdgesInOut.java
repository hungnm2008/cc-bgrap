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

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map.Entry;
import org.apache.giraph.edge.ConfigurableOutEdges;
import org.apache.giraph.edge.Edge;
import org.apache.giraph.edge.EdgeFactory;
import org.apache.giraph.edge.MutableEdge;
import org.apache.giraph.edge.StrictRandomAccessOutEdges;
import org.apache.giraph.utils.EdgeIterables;
import org.apache.hadoop.io.LongWritable;


public class OpenHashMapEdgesInOut extends
		ConfigurableOutEdges<LongWritable, EdgeValue> implements
		StrictRandomAccessOutEdges<LongWritable, EdgeValue> {
	
	private Long2ObjectMap<String> map;
	private EdgeValue repValue = new EdgeValue();
	/** Specify the output delimiter */
	public static final String LINE_TOKENIZE_VALUE = "openhashmap.edges.delimiter";
	/** Default output delimiter */
	public static final String LINE_TOKENIZE_VALUE_DEFAULT = "\t";
	
	public String delimiter = "\t";

	@Override
	public void initialize(Iterable<Edge<LongWritable, EdgeValue>> edges) {
		EdgeIterables.initialize(this, edges);
		delimiter = getConf().get(LINE_TOKENIZE_VALUE, LINE_TOKENIZE_VALUE_DEFAULT);
	}

	@Override
	public void initialize(int capacity) {
		map = new Long2ObjectOpenHashMap<String>(capacity);
		delimiter = getConf().get(LINE_TOKENIZE_VALUE, LINE_TOKENIZE_VALUE_DEFAULT);
	}

	@Override
	public void initialize() {
		map = new Long2ObjectOpenHashMap<String>();
		delimiter = getConf().get(LINE_TOKENIZE_VALUE, LINE_TOKENIZE_VALUE_DEFAULT);
	}

	@Override
	public void add(Edge<LongWritable, EdgeValue> edge) {
		
		map.put(edge.getTargetVertexId().get(), new String (
				edge.getValue().getPartition() + delimiter
				+ edge.getValue().getWeight() + delimiter
				+ edge.getValue().isVirtualEdge()
				)
		);
	}

	@Override
	public void remove(LongWritable targetVertexId) {
		map.remove(targetVertexId.get());
	}

	@Override
	public int size() {
		return map.size();
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public Iterator<Edge<LongWritable, EdgeValue>> iterator() {
		return (Iterator) mutableIterator();
	}

	public Iterator<MutableEdge<LongWritable, EdgeValue>> mutableIterator() {
		return new Iterator<MutableEdge<LongWritable, EdgeValue>>() {
			private Iterator<Entry<Long, String>> it = map.entrySet().iterator();
			private MutableEdge<LongWritable, EdgeValue> repEdge = EdgeFactory
					.createReusable(new LongWritable(), new EdgeValue());

			@Override
			public boolean hasNext() {
				return it.hasNext();
			}

			@Override
			public MutableEdge<LongWritable, EdgeValue> next() {
				Entry<Long, String> entry = it.next();
				repEdge.getTargetVertexId().set(entry.getKey());
				String[] tokens = entry.getValue().split(delimiter);
				repEdge.getValue().setPartition(Short.parseShort(tokens[0]));
				repEdge.getValue().setWeight(Byte.parseByte(tokens[1]));
				repEdge.getValue().setVirtualEdge(Boolean.parseBoolean(tokens[2]));
				return repEdge;
			}

			@Override
			public void remove() {
			}
		};
	}

	@Override
	public void readFields(DataInput in) throws IOException {
		int numEdges = in.readInt();
		initialize(numEdges);
		for (int i = 0; i < numEdges; i++) {
			long id = in.readLong();
			String v = in.readUTF();
			map.put(id, v);
		}
	}

	@Override
	public void write(final DataOutput out) throws IOException {
		out.writeInt(map.size());
		for (Entry<Long, String> e : map.entrySet()) {
			out.writeLong(e.getKey());
			out.writeUTF(e.getValue());
		}
	}

	@Override
	public EdgeValue getEdgeValue(LongWritable targetVertexId) {
		String v = map.get(targetVertexId.get());
		if(v==null) return null;
		
		String[] tokens = v.split(delimiter);
		repValue.setPartition(Short.parseShort(tokens[0]));
		repValue.setWeight(Byte.parseByte(tokens[1]));
		repValue.setVirtualEdge(Boolean.parseBoolean(tokens[2]));
		return repValue;
	}

	@Override
	public void setEdgeValue(LongWritable targetVertexId, EdgeValue edgeValue) {
		map.put(targetVertexId.get(), new String (
				edgeValue.getPartition() + delimiter
				+ edgeValue.getWeight() + delimiter
				+ edgeValue.isVirtualEdge()
				));
	}
}
