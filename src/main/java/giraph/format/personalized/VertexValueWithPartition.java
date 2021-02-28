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

package giraph.format.personalized;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.apache.hadoop.io.Writable;

public class VertexValueWithPartition implements Writable {
	private short partition=-1;
	private double value;

	public VertexValueWithPartition() {
	}

	public VertexValueWithPartition(short p, double v) {
		setPartition(p);
		setVertexValue(v);
	}

	public VertexValueWithPartition(short p) {
		setPartition(p);
	}

	public VertexValueWithPartition(double v) {
		setVertexValue(v);
	}

	public short getPartition() {
		return partition;
	}

	public void setPartition(short p) {
		partition = p;
	}

	public double getVertexValue() {
		return value;
	}

	public void setVertexValue(double realOutDegree) {
		this.value = realOutDegree;
	}

	@Override
	public void readFields(DataInput in) throws IOException {
		partition = in.readShort();
		value = in.readDouble();
	}

	@Override
	public void write(DataOutput out) throws IOException {
		out.writeShort(partition);
		out.writeDouble(value);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		VertexValueWithPartition that = (VertexValueWithPartition) o;
		if (value != that.value) {
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return partition + " " + value;
	}
}
