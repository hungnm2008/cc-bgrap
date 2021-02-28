package giraph.partition.personalized;


import org.apache.giraph.partition.GraphPartitionerFactory;
import org.apache.giraph.worker.LocalData;
import org.apache.hadoop.io.Writable;

import giraph.format.personalized.BGRAP_LongWritable;
import giraph.ml.grafos.okapi.spinner.Spinner;

/**
 * PartitionerFactory Class to use the results of BGRAP and Spinner partitioner
 * For Giraph 1.2.0
 * {@link Spinner}
 *
 * @param <I> Vertex id value
 * @param <V> Vertex value
 * @param <E> Edge value
 */
public class BGRAP_GraphPartitionnerFactory<I extends BGRAP_LongWritable, V extends Writable, E extends Writable>
extends GraphPartitionerFactory<I, V, E> {

	@Override
	public void initialize(LocalData<I, V, E, ? extends Writable> localData) {
		
	}

	@Override
	public int getPartition(I id, int partitionCount, int workerCount) {
		// TODO Auto-generated method stub
		return Math.abs(id.getPartition());
	}

	@Override
	public int getWorker(int partition, int partitionCount, int workerCount) {
		// TODO Auto-generated method stub
		return partition % workerCount;
	}
}
