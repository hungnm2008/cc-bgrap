package giraph.lri.nguyen_candela.test;

import giraph.format.personalized.BGRAPVertexInputFormat;
import giraph.format.personalized.BGRAP_IdWithPartitionOuputFormat;
import giraph.lri.rrojas.rankdegree.BGRAP_eb;
import giraph.lri.rrojas.rankdegree.BGRAP_vb;
import giraph.lri.rrojas.rankdegree.LPGPartitionner;
import giraph.ml.grafos.okapi.spinner.EdgeValue;
import giraph.ml.grafos.okapi.spinner.ShortBooleanHashMapsEdgesInOut;
import giraph.ml.grafos.okapi.spinner.VertexValue;
import giraph.test.BDRP_ExtensivePartitioningTests;
import giraph.test.PersonalizedGiraphJob;
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

public class Test_EB extends PersonalizedGiraphJob {
    private String giraphOutputRep = getHdfsNode() + "/bdrp/output",
    // giraphVertexInputRep = hdfsNode+"/giraph_data/input/VertexFormat", // doesn't
    // work : Giraph bug 904 : https://issues.apache.org/jira/browse/GIRAPH-904
    giraphVertexInputRep = getHdfsNode() + "/bdrp/data";
    // giraphEdgeInputRep = hdfsNode+"/giraph_data/input/edge_format";

    public static void main(String[] args) throws Exception {
        ToolRunner.run(new Test_EB(), data);
    }

    /* A loal folder to save the results of partitioning */
    String hdfsOutputDirectory = "",
            localOutputDirectory = "/home/ubuntu/BGRAP";

    /* the number of worker (the optimal configuration is to set it to the total number of cores on the used cluster) */
    private int nbWorker=1,
            k;

    boolean done=false;

    /* the weight of the penality term in the objectif function of the BGRAP partitionner */
    private float kappa;

    /* The partitionner to use */
    String[] algo=new String[] {
            "BGRAP_eb"
    };

    /* The name of data sets in the @giraphVertexInputRep folder on the HDFS system */
    static String[] data = new String[] {
            "testfile.txt",
    };


    @Override
    /**
     * set the configuration of Giraph job
     * @param argDataNames A list of data sets in the @giraphVertexInputRep folder on the HDFS system.
     * @return
     * @throws IOException
     */
    public int run(String[] argDataNames) throws Exception {
        if(argDataNames.length==0)
            argDataNames=data.clone();



        configureHDFS();

        int[] nbPart = new int[] {4};
        int j = 0, d=0, startIndex=0, maxIndex=nbPart.length-1;
        int maxRun=10, threshold=40;
        nbWorker=1;
        kappa=0.5f;

        for(int part:nbPart){
            for(String alg:algo){
                for(String filename:data){
                    System.out.print("run: ");
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

                    boolean flag=partitioningJob.run(true);

                    System.out.print("Result:" + flag);

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
            GiraphConfiguration.MASTER_COMPUTE_CLASS.set(giraphConf, LPGPartitionner.RDEBMasterCompute.class);
            GiraphConfiguration.COMPUTATION_CLASS.set(giraphConf, LPGPartitionner.ConverterPropagate.class);
        } else if(algo.equals("BGRAP_vb")) {
            GiraphConfiguration.MASTER_COMPUTE_CLASS.set(giraphConf, LPGPartitionner.RDVBMasterCompute.class);
            GiraphConfiguration.COMPUTATION_CLASS.set(giraphConf, LPGPartitionner.ConverterPropagate.class);
        }

        return giraphConf;
    }

}
