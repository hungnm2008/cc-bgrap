package giraph.data.converstion;

import org.apache.giraph.conf.GiraphConfiguration;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.util.Tool;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URI;

/**
 * This class allows to create an empty GiraphJob with the configuration of the
 * Hadoop cluster
 */
public class PersonalizedGiraphJob implements Tool {
	private Configuration conf = new Configuration();
	/**
	 * the path of the HADOOP_HOME configuration folder
	 */
	private String confPath = System.getenv("HADOOP_HOME") + "/conf";
	/**
	 * HDFS Node address
	 */
	private String hdfsNode = "hdfs://master:50001";
	/**
	 * Hadoop master address
	 */
	private String masterIp = "";
	private FileSystem hadoopDataFileSystem;
	private int memoryFraction = 8;

	/**
	 * set the configuration of the hadoop cluster
	 * 
	 * @throws IOException
	 */
	public void configureHDFS() throws IOException {

		// load configuration from $HADOOP_HOME/conf
		System.out.println(System.getenv("HADOOP_HOME"));

		// Or configure manually
		getConf().set("hadoop.job.ugi", "adnanmoe");
		getConf().set("dfs.web.ugi", "adnanmoe");
		// let Hadoop create its own tmp directories
		// getConf().set("hadoop.tmp.dir", System.getenv("HOME") +
		// "/AppHadoop/tmp/"
		// +System.getenv("USER") + "_" + System.getenv("HOSTNAME"));
		getConf().set("dfs.permissions", "false");
		getConf().set("dfs.namenode.name.dir", System.getenv("HOME") + "/AppHadoop/data/namenode");
		getConf().set("dfs.datanode.data.dir", System.getenv("HOME") + "/AppHadoop/data/datanode");
		getConf().set("fs.default.name", hdfsNode);// for hadoop 2

		int mem = 4 * memoryFraction;
		double pct1 = 1;
		double pct2 = 0.8;
		// mapred.xml
		getConf().set("mapred.job.tracker", masterIp + ":50003");
		// to avoid java.lang.OutOfMemoryError exception (Java heap space)
		// -Xms256m -Xmx8g
		// getConf().set("mapred.child.java.opts", "-Xmx12288m");
		getConf().set("mapred.child.java.opts", "-Xmx" + (int) ((pct1) * mem * 1024) + "m");

		getConf().set("mapred.map.child.java.opts", "-Xmx" + (int) (pct2 * mem * 1024) + "m");
		getConf().set("mapred.reduce.child.java.opts", "-Xmx" + (int) (pct2 * mem * 1024) + "m");

		// getConf().setBoolean("mapred.acls.enabled", true);
		// getConf().set("mapreduce.job.acl-modify-job", "adnanmoe");
		// getConf().set("mapreduce.job.acl-view-job", "adnanmoe");
		// getConf().set("mapred.queue.default.acl-administer-jobs",
		// "adnanmoe");

		getConf().setInt("mapred.job.map.memory.mb", mem * 1024);
		getConf().setInt("mapred.job.reduce.memory.mb", mem * 1024);
		getConf().setInt("mapred.tasktracker.indexcache.mb", 128);

		// set higher value when dealing with large data sets
		getConf().setInt("mapreduce.task.timeout", 600000 * 100);// in
																	// millisecond
		getConf().setInt("mapreduce.job.counters.max", 1500);//
		getConf().setInt("mapred.job.counters.max", 1500);//
		// getConf().setInt("mapreduce.job.counters.limit", 500);// for hadoop
		// 2.x

		// System.out.println(conf.get("mapred.task.partition"));
		// getConf().set("mapred.task.partition", "8");

		// Xms256m -Xmx4g -XX:-UseGCOverheadLimit -XX:GCTimeRatio=19
		// -XX:+UseParallelOldGC -XX:+UseParallelGC

		// because fs.default.name is unknown
		// getConf().set("fs.defaultFS", hdfsNode);

		// for large file
		// to avoid java.lang.OutOfMemoryError exception: GC overhead limit
		// exceeded
		// add this parameter to the JVM or to the program launcher
		// -XX:-UseGCOverheadLimit

		// to get access to HDFS system
		conf.set("fs.hdfs.impl", org.apache.hadoop.hdfs.DistributedFileSystem.class.getName());
		conf.set("fs.file.impl", org.apache.hadoop.fs.LocalFileSystem.class.getName());

		// conf.setBoolean("mapreduce.job.committer.task.cleanup.needed",
		// false);
		conf.setBoolean("mapred.job.tracker.persist.jobstatus.active", true);

		setHadoopDataFileSystem(FileSystem.get(URI.create(hdfsNode), conf));
	}
	
	/**
	 * read configuration from the $HADOOP_HOME/config/
	 */
	public void setConfigFromFiles() {
		File confDir = new File(confPath);
		if (confDir.exists() && confDir.isDirectory() && confDir.list().length > 0) {
			String[] confFiles = confDir.list(new FilenameFilter() {
				public boolean accept(File dir, String name) { // TODO
					// Auto-generated method stub
					if (name.endsWith(".xml"))
						return true;
					else
						return false;
				}
			});
			for (int i = 0; i < confFiles.length; i++) {
				System.out.println(confFiles[i]);
				conf.addResource(new Path(confPath + "/" + confFiles[i]));
			}
			// conf.set("hadoop.job.history.location",
			// "${hadoop.tmp.dir}/history/${user.name}");
			conf.set("dfs.web.ugi", "adnanmoe");
		}
	}

	public FileSystem getHadoopDataFileSystem() {
		return hadoopDataFileSystem;
	}

	public void setHadoopDataFileSystem(FileSystem hadoopDataFileSystem) {
		this.hadoopDataFileSystem = hadoopDataFileSystem;
	}

	public String getConfPath() {
		return confPath;
	}

	public void setConfPath(String confPath) {
		this.confPath = confPath;
	}

	public String getHdfsNode() {
		return hdfsNode;
	}

	public void setHdfsNode(String hdfsNode) {
		this.hdfsNode = hdfsNode;
	}

	public String getMasterIp() {
		return masterIp;
	}

	public void setMasterIp(String masterIp) {
		this.masterIp = masterIp;
	}

	public int getMemoryFraction() {
		return memoryFraction;
	}

	public void setMemoryFraction(int nbWorkers) {
		this.memoryFraction = nbWorkers;
	}

	/**
	 * set additional configurations parameters
	 * 
	 * @param giraphConf
	 */
	protected void useAcceleratedSetting(GiraphConfiguration giraphConf) {

		// Multithreading
		/*
		 * To minimize network usage when reading input splits, each worker can
		 * prioritize splits that reside on its host.
		 */
		// GiraphConfiguration.USE_INPUT_SPLIT_LOCALITY.set(giraphConf, true);
		/**
		 * allows several vertexWriters to be created and written to in parallel
		 */
		GiraphConfiguration.VERTEX_OUTPUT_FORMAT_THREAD_SAFE.set(giraphConf, true);
		/**
		 * Number of threads for input/output split loading
		 */
		int numThreads = 1;
		GiraphConfiguration.NUM_INPUT_THREADS.set(giraphConf, 8);
		GiraphConfiguration.NUM_OUTPUT_THREADS.set(giraphConf, numThreads);
		GiraphConfiguration.NUM_COMPUTE_THREADS.set(giraphConf, 8);

		// Message exchange tuning
		/**
		 * let a vertex receiving messages up to the limitation of the worker’s
		 * heap space
		 */
		// GiraphConfiguration.USE_BIG_DATA_IO_FOR_MESSAGES.set(giraphConf,
		// true);
		/*
		 * encodes one message for multiple vertices in the same destination
		 * partition. This encoding is useful for graph algorithms that
		 * broadcast identical messages to a large number of vertices
		 */
		// GiraphConfiguration.MESSAGE_ENCODE_AND_STORE_TYPE.set(giraphConf,
		// MessageEncodeAndStoreType.EXTRACT_BYTEARRAY_PER_PARTITION);

		// Out-of-core processing
		// GiraphConfiguration.USE_OUT_OF_CORE_GRAPH.set(giraphConf, true);
		// GiraphConfiguration.MAX_PARTITIONS_IN_MEMORY.set(giraphConf, 128);
		// giraphConf.setBoolean("giraph.useOutOfCoreMessages", true);

		// giraphConf.setBoolean("giraph.jmap.histo.enable", true);
		giraphConf.setBoolean("giraph.oneToAllMsgSending", true);
		/*
		 * giraph.serverReceiveBufferSize=integer, default 524288: You want to
		 * increase the buffer size when your application sends a lot of data;
		 * for example, many and/or large messages. This avoids that the server
		 * is constantly moving little pieces of data around
		 */
		int m = 100;
		giraphConf.setInt("giraph.serverReceiveBufferSize", m * 1024 * 1024); // m*1MByte
		giraphConf.setInt("giraph.clientSendBufferSize", m * 1024 * 1024); // m*1MByte

	}

	@Override
	public Configuration getConf() {
		// TODO Auto-generated method stub
		return conf;
	}

	@Override
	public void setConf(Configuration arg0) {
		// TODO Auto-generated method stub
		conf = arg0;
	}

	@Override
	public int run(String[] arg0) throws Exception {
		// TODO Auto-generated method stub
		return 0;
	}

}
