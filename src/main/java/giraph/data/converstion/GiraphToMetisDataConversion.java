package giraph.data.converstion;

import org.apache.giraph.conf.GiraphConfiguration;
import org.apache.giraph.io.formats.*;
import org.apache.giraph.job.GiraphJob;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.util.ToolRunner;
import org.apache.log4j.Logger;
import giraph.format.personalized.VertexNeighboorListOutputFormat;
import giraph.ml.grafos.okapi.spinner.Spinner;
import giraph.ml.grafos.okapi.spinner.SpinnerEdgeInputFormat;
import java.io.IOException;

/**
 * implemented with Giraph 1.2.0 and Hadoop 1.2.1
 * 
 * @author adnan
 *
 */
public class GiraphToMetisDataConversion extends PersonalizedGiraphJob {

	//private String hdfsNode = "hdfs://129.175.25.75:50001";
	private String giraphVertexInputRep = getHdfsNode()+"/giraph_data/input/vertex_format";
	private String giraphEdgeInputRep = getHdfsNode()+"/giraph_data/input/edge_format";

	public static void main(String[] args) throws Exception {
		/*
		 * SimpleSuperstepComputation
		 * 
		 */

		args = new String[] { "WikiTalk.vertex"
				// "soc-LiveJournal1.vertex"
		};
		ToolRunner.run(new GiraphToMetisDataConversion(), args);
	}

	Logger logger;
	int nbWorker=7;
	
	static String[] data = new String[] { 
			"WikiTalk", 
			"BerkeleyStan",	
			"Flixster", 
			"DelaunaySC",	
			"LiveJournal", 
			"Pokec",
			"Orkut", 
			"Graph500",
//			"Twitter"
	};

	@Override
	public int run(String[] arg0) throws Exception {
		
		String filename = "FriendSter";
		configureHDFS();

		/**/
		GiraphConfiguration giraphConf = VertexDataTest(filename);
		String jobName = "conversation - " + filename;
		String outputPath = giraphEdgeInputRep + "/converted/" + filename;

		if (getHadoopDataFileSystem().exists(new Path(outputPath))) {
			getHadoopDataFileSystem().delete(new Path(outputPath), true);
		}
		

		giraphConf.setLocalTestMode(false);
		giraphConf.setCheckpointFrequency(10);
		giraphConf.setWorkerConfiguration(1, nbWorker, 100);
		GiraphConfiguration.SPLIT_MASTER_WORKER.set(giraphConf, false);

		GiraphJob giraphJob = new GiraphJob(giraphConf, jobName);
		GiraphTextOutputFormat.setOutputPath(giraphJob.getInternalJob(), new Path(outputPath));
		
		giraphJob.run(true);
		
		
		Path newFilePath = new Path("/users/lahdak/adnanmoe/Bureau/Data/" + filename + ".vertex");
		Runtime rt = Runtime.getRuntime();
		rt.exec("hadoop fs -getmerge " + outputPath+" "+newFilePath);
		return 0;

	}

	@SuppressWarnings({ })
	protected GiraphConfiguration VertexDataTest(String filename) throws IOException {
		String inputPath = giraphVertexInputRep + "/" + filename ;//+ "/"+filename+"_split00.edges";

		GiraphConfiguration giraphConf = new GiraphConfiguration(getConf());
		giraphConf.setMasterComputeClass(Spinner.PartitionerMasterCompute.class);
		giraphConf.setComputationClass(Spinner.ConverterPropagate.class);
		//giraphConf.setComputationClass(org.apache.giraph.examples.SimpleShortestPathsComputation.class);
		giraphConf.setEdgeInputFormatClass(SpinnerEdgeInputFormat.class);
		giraphConf.setVertexOutputFormatClass(VertexNeighboorListOutputFormat.class);
		giraphConf.setInt("spinner.maxIterations", 0);
		
		

		InMemoryVertexOutputFormat.initializeOutputGraph(giraphConf);
		GiraphFileInputFormat.addEdgeInputPath(giraphConf, new Path(inputPath));

		return giraphConf;

	}
}