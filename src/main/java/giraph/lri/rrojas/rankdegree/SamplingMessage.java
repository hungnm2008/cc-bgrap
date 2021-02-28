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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;

import giraph.ml.grafos.okapi.common.data.LongArrayListWritable;
import giraph.ml.grafos.okapi.common.data.MessageWrapper;
import org.apache.giraph.utils.ArrayListWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Writable;



public class SamplingMessage implements Writable {
	private int sourceId;
	private int partition;
	// JC: Add friendList as message
	private ArrayList<IntWritable> friendlist;

	public static class LongIdFriendsList extends MessageWrapper<IntWritable,
			LongArrayListWritable> {
		@Override
		public Class<IntWritable> getVertexIdClass() {
			return IntWritable.class;
		}
		@Override
		public Class<LongArrayListWritable> getMessageClass() {
			return LongArrayListWritable.class;
		}
	}

	public SamplingMessage() {
		this.sourceId = -1;
		this.partition = -1;
		this.friendlist = new ArrayList<IntWritable>();
	}

	public SamplingMessage(int sourceId, int partition) {
		this.sourceId = sourceId;
		this.partition = partition;
		this.friendlist = new ArrayList<IntWritable>();
	}

	public SamplingMessage(int sourceId, int numfriends, ArrayList<IntWritable> friendlist) {
		this.sourceId = sourceId;
		this.partition = numfriends;
		this.friendlist = friendlist;
	}



	public int getSourceId() {
		return sourceId;
	}

	public void setSourceId(int sourceId) {
		this.sourceId = sourceId;
	}

	public int getPartition() {
		return partition;
	}

	public void setPartition(int partition) {
		this.partition = partition;
	}


	public ArrayList<IntWritable> getFriendlist() {
		return friendlist;
	}

	public void setFriendlist(ArrayList<IntWritable> friendlist) {
		this.friendlist = friendlist;
	}

	@Override
	public void readFields(DataInput input) throws IOException {
		sourceId = input.readInt();
		partition = input.readInt();

		//Read FriendList
		friendlist.clear();
	    int length = input.readInt();
	    for (int x = 0; x < length; x++) {
	        IntWritable tmp = new IntWritable();
	        tmp.readFields(input);
	        friendlist.add(tmp);
	    }
	}

	@Override
	public void write(DataOutput output) throws IOException {
		output.writeInt(sourceId);
		output.writeInt(partition);

		// Write FriendList
		output.writeInt(friendlist.size());
	    for (IntWritable tmp : friendlist) {
	        tmp.write(output);
	    }
	}


	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		SamplingMessage that = (SamplingMessage) o;
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