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

public class VertexValue implements Writable {
	private short currentPartition = -1;
	private short newPartition = -1;

	private int realOutDegree;
	private int realInDegree;

	public VertexValue() {
	}

	public VertexValue(short currentPartition, short newPartition) {
		setCurrentPartition(currentPartition);
		setNewPartition(newPartition);
	}

	public short getCurrentPartition() {
		return currentPartition;
	}

	public void setCurrentPartition(short p) {
		currentPartition = p;
	}

	public short getNewPartition() {
		return newPartition;
	}

	public void setNewPartition(short p) {
		newPartition = p;
	}

	public int getRealOutDegree() {
		return realOutDegree;
	}

	public void setRealInDegree(int inDegree) {
		this.realInDegree = inDegree;
	}

	public int getRealInDegree() {
		return realInDegree;
	}

	public void setRealOutDegree(int realOutDegree) {
		this.realOutDegree = realOutDegree;
	}

	@Override
	public void readFields(DataInput in) throws IOException {
		currentPartition = in.readShort();
		newPartition = in.readShort();
	}

	@Override
	public void write(DataOutput out) throws IOException {
		out.writeShort(currentPartition);
		out.writeShort(newPartition);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		VertexValue that = (VertexValue) o;
		if (currentPartition != that.currentPartition || newPartition != that.newPartition) {
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return getCurrentPartition() + " " + getNewPartition();
	}
}
