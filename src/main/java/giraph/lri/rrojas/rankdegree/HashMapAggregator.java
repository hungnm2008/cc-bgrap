/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
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

import org.apache.hadoop.io.MapWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Writable;

import java.util.Map.Entry;

import org.apache.giraph.aggregators.BasicAggregator;


/** Aggregator for updating shared-HashMap values. */
public class HashMapAggregator extends BasicAggregator<MapWritable> {

	@Override
	public MapWritable createInitialValue() {
		return new MapWritable();
	}

	@Override
	public void aggregate(MapWritable map) {
		MapWritable temp = getAggregatedValue();
		for (Entry<Writable, Writable> entry : map.entrySet()) {
			Writable key = entry.getKey();
			if(temp.containsKey(key)) {
				((IntWritable)temp.get(key)).set(((IntWritable)entry.getValue()).get() + ((IntWritable)temp.get(key)).get());;
			} else {
				temp.put(entry.getKey(), entry.getValue());
			}
		}
		setAggregatedValue(temp);
	}

}
