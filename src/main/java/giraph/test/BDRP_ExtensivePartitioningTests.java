package giraph.test;

import giraph.format.personalized.BGRAPVertexInputFormat;
import giraph.format.personalized.BGRAP_IdWithPartitionOuputFormat;
import giraph.lri.aelmoussawi.partitioning.BGRAP_eb;
import giraph.lri.aelmoussawi.partitioning.BGRAP_vb;
import giraph.lri.aelmoussawi.partitioning.LPGPartitionner;
import giraph.ml.grafos.okapi.spinner.EdgeValue;
import giraph.ml.grafos.okapi.spinner.ShortBooleanHashMapsEdgesInOut;
import giraph.ml.grafos.okapi.spinner.VertexValue;
import org.apache.giraph.conf.GiraphConfiguration;
import org.apache.giraph.io.formats.GiraphFileInputFormat;
import org.apache.giraph.io.formats.InMemoryVertexOutputFormat;
import org.apache.giraph.job.GiraphJob;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.ToolRunner;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * implemented with Giraph 1.2.0 and Hadoop 1.2.1
 * 
 * This class allow to Run iteratively several partitioning algorithm on a given graph.
 * For each algorithm, we variate the number of desired partitions, then for each value 
 * we run 10 execution and for each we export the partition to the local disk
 * 
 * @author adnan
 *
 */
@SuppressWarnings("unused")
public class BDRP_ExtensivePartitioningTests extends PersonalizedGiraphJob {
	private String giraphOutputRep = getHdfsNode() + "/bdrp/data",
			// giraphVertexInputRep = hdfsNode+"/giraph_data/input/VertexFormat", // doesn't
			// work : Giraph bug 904 : https://issues.apache.org/jira/browse/GIRAPH-904
			giraphVertexInputRep = getHdfsNode() + "/bdrp/data";
	// giraphEdgeInputRep = hdfsNode+"/giraph_data/input/edge_format";

	public static void main(String[] args) throws Exception {
		ToolRunner.run(new BDRP_ExtensivePartitioningTests(), data);
	}
	
	/* A loal folder to save the results of partitioning */
	String hdfsOutputDirectory = "",
			localOutputDirectory = "C:\\Users\\Julio\\Documents\\MasterDegree\\BDMA\\Classes\\CentraleSupelec\\BDRP\\Project\\Code";
			
	/* the number of worker (the optimal configuration is to set it to the total number of cores on the used cluster) */
	private int nbWorker=64,
			k;
	
	boolean done=false;
	
	/* the weight of the penality term in the objectif function of the BGRAP partitionner */
	private float kappa;
	
	/* The partitionner to use */
	String[] algo=new String[] {
			"BGRAP_eb",
			"BGRAP_vb",	
	};
	
	/* The name of data sets in the @giraphVertexInputRep folder on the HDFS system */
	static String[] data = new String[] {
			"WikiTalk",
//			"BerkeleyStan",
//			"Flixster",
//			"DelaunaySC",
//			"Pokec",
//			"LiveJournal",
//			"Orkut",
//			"Graph500",
//			"Twitter"
			};
	
	
	@Override
	/**
	 * set the configuration of Giraph job
	 * @param argDataNames A list of data sets in the @giraphVertexInputRep folder on the HDFS system.
	 * @return
	 * @throws IOException
	 */
	public int run(String[] argDataNames) throws Exception {
		String filename;
		if(argDataNames.length==0)
			argDataNames=data.clone();
		
					
		
		configureHDFS();
		
		int[] nbPart = new int[] { 2, 4, 8, 12, 16 , 20, 24, 28, 32};
		int j = 0, d=0, startIndex=0, maxIndex=nbPart.length-1;
		int maxRun=10, threshold=40;
		nbWorker=8;
		kappa=0.5f;
		
		// A loop for the datasets
		nextDataSet :
		// A loop for the partitioning algorithms
		nextAlgorithm :
		// Variate the number of partitions
		for(int index = startIndex; index <= maxIndex; index++) {
			k = nbPart[index];
			boolean flag = false;
			filename = argDataNames[d];
			System.out.println(filename);
			System.out.println(algo[j]);
			System.out.println("k: "+k);
			// Run @maxRun executions
			for (int run=1; run<=maxRun; run++) {
				
				/**
				 * An example of Giraph Job execution
				 */
				System.out.print("run: "+run);
				
				GiraphJob partitioningJob = new GiraphJob(initConfig(filename, algo[j]), algo[j]+";" + filename);
				
				FileOutputFormat.setOutputPath(partitioningJob.getInternalJob(), new Path(hdfsOutputDirectory));
				useAcceleratedSetting(partitioningJob.getConfiguration());

				partitioningJob.getConfiguration().setInt(LPGPartitionner.NUM_PARTITIONS, k);
				
				// export statistics on the partition quality to local file (on the cluster)
				partitioningJob.getConfiguration().setBoolean(LPGPartitionner.SAVE_STATS, true);
				
				partitioningJob.getConfiguration().setBoolean(LPGPartitionner.GRAPH_DIRECTED, true);
				partitioningJob.getConfiguration().setFloat(LPGPartitionner.KAPPA, kappa);
				partitioningJob.getConfiguration().setBoolean(LPGPartitionner.COMPUTE_OUTDEGREE_THRESHOLD, true);
				
				useAcceleratedSetting(partitioningJob.getConfiguration());
				
				flag=partitioningJob.run(true);
				boolean flag2 = false;
				
				// if the job finish correctly 
				if (flag) {
					// save the partition to the local file system
					String localOutputFileName = (localOutputDirectory+"/k="+k+"/" +"/run="+run+ "/" + filename + "."+algo[j]+".partition");
					File f = new File(localOutputFileName);
					
					if (!f.exists() || f.delete() ) {
						Path localOutputFilePath = new Path(localOutputFileName);
						
						flag2 = FileUtil.copyMerge(getHadoopDataFileSystem(), new Path(hdfsOutputDirectory+"/"), 
								FileSystem.getLocal(getConf()), localOutputFilePath, 
								false, getConf(), "");	
					}
					
					// put the process to sleep few seconds to allow the Reducer to download the results on the local file system and clean up the current job
					if(flag2) {
						if(filename.equals("Twitter") || filename.equals("Graph500") || filename.equals("Orkut") || filename.equals("sk-2005")) {
							//10 sec until mapred clean
							System.out.print("...");
							TimeUnit.SECONDS.sleep(15);
							System.out.println("finish");
						}					
						else {
							System.out.print("...");
							TimeUnit.SECONDS.sleep(2);
							System.out.println("finish");
						}
					}
					
					
				}
				else return 0;
			}// 10 runs finished
			
			
			//go to the next dataset and restart loops on the partitionner, the number of partitions and number of executions
			if(k==32) {
				d++;
				if(d == argDataNames.length) {
					j++;
					if(j<algo.length) {
						k=0;
						d=0;
						continue nextAlgorithm;
					}
					else
						return 0;
				}
				else {
					k=0;
					continue nextAlgorithm;//nextDataSet;
				}
			}
			
			//go to the next partitionner and restart loops on number of partitions and number of executions
			if(index==maxIndex) {
				j++;
				if(j == algo.length) {
					d++;
					if(d<argDataNames.length) {
						index=startIndex;
						j=0;
						continue nextAlgorithm;
					}
					else
						return 0;
				}
				else {
					index=startIndex;
					continue nextAlgorithm;
				}
			}
		}
		return 0;

	}

	/**
	 * set the configuration of Giraph job
	 * @param filename The name on the file on the HDSF system
	 * @param algo The name of the partitioning algorithm to run
	 * @return
	 * @throws IOException
	 */
	protected GiraphConfiguration initConfig(String filename, String algo) throws IOException {
		
		GiraphConfiguration giraphConf = new GiraphConfiguration(getConf());

		
		giraphConf.setLocalTestMode(false);
		// giraphConf.setCheckpointFrequency(10);
		giraphConf.setWorkerConfiguration(1, nbWorker, 100);
		GiraphConfiguration.SPLIT_MASTER_WORKER.set(giraphConf, false);
	
		String inputPath = giraphVertexInputRep + "/" + filename;
		
		if ( getHadoopDataFileSystem().exists(new Path(inputPath+".vertex")) ) {
			inputPath += ".vertex";
			GiraphFileInputFormat.addVertexInputPath(giraphConf, new Path(inputPath));
		} else {
			GiraphFileInputFormat.addVertexInputPath(giraphConf, new Path(inputPath));
		}
	
		GiraphConfiguration.VERTEX_INPUT_FORMAT_CLASS.set(giraphConf, BGRAPVertexInputFormat.class);
		GiraphConfiguration.VERTEX_OUTPUT_FORMAT_CLASS.set(giraphConf, BGRAP_IdWithPartitionOuputFormat.class);
		/**
		 * algorithm parameter
		 */
		giraphConf.setInt(LPGPartitionner.EDGE_WEIGHT, 2);
	
		GiraphConfiguration.VERTEX_VALUE_CLASS.set(giraphConf, VertexValue.class);
		GiraphConfiguration.EDGE_VALUE_CLASS.set(giraphConf, EdgeValue.class);
		// this class automatically create missed undirected edges and cause the lose of bi-direction information
		//GiraphConfiguration.VERTEX_EDGES_CLASS.set(giraphConf, OpenHashMap.class);
		GiraphConfiguration.VERTEX_EDGES_CLASS.set(giraphConf, ShortBooleanHashMapsEdgesInOut.class);
		// must used in this way
		GiraphConfiguration.INPUT_VERTEX_EDGES_CLASS.set(giraphConf, ShortBooleanHashMapsEdgesInOut.class);
	
		InMemoryVertexOutputFormat.initializeOutputGraph(giraphConf);
		
		hdfsOutputDirectory = getHdfsNode() + "/bdrp/data"+filename;
		
		if (getHadoopDataFileSystem().exists(new Path(hdfsOutputDirectory))) {
			getHadoopDataFileSystem().delete(new Path(hdfsOutputDirectory), true);
		}
				
		
		if(algo.equals("BGRAP_eb")) {
			GiraphConfiguration.MASTER_COMPUTE_CLASS.set(giraphConf, BGRAP_eb.PartitionerMasterCompute.class);
			GiraphConfiguration.COMPUTATION_CLASS.set(giraphConf, BGRAP_eb.ConverterPropagate.class);
		} else if(algo.equals("BGRAP_vb")) {
			GiraphConfiguration.MASTER_COMPUTE_CLASS.set(giraphConf, BGRAP_vb.PartitionerMasterCompute.class);
			GiraphConfiguration.COMPUTATION_CLASS.set(giraphConf, BGRAP_vb.ConverterPropagate.class);
		}
	
		return giraphConf;
	}
	
}
