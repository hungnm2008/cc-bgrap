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

import it.unimi.dsi.fastutil.longs.Long2BooleanMap;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ShortMap;
import it.unimi.dsi.fastutil.longs.Long2ShortOpenHashMap;

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

import giraph.lri.aelmoussawi.partitioning.LPGPartitionner;

public class ShortBooleanHashMapsEdgesInOut extends
		ConfigurableOutEdges<LongWritable, EdgeValue> implements
		StrictRandomAccessOutEdges<LongWritable, EdgeValue> {
	
	private Long2ShortMap partitionMap;
	private Long2BooleanMap isVirtualMap;
	
	private EdgeValue repValue = new EdgeValue();

	private byte w = LPGPartitionner.DEFAULT_EDGE_WEIGHT;

	@Override
	public void initialize(Iterable<Edge<LongWritable, EdgeValue>> edges) {
		EdgeIterables.initialize(this, edges);
		w = (byte) getConf().getInt(LPGPartitionner.EDGE_WEIGHT, LPGPartitionner.DEFAULT_EDGE_WEIGHT);
	}

	@Override
	public void initialize(int capacity) {
		partitionMap = new Long2ShortOpenHashMap(capacity);
		isVirtualMap = new Long2BooleanOpenHashMap(capacity);
		//to set an edge as virtual by default
		isVirtualMap.defaultReturnValue(true);
		w = (byte) getConf().getInt(LPGPartitionner.EDGE_WEIGHT, LPGPartitionner.DEFAULT_EDGE_WEIGHT);
	}

	@Override
	public void initialize() {
		partitionMap = new Long2ShortOpenHashMap();
		isVirtualMap = new Long2BooleanOpenHashMap();
		//to set an edge as virtual by default
		isVirtualMap.defaultReturnValue(true);
		w = (byte) getConf().getInt(LPGPartitionner.EDGE_WEIGHT, LPGPartitionner.DEFAULT_EDGE_WEIGHT);
	}

	@Override
	public void add(Edge<LongWritable, EdgeValue> edge) {		
		partitionMap.put(edge.getTargetVertexId().get(), 
				edge.getValue().getPartition());		
		isVirtualMap.put(edge.getTargetVertexId().get(), 
				edge.getValue().isVirtualEdge());
		//System.out.println("add :"+edge.getTargetVertexId().get() +"\t"+ edge.getValue().toString());
	}

	@Override
	public void remove(LongWritable targetVertexId) {
		partitionMap.remove(targetVertexId.get());
		isVirtualMap.remove(targetVertexId.get());
	}

	@Override
	public int size() {
		return partitionMap.size();
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public Iterator<Edge<LongWritable, EdgeValue>> iterator() {
		return (Iterator) mutableIterator();
	}
	

	public Iterator<MutableEdge<LongWritable, EdgeValue>> mutableIterator() {
		return new Iterator<MutableEdge<LongWritable, EdgeValue>>() {
			private Iterator<Entry<Long, Short>> itPartition = partitionMap.entrySet().iterator();
			private Iterator<Entry<Long, Boolean>> itIsVirtual = isVirtualMap.entrySet().iterator();
			
			private MutableEdge<LongWritable, EdgeValue> repEdge = EdgeFactory
					.createReusable(new LongWritable(), new EdgeValue());

			@Override
			public boolean hasNext() {
				return itPartition.hasNext() && itIsVirtual.hasNext();
			}

			@Override
			public MutableEdge<LongWritable, EdgeValue> next() {
				Entry<Long, Short> entry = itPartition.next();				
				repEdge.getTargetVertexId().set(entry.getKey());				
				repEdge.getValue().setPartition(entry.getValue());
				
				itIsVirtual.next();
				boolean isVirtual = isVirtualMap.get(entry.getKey());
				repEdge.getValue().setVirtualEdge(isVirtual);
				
				if(isVirtual) {//virtual edge
					repValue.setWeight( LPGPartitionner.DEFAULT_EDGE_WEIGHT );
				} else {//virtual edge
					repEdge.getValue().setWeight( w );
				}
				//System.out.println("iterate :"+repEdge.getTargetVertexId()+"\t"+isVirtual);
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
			short v = in.readShort();
			partitionMap.put(id, v);
			
			boolean v2 = in.readBoolean();
			isVirtualMap.put(id, v2);
			
			//System.out.println("read : "+id+"\t"+v+"\t"+v2);
		}
	}

	@Override
	public void write(final DataOutput out) throws IOException {
		out.writeInt(partitionMap.size());
		long key;
		for (Entry<Long, Short> e : partitionMap.entrySet()) {
			key = e.getKey();
			out.writeLong(key);
			out.writeShort(e.getValue());
			out.writeBoolean(isVirtualMap.get(key));
			//System.out.println("write : "+key+"\t"+e.getValue()+"\t"+isVirtualMap.get(key));
		}
	}

	@Override
	public EdgeValue getEdgeValue(LongWritable targetVertexId) {
		short partition = partitionMap.get(targetVertexId.get());
		boolean virtualEdge = isVirtualMap.get(targetVertexId.get());
		
		
		repValue.setPartition(partition);
		repValue.setVirtualEdge(virtualEdge);
		if(virtualEdge) {//virtual edge
			repValue.setWeight( LPGPartitionner.DEFAULT_EDGE_WEIGHT );
		}
		else {//real edge
			repValue.setWeight( w );
		}
		return repValue;
	}

	@Override
	public void setEdgeValue(LongWritable targetVertexId, EdgeValue edgeValue) {
		partitionMap.put(targetVertexId.get(), edgeValue.getPartition());
		isVirtualMap.put(targetVertexId.get(), edgeValue.isVirtualEdge());
	}
}
