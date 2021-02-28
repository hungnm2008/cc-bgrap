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

import it.unimi.dsi.fastutil.ints.Int2BooleanMap;
import it.unimi.dsi.fastutil.ints.Int2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ShortMap;
import it.unimi.dsi.fastutil.ints.Int2ShortOpenHashMap;

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
import org.apache.hadoop.io.IntWritable;

import giraph.lri.aelmoussawi.partitioning.LPGPartitionner;
import giraph.ml.grafos.okapi.spinner.EdgeValue;

public class ShortBooleanHashMapsEdgesInOut extends
		ConfigurableOutEdges<IntWritable, EdgeValue> implements
		StrictRandomAccessOutEdges<IntWritable, EdgeValue> {
	
	private Int2ShortMap partitionMap;
	private Int2BooleanMap isVirtualMap;
	
	private EdgeValue repValue = new EdgeValue();

	private byte w = LPGPartitionner.DEFAULT_EDGE_WEIGHT;

	@Override
	public void initialize(Iterable<Edge<IntWritable, EdgeValue>> edges) {
		EdgeIterables.initialize(this, edges);
		w = (byte) getConf().getInt(LPGPartitionner.EDGE_WEIGHT, LPGPartitionner.DEFAULT_EDGE_WEIGHT);
	}

	@Override
	public void initialize(int capacity) {
		partitionMap = new Int2ShortOpenHashMap(capacity);
		isVirtualMap = new Int2BooleanOpenHashMap(capacity);
		//to set an edge as virtual by default
		isVirtualMap.defaultReturnValue(true);
		w = (byte) getConf().getInt(LPGPartitionner.EDGE_WEIGHT, LPGPartitionner.DEFAULT_EDGE_WEIGHT);
	}

	@Override
	public void initialize() {
		partitionMap = new Int2ShortOpenHashMap();
		isVirtualMap = new Int2BooleanOpenHashMap();
		//to set an edge as virtual by default
		isVirtualMap.defaultReturnValue(true);
		w = (byte) getConf().getInt(LPGPartitionner.EDGE_WEIGHT, LPGPartitionner.DEFAULT_EDGE_WEIGHT);
	}

	@Override
	public void add(Edge<IntWritable, EdgeValue> edge) {		
		partitionMap.put(edge.getTargetVertexId().get(), 
				edge.getValue().getPartition());		
		isVirtualMap.put(edge.getTargetVertexId().get(), 
				edge.getValue().isVirtualEdge());
		//System.out.println("add :"+edge.getTargetVertexId().get() +"\t"+ edge.getValue().toString());
	}

	@Override
	public void remove(IntWritable targetVertexId) {
		partitionMap.remove(targetVertexId.get());
		isVirtualMap.remove(targetVertexId.get());
	}

	@Override
	public int size() {
		return partitionMap.size();
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public Iterator<Edge<IntWritable, EdgeValue>> iterator() {
		return (Iterator) mutableIterator();
	}
	

	public Iterator<MutableEdge<IntWritable, EdgeValue>> mutableIterator() {
		return new Iterator<MutableEdge<IntWritable, EdgeValue>>() {
			private Iterator<Entry<Integer, Short>> itPartition = partitionMap.entrySet().iterator();
			private Iterator<Entry<Integer, Boolean>> itIsVirtual = isVirtualMap.entrySet().iterator();
			
			private MutableEdge<IntWritable, EdgeValue> repEdge = EdgeFactory
					.createReusable(new IntWritable(), new EdgeValue());

			@Override
			public boolean hasNext() {
				return itPartition.hasNext() && itIsVirtual.hasNext();
			}

			@Override
			public MutableEdge<IntWritable, EdgeValue> next() {
				Entry<Integer, Short> entry = itPartition.next();				
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
			int id = in.readInt();
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
		int key;
		for (Entry<Integer, Short> e : partitionMap.entrySet()) {
			key = e.getKey();
			out.writeInt(key);
			out.writeShort(e.getValue());
			out.writeBoolean(isVirtualMap.get(key));
			//System.out.println("write : "+key+"\t"+e.getValue()+"\t"+isVirtualMap.get(key));
		}
	}

	@Override
	public EdgeValue getEdgeValue(IntWritable targetVertexId) {
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
	public void setEdgeValue(IntWritable targetVertexId, EdgeValue edgeValue) {
		partitionMap.put(targetVertexId.get(), edgeValue.getPartition());
		isVirtualMap.put(targetVertexId.get(), edgeValue.isVirtualEdge());
	}
}
