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

import org.apache.hadoop.io.WritableComparable;

@SuppressWarnings("rawtypes")
public class BGRAP_LongWritable implements WritableComparable {
	public static final String DELIMITER = "_";
	private short partition=-1;
	private long id;

	public BGRAP_LongWritable() {
	}
	
	public BGRAP_LongWritable(long id) {
		setId(id);
	}
	
	public BGRAP_LongWritable(BGRAP_LongWritable o) {
		setId(o.getId());
		setPartition(o.getPartition());
	}
	
	public BGRAP_LongWritable(long id, short partition) {
		setId(id);
		setPartition(partition);
	}
	
	public BGRAP_LongWritable(String id) {
		String[] tokens = id.split(DELIMITER);
		setId(Long.parseLong(tokens[0]));
		if(tokens.length>1) setPartition(Short.parseShort(tokens[1]));
	}
	
	public BGRAP_LongWritable(String id, String partition) {
		setId(Long.parseLong(id));
		setPartition(Short.parseShort(partition));
	}

	@Override
	public void readFields(DataInput in) throws IOException {
		setId(in.readLong());
		setPartition(in.readShort());
	}

	@Override
	public void write(DataOutput out) throws IOException {
		out.writeLong(id);
		out.writeShort(partition);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		BGRAP_LongWritable other = (BGRAP_LongWritable) o;
		
		
		if (getPartition() == other.partition && getId() == other.id) {
			// when reading vertex partition and vertex edge from two separate files
			// two identical IDs while while be considered no equals because the vertex
			// partition in the edge file will be set to -1   
			return true;
		}
		/*
		if (this.getId() == other.getId()) {
			return true;
		}*/
		return false;
	}

	@Override
	public String toString() {
		return this.getId() + DELIMITER + getPartition();
	}

	@Override
	public int hashCode() {
		return (int) id;
	}

	public short getPartition() {
		return partition;
	}

	public void setPartition(short p) {
		this.partition=p;
	}

	public long getId() {
		return this.id;
	}

	public void setId(long id) {
		this.id = id;
	}

	@Override
	public int compareTo(Object o) {
		if (o == this) {
			return 0;
		}
		BGRAP_LongWritable other = (BGRAP_LongWritable) o;
		return this.getId() > other.getId() ? +1 : this.getId() < other.getId() ? -1 : 0;
	}
}
