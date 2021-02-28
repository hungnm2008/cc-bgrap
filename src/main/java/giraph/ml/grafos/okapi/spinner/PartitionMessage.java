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

public class PartitionMessage implements Writable {
	private long sourceId;
	private short partition;

	public PartitionMessage() {
	}

	public PartitionMessage(long sourceId, short partition) {
		this.sourceId = sourceId;
		this.partition = partition;
	}

	public long getSourceId() {
		return sourceId;
	}

	public void setSourceId(long sourceId) {
		this.sourceId = sourceId;
	}

	public short getPartition() {
		return partition;
	}

	public void setPartition(short partition) {
		this.partition = partition;
	}

	@Override
	public void readFields(DataInput input) throws IOException {
		sourceId = input.readLong();
		partition = input.readShort();
	}

	@Override
	public void write(DataOutput output) throws IOException {
		output.writeLong(sourceId);
		output.writeShort(partition);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		PartitionMessage that = (PartitionMessage) o;
		if (partition != that.partition || sourceId != that.sourceId) {
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return getSourceId() + " " + getPartition();
	}
}
