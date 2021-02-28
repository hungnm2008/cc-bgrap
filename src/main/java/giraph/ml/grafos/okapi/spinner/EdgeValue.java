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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.apache.hadoop.io.Writable;


public class EdgeValue implements Writable {
	private short partition = -1;
	private byte weight = 1;
	private boolean virtualEdge=true;

	public EdgeValue() {
	}

	public EdgeValue(EdgeValue o) {
		this(o.getPartition(), o.getWeight(), o.isVirtualEdge());
	}

	public EdgeValue(short partition, byte weight) {
		setPartition(partition);
		setWeight(weight);
	}

	public EdgeValue(short partition, byte weight, boolean virtualEdge) {
		setPartition(partition);
		setWeight(weight);
		setVirtualEdge(virtualEdge);
	}

	public short getPartition() {
		return partition;
	}

	public void setPartition(short partition) {
		this.partition = partition;
	}

	public byte getWeight() {
		return weight;
	}

	public void setWeight(byte weight) {
		this.weight = weight;
	}

	public boolean isVirtualEdge() {
		return virtualEdge;
	}

	public void setVirtualEdge(boolean virtualEdge) {
		this.virtualEdge = virtualEdge;
	}

	@Override
	public void readFields(DataInput in) throws IOException {
		partition = in.readShort();
		weight = in.readByte();
		virtualEdge = in.readBoolean();
	}

	@Override
	public void write(DataOutput out) throws IOException {
		out.writeShort(partition);
		out.writeByte(weight);
		out.writeBoolean(virtualEdge);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		EdgeValue that = (EdgeValue) o;
		return this.partition == that.partition;
	}

	@Override
	public String toString() {
		return getWeight() + " " + getPartition()+ " " +isVirtualEdge();
	}

	public short[] toShortPartitionVirtualArray() {
		// TODO Auto-generated method stub
		return new short[] {(short) (isVirtualEdge()?1:0), 
				getPartition()};
	}

}
