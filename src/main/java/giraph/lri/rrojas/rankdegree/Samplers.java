
package giraph.lri.rrojas.rankdegree;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;

import giraph.ml.grafos.okapi.common.data.LongArrayListWritable;
import giraph.ml.grafos.okapi.common.data.MessageWrapper;
import org.apache.giraph.edge.Edge;
import org.apache.giraph.graph.AbstractComputation;
import org.apache.giraph.graph.Vertex;
import org.apache.giraph.utils.ArrayListWritable;
import org.apache.hadoop.io.*;

import giraph.ml.grafos.okapi.spinner.EdgeValue;
import giraph.ml.grafos.okapi.spinner.VertexValue;

import giraph.lri.rrojas.rankdegree.HashMapAggregator;
import giraph.lri.rrojas.rankdegree.SamplingMessage;
import giraph.ml.grafos.okapi.common.computation.SendFriends;

import java.lang.Math; 

@SuppressWarnings("unused")
public class Samplers extends LPGPartitionner {

	//to obtain sample and seed set seed to -3, then change agg at the beginning of each SS and end of sampling.

	/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//SS2: INITIALIZE SAMPLE RD : RANDOM SEEDS  => NEED TO REMOVE USELESS CODE FROM HD //////////////////////////////////////////////////////////////////
	/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	public static class InitializeSampleRD extends AbstractComputation<IntWritable, VertexValue, EdgeValue, SamplingMessage, SamplingMessage> {
		//AE:
		protected int numberOfPartitions;
		protected boolean directedGraph;
		protected String[] loadAggregatorNames;
		protected String[] vertexCountAggregatorNames;
		protected String[] vertexCountAggregatorNamesSampling;
		private boolean debug;

		@Override
		public void preSuperstep() {
			int superstep = (int) getSuperstep();
			directedGraph = getContext().getConfiguration().getBoolean(GRAPH_DIRECTED, DEFAULT_GRAPH_DIRECTED);
			numberOfPartitions = getContext().getConfiguration().getInt(NUM_PARTITIONS, DEFAULT_NUM_PARTITIONS);
			loadAggregatorNames = new String[numberOfPartitions];
			vertexCountAggregatorNames = new String[numberOfPartitions];
			vertexCountAggregatorNamesSampling = new String[numberOfPartitions];
			for (int i = 0; i < numberOfPartitions; i++) {
				loadAggregatorNames[i] = AGG_EGDES_LOAD_PREFIX + i;
				vertexCountAggregatorNames[i] = AGG_VERTEX_COUNT_PREFIX + i;
				vertexCountAggregatorNamesSampling[i] = AGG_VERTEX_COUNT_PREFIX + i+"_SAMPLING";
			}
			debug = getContext().getConfiguration().getBoolean(DEBUG, false);
		}

		@Override
		public void compute(Vertex<IntWritable, VertexValue, EdgeValue> vertex, Iterable<SamplingMessage> messages) throws IOException {
			int sampleSize = ((IntWritable) getAggregatedValue(AGG_SAMPLE)).get();
			int superstep = (int) getSuperstep();
			int vid = vertex.getId().get();
			short partition = (short) vertex.getValue().getCurrentPartition();

			//MISC. CHECKS
			if(partition == -2) {
				// keep initialized partitions updated
				aggregate(vertexCountAggregatorNamesSampling[vertex.getValue().getNewPartition()], new LongWritable(1)); // Hung

				// test to see if we need to reactivate algorithm
				int potentiallySampled = ((IntWritable) getAggregatedValue(AGG_SAMPLE_SS)).get();
				int actuallySampled = ((IntWritable) getAggregatedValue(AGG_SAMPLE_SSR)).get();
				if(potentiallySampled==actuallySampled && potentiallySampled!=0) {
					sendMessageToAllEdges(vertex, new SamplingMessage(vid, -1));
					System.out.println("*SS"+superstep+":Algorithm Reactivation");
				}
			}
			//IF ALGORITHM IS DONE
			if (sampleSize >= BETA){
				// if all partitions are initialized, finish sampling
				if(partitionsInitialized()) {
					//AE:
					int numOutEdges = vertex.getNumEdges();
					if (directedGraph) {
						numOutEdges = vertex.getValue().getRealOutDegree();
					}

					//RR:
					if (partition==-2){
						partition = vertex.getValue().getNewPartition();
						vertex.getValue().setCurrentPartition(partition);
						//System.out.println("*VID_"+vid+":Partition_"+partition);

						//AE:
						aggregate(vertexCountAggregatorNames[partition], new LongWritable(1)); // Hung
						aggregate(loadAggregatorNames[partition], new LongWritable(numOutEdges));
						aggregate(AGG_INITIALIZED_VERTICES, new IntWritable(1));
						aggregate(AGG_FIRST_LOADED_EDGES, new LongWritable(numOutEdges));

						SamplingMessage message = new SamplingMessage(vertex.getId().get(), partition);
						sendMessageToAllEdges(vertex, message);
					}
					NEEDS_SAMPLE = false;

					//AE:
					aggregate(AGG_UPPER_TOTAL_COMM_VOLUME, new LongWritable(Math.min(numberOfPartitions, numOutEdges)));

				} else if (partition==-2) { // make sure to initialize all partitions while balancing loads
					int expectedNodes = Math.floorDiv(sampleSize, numberOfPartitions);
					partition = vertex.getValue().getNewPartition();
					long partitionSize = ((LongWritable) getAggregatedValue(vertexCountAggregatorNamesSampling[partition])).get(); // Hung

					if((partitionSize-expectedNodes)>0 && r.nextFloat() < (float)(partitionSize-expectedNodes)/partitionSize){
						vertex.getValue().setNewPartition(newPartition());
					}
					aggregate(vertexCountAggregatorNamesSampling[vertex.getValue().getNewPartition()], new LongWritable(1)); // Hung
				}
			}
			//IF ALGORITHM NEEDS TO CONTINUE
			else {
				//IF ALGORITHM IS INITIALIZING
				if(superstep == 2 || sampleSize == 0){
					//System.out.println("*SS"+superstep+":InitializingVertices-"+vid);
					if(r.nextFloat() < SIGMA_P){
						vertex.getValue().setCurrentPartition((short)-2);
						vertex.getValue().setNewPartition(newPartition());
						sendMessageToAllEdges(vertex, new SamplingMessage(vid, -1));
						aggregate(AGG_SAMPLE, new IntWritable(1));
						//System.out.println("*SS"+superstep+":isSampled-"+vid);
					}
				}
				//CORE ALGORITHM
				else {
					//READ MESSAGES
					//System.out.println("*SS"+superstep+":Messages-"+vid);
					ArrayList<SamplingMessage> answerNeighbor = new ArrayList<SamplingMessage>();
					ArrayList<SamplingMessage> rankedNeighbors = new ArrayList<SamplingMessage>();
					boolean getsSampled = false;
					forMessage : for (SamplingMessage m : messages) {
						switch(m.getPartition()){
							case -1: //Request vertex degree
								answerNeighbor.add(new SamplingMessage(m.getSourceId(),m.getPartition()));
								break;

							case -2: //Notify vertex has been sampled
								int sampledVerticesSS = ((IntWritable) getAggregatedValue(AGG_SAMPLE_SS)).get();
								float sampledProb = (float)1/((float)sampledVerticesSS/(BETA - sampleSize));
								if(r.nextFloat() < sampledProb){
									getsSampled = true;
								}
								aggregate(AGG_SAMPLE_SS, new IntWritable(1));	// these two are to make sure at least one gets sampled
								aggregate(AGG_SAMPLE_SSR, new IntWritable(1)); // otherwise we will need to reactivate the algorithm
								break forMessage;

							default: //Rank highest degree neighbors
								if(rankedNeighbors.isEmpty()||rankedNeighbors.size()<TAU)
									rankedNeighbors.add(new SamplingMessage(m.getSourceId(),m.getPartition()));
								else
									rankedNeighbors = replaceMin(rankedNeighbors, new SamplingMessage(m.getSourceId(),m.getPartition()));
								break;
						}
					}

					//ACTIONS ACCORDING TO CURRENT STATE AND MESSAGES
					if(partition == -2){
						if(!rankedNeighbors.isEmpty()){
							SamplingMessage nm = new SamplingMessage(vid, -2);
							for(int rn = 0; rn < rankedNeighbors.size(); rn++) {
								aggregate(AGG_SAMPLE_SS, new IntWritable(1));
								sendMessage(new IntWritable(rankedNeighbors.get(rn).getSourceId()), nm);
							}
						}
					} else {
						if(getsSampled){
							vertex.getValue().setCurrentPartition((short)-2);
							vertex.getValue().setNewPartition(newPartition());
							sendMessageToAllEdges(vertex, new SamplingMessage(vid, -1));
							aggregate(AGG_SAMPLE, new IntWritable(1));
							aggregate(vertexCountAggregatorNamesSampling[vertex.getValue().getNewPartition()], new LongWritable(1)); // Hung
							aggregate(AGG_SAMPLE_SSR, new IntWritable(-1)); // we deduct the ones that got sampled to avoid reactivation
							//System.out.println("*SS"+superstep+":isSampled-"+vid);
						} else {
							if(!answerNeighbor.isEmpty()){
								int vertexDegree = vertex.getValue().getRealInDegree() + vertex.getValue().getRealOutDegree();
								SamplingMessage nm = new SamplingMessage(vid, vertexDegree);
								for(int an = 0; an < answerNeighbor.size(); an++) {
									sendMessage(new IntWritable(answerNeighbor.get(an).getSourceId()), nm);
								}
							}
						}
					}
				}
			}
		}

		protected ArrayList<SamplingMessage> replaceMin(ArrayList<SamplingMessage> list, SamplingMessage message){
			int minValue = Integer.MAX_VALUE;
			int minIndex = Integer.MAX_VALUE;
			for (int i = 0; i < list.size() ; i++) {
				int m = list.get(i).getPartition();
				if(m<minValue){
					minValue = m;
					minIndex = i;
				}
			}
			if(message.getPartition() > minValue) {
				list.set(minIndex, message);
			}
			return list;
		}

		protected boolean partitionsInitialized() {
			for (int i = 0; i < numberOfPartitions; i++) {
				long partitionSize = ((LongWritable) getAggregatedValue(vertexCountAggregatorNamesSampling[i])).get(); // Hung
				if(partitionSize==0)
					return false;
			}
			return true;
		}

		protected short newPartition() {
			short newPartition;
			long partitionSize;

			do {
				newPartition = (short) r.nextInt(numberOfPartitions);
				if(partitionsInitialized()) {
					break;
				} else {
					partitionSize = ((LongWritable) getAggregatedValue(vertexCountAggregatorNamesSampling[newPartition])).get(); // Hung
				}
			} while(partitionSize!=0);
			return newPartition;
		}

	}

	/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//SS2: INITIALIZE SAMPLE HD : HIGHEST DEGREE ////////////////////////////////////////////////////////////////////////////////////////////////////////
	/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	public static class InitializeSampleHD extends AbstractComputation<IntWritable, VertexValue, EdgeValue, SamplingMessage, SamplingMessage> {
		//AE:
		protected int numberOfPartitions;
		protected boolean directedGraph;
		protected String[] loadAggregatorNames;
		protected String[] vertexCountAggregatorNames;
		protected String[] vertexCountAggregatorNamesSampling;
		private boolean debug;

		//RR:
		protected int degreeSigma;
		protected float probSigma;


		@Override
		public void preSuperstep() {
			int superstep = (int) getSuperstep();
			//AE:
			directedGraph = getContext().getConfiguration().getBoolean(GRAPH_DIRECTED, DEFAULT_GRAPH_DIRECTED);
			numberOfPartitions = getContext().getConfiguration().getInt(NUM_PARTITIONS, DEFAULT_NUM_PARTITIONS);
			loadAggregatorNames = new String[numberOfPartitions];
			vertexCountAggregatorNames = new String[numberOfPartitions];
			vertexCountAggregatorNamesSampling = new String[numberOfPartitions];
			for (int i = 0; i < numberOfPartitions; i++) {
				loadAggregatorNames[i] = AGG_EGDES_LOAD_PREFIX + i;
				vertexCountAggregatorNames[i] = AGG_VERTEX_COUNT_PREFIX + i;
				vertexCountAggregatorNamesSampling[i] = AGG_VERTEX_COUNT_PREFIX + i+"_SAMPLING";
			}
			debug = getContext().getConfiguration().getBoolean(DEBUG, false);

			//RR:
			if(superstep == 3){
				degreeDist = (MapWritable) getAggregatedValue(AGG_DEGREE_DIST);
				int maxDegree = ((IntWritable) getAggregatedValue(AGG_MAX_DEGREE)).get();

				//get sigma seeds
				int sigmaTemp = 0;
				int sigmaPrev;
				int nextBucket;
				for (int i = maxDegree; i >= 0; i--) {
					IntWritable degreeTemp = new IntWritable(i);
					if(degreeDist.containsKey(degreeTemp)) {
						nextBucket = ((IntWritable)degreeDist.get(degreeTemp)).get();
						sigmaPrev = sigmaTemp;
						sigmaTemp += nextBucket;
						if(sigmaTemp >= SIGMA){
							degreeSigma = i;
							probSigma = ((float)(SIGMA - sigmaPrev) / nextBucket);
							break;
						}
					}
				}
			}
		}

		@Override
		public void compute(Vertex<IntWritable, VertexValue, EdgeValue> vertex, Iterable<SamplingMessage> messages) throws IOException {
			int sampleSize = ((IntWritable) getAggregatedValue(AGG_SAMPLE)).get();
			int superstep = (int) getSuperstep();
			int vid = vertex.getId().get();
			short partition = (short) vertex.getValue().getCurrentPartition();

			//MISC. CHECKS
			if(partition == -2) {
				// keep initialized partitions updated
				aggregate(vertexCountAggregatorNamesSampling[vertex.getValue().getNewPartition()], new LongWritable(1)); // Hung

				// test to see if we need to reactivate algorithm
				int potentiallySampled = ((IntWritable) getAggregatedValue(AGG_SAMPLE_SS)).get();
				int actuallySampled = ((IntWritable) getAggregatedValue(AGG_SAMPLE_SSR)).get();
				if(potentiallySampled==actuallySampled && potentiallySampled!=0) {
					sendMessageToAllEdges(vertex, new SamplingMessage(vid, -1));
					System.out.println("*SS"+superstep+":Algorithm Reactivation");
				}
			}
			//IF ALGORITHM IS DONE
			if (sampleSize >= BETA){
				// if all partitions are initialized, finish sampling
				if(partitionsInitialized()) {
					//AE:
					int numOutEdges = vertex.getNumEdges();
					if (directedGraph) {
						numOutEdges = vertex.getValue().getRealOutDegree();
					}

					//RR:
					if (partition==-2){
						partition = vertex.getValue().getNewPartition();
						vertex.getValue().setCurrentPartition(partition);
						//System.out.println("*VID_"+vid+":Partition_"+partition);

						//AE:
						aggregate(vertexCountAggregatorNames[partition], new LongWritable(1)); // Hung
						aggregate(loadAggregatorNames[partition], new LongWritable(numOutEdges));
						aggregate(AGG_INITIALIZED_VERTICES, new IntWritable(1));
						aggregate(AGG_FIRST_LOADED_EDGES, new LongWritable(numOutEdges));

						SamplingMessage message = new SamplingMessage(vertex.getId().get(), partition);
						sendMessageToAllEdges(vertex, message);
					}
					NEEDS_SAMPLE = false;

					//AE:
					aggregate(AGG_UPPER_TOTAL_COMM_VOLUME, new LongWritable(Math.min(numberOfPartitions, numOutEdges)));

				} else if (partition==-2) { // initialize all partitions while balancing loads
					int expectedNodes = Math.floorDiv(sampleSize, numberOfPartitions);
					partition = vertex.getValue().getNewPartition();
					long partitionSize = ((LongWritable) getAggregatedValue(vertexCountAggregatorNamesSampling[partition])).get(); // Hung

					if((partitionSize-expectedNodes)>0 && r.nextFloat() < (float)(partitionSize-expectedNodes)/partitionSize){
						vertex.getValue().setNewPartition(newPartition());
					}
					aggregate(vertexCountAggregatorNamesSampling[vertex.getValue().getNewPartition()], new LongWritable(1)); // Hung
				}
			}
			//IF ALGORITHM NEEDS TO CONTINUE
			else {
				//IF ALGORITHM IS INITIALIZING
				if(superstep == 2){
					//System.out.println("*SS"+superstep+":FillingDegreeFrequency-"+vid);
					int vertexDegree = vertex.getValue().getRealOutDegree()+vertex.getValue().getRealInDegree();
					addDegreeDist(vertexDegree);
					sendMessageToAllEdges(vertex, new SamplingMessage(vid, -1)); //SEND MESSAGE TO KEEP ALIVE
				} else if(superstep == 3 || sampleSize == 0){
					//System.out.println("*SS"+superstep+":InitializingVertices-"+vid);
					int vertexDegree = vertex.getValue().getRealInDegree() + vertex.getValue().getRealOutDegree();
					if(vertexDegree > degreeSigma){
						vertex.getValue().setCurrentPartition((short)-2);
						vertex.getValue().setNewPartition(newPartition());
						sendMessageToAllEdges(vertex, new SamplingMessage(vid, -1));
						aggregate(AGG_SAMPLE, new IntWritable(1));
						//System.out.println("*SS"+superstep+":isSampled-"+vid);
					} else if (vertexDegree == degreeSigma){
						if(r.nextFloat() < probSigma){
							vertex.getValue().setCurrentPartition((short)-2);
							vertex.getValue().setNewPartition(newPartition());
							sendMessageToAllEdges(vertex, new SamplingMessage(vid, -1));
							aggregate(AGG_SAMPLE, new IntWritable(1));
							//System.out.println("*SS"+superstep+":isSampled-"+vid);
						}
					}
				}
				//CORE ALGORITHM
				else {
					//READ MESSAGES
					//System.out.println("*SS"+superstep+":Messages-"+vid);
					ArrayList<SamplingMessage> answerNeighbor = new ArrayList<SamplingMessage>();
					ArrayList<SamplingMessage> rankedNeighbors = new ArrayList<SamplingMessage>();
					boolean getsSampled = false;
					forMessage : for (SamplingMessage m : messages) {
						switch(m.getPartition()){
							case -1: //Request vertex degree
								answerNeighbor.add(new SamplingMessage(m.getSourceId(),m.getPartition()));
								break;

							case -2: //Notify vertex has been sampled
								int sampledVerticesSS = ((IntWritable) getAggregatedValue(AGG_SAMPLE_SS)).get();
								float sampledProb = (float)1/((float)sampledVerticesSS/(BETA - sampleSize));
								if(r.nextFloat() < sampledProb){
									getsSampled = true;
								}
								aggregate(AGG_SAMPLE_SS, new IntWritable(1));	// these two are to make sure at least one gets sampled
								aggregate(AGG_SAMPLE_SSR, new IntWritable(1)); // otherwise we will need to reactivate the algorithm
								break forMessage;

							default: //Rank highest degree neighbors
								if(rankedNeighbors.isEmpty()||rankedNeighbors.size()<TAU)
									rankedNeighbors.add(new SamplingMessage(m.getSourceId(),m.getPartition()));
								else
									rankedNeighbors = replaceMin(rankedNeighbors, new SamplingMessage(m.getSourceId(),m.getPartition()));
								break;
						}
					}

					//ACTIONS ACCORDING TO CURRENT STATE AND MESSAGES
					if(partition == -2){
						if(!rankedNeighbors.isEmpty()){
							SamplingMessage nm = new SamplingMessage(vid, -2);
							for(int rn = 0; rn < rankedNeighbors.size(); rn++) {
								aggregate(AGG_SAMPLE_SS, new IntWritable(1));
								sendMessage(new IntWritable(rankedNeighbors.get(rn).getSourceId()), nm);
							}
						}
					} else {
						if(getsSampled){
							vertex.getValue().setCurrentPartition((short)-2);
							vertex.getValue().setNewPartition(newPartition());
							sendMessageToAllEdges(vertex, new SamplingMessage(vid, -1));
							aggregate(AGG_SAMPLE, new IntWritable(1));
							aggregate(vertexCountAggregatorNamesSampling[vertex.getValue().getNewPartition()], new LongWritable(1)); // Hung
							aggregate(AGG_SAMPLE_SSR, new IntWritable(-1)); // we deduct the ones that got sampled to avoid reactivation
							//System.out.println("*SS"+superstep+":isSampled-"+vid);
						} else {
							if(!answerNeighbor.isEmpty()){
								int vertexDegree = vertex.getValue().getRealInDegree() + vertex.getValue().getRealOutDegree();
								SamplingMessage nm = new SamplingMessage(vid, vertexDegree);
								for(int an = 0; an < answerNeighbor.size(); an++) {
									sendMessage(new IntWritable(answerNeighbor.get(an).getSourceId()), nm);
								}
							}
						}
					}
				}
			}
		}

		protected ArrayList<SamplingMessage> replaceMin(ArrayList<SamplingMessage> list, SamplingMessage message){
			int minValue = Integer.MAX_VALUE;
			int minIndex = Integer.MAX_VALUE;
			for (int i = 0; i < list.size() ; i++) {
				int m = list.get(i).getPartition();
				if(m<minValue){
					minValue = m;
					minIndex = i;
				}
			}
			if(message.getPartition() > minValue) {
				list.set(minIndex, message);
			}
			return list;
		}

		protected boolean partitionsInitialized() {
			for (int i = 0; i < numberOfPartitions; i++) {
				long partitionSize = ((LongWritable) getAggregatedValue(vertexCountAggregatorNamesSampling[i])).get(); // Hung
				if(partitionSize==0)
					return false;
			}
			return true;
		}

		protected short newPartition() {
			short newPartition;
			long partitionSize; // Hung

			do {
				newPartition = (short) r.nextInt(numberOfPartitions);
				if(partitionsInitialized()) {
					break;
				} else {
					partitionSize = ((LongWritable) getAggregatedValue(vertexCountAggregatorNamesSampling[newPartition])).get(); // Hung
				}
			} while(partitionSize!=0);
			return newPartition;
		}

		protected synchronized void addDegreeDist(int degree) {
			MapWritable temp = new  MapWritable();
			temp.put(new IntWritable(degree), new IntWritable(1));
			aggregate(AGG_DEGREE_DIST, temp);
		}
	}



	/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//SS2: INITIALIZE SAMPLE CC : clustering coefficient ////////////////////////////////////////////////////////////////////////////////////////////////////////
	/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	public static class InitializeSampleCC extends AbstractComputation<IntWritable, VertexValue, EdgeValue, SamplingMessage, SamplingMessage> {
		//AE:
		protected int numberOfPartitions;
		protected boolean directedGraph;
		protected String[] loadAggregatorNames;
		protected String[] vertexCountAggregatorNames;
		protected String[] vertexCountAggregatorNamesSampling;
		private boolean debug;

		//RR:
		protected int degreeSigma;
		protected float probSigma;

		protected int sigma_vertex;
		protected double minCC;
		protected MapWritable clustCoef = new MapWritable();

		@Override
		public void preSuperstep() {
			int superstep = (int) getSuperstep();
			//AE:
			directedGraph = getContext().getConfiguration().getBoolean(GRAPH_DIRECTED, DEFAULT_GRAPH_DIRECTED);
			numberOfPartitions = getContext().getConfiguration().getInt(NUM_PARTITIONS, DEFAULT_NUM_PARTITIONS);
			loadAggregatorNames = new String[numberOfPartitions];
			vertexCountAggregatorNames = new String[numberOfPartitions];
			vertexCountAggregatorNamesSampling = new String[numberOfPartitions];

			for (int i = 0; i < numberOfPartitions; i++) {
				loadAggregatorNames[i] = AGG_EGDES_LOAD_PREFIX + i;
				vertexCountAggregatorNames[i] = AGG_VERTEX_COUNT_PREFIX + i;
				vertexCountAggregatorNamesSampling[i] = AGG_VERTEX_COUNT_PREFIX + i+"_SAMPLING";
			}
			debug = true; //getContext().getConfiguration().getBoolean(DEBUG, false);

			//RR:
			if(superstep == 4){


				// JC:  GET LIMITS OF COEFICIENT FOR SIGMA %
				clustCoef = (MapWritable) getAggregatedValue(AGG_CL_COEFFICIENT);

				class Coefficient implements Comparable{
					Double coef;
					Integer frequency;

					public Coefficient(double coef, int frequency){
						this.coef = coef;
						this.frequency = frequency;
					}

					@Override
					public int compareTo(Object o) {
						double comparecoef=((Coefficient)o).coef;
						if (this.coef < comparecoef) return -1;
						if (this.coef > comparecoef) return 1;
						return 0;
					}
				}

				ArrayList<Coefficient> values = new  ArrayList<Coefficient>();

				double total_coef = 0;
				double max_coef = 0;
				double min_coef = 10000;
				
				for (Entry<Writable, Writable> entry : clustCoef.entrySet()) {
					//System.out.println("SS"+superstep+": Key:"+entry.getKey()+": Value:"+entry.getValue());
					int frequency = ((IntWritable)entry.getValue()).get();
					double c_coef = (((IntWritable) entry.getKey()).get());
					max_coef = Math.max(max_coef,c_coef);
					min_coef = Math.min(min_coef,c_coef);

					values.add(new Coefficient(c_coef,frequency));
					total_coef+=c_coef*frequency;
				}

				Collections.sort(values, Collections.reverseOrder());


				int counter = 0;
				int counterPrev = 0;
				int nextBucket = 0;
				
				for(Coefficient c : values){
					nextBucket = c.frequency;
					counterPrev = counter;
					counter += nextBucket;
					if (counter >= SIGMA) {
						minCC = c.coef;
						probSigma = ((float)(SIGMA - counterPrev) / nextBucket);
						break;
					}
				}

				//System.out.println("total_coef: " + total_coef);
				System.out.println("minCC: " + minCC);


			}
		}


		@Override
		public void compute(Vertex<IntWritable, VertexValue, EdgeValue> vertex, Iterable<SamplingMessage> messages) throws IOException {
			int sampleSize = ((IntWritable) getAggregatedValue(AGG_SAMPLE)).get();
			int superstep = (int) getSuperstep();
			int vid = vertex.getId().get();
			short partition = (short) vertex.getValue().getCurrentPartition();

			//MISC. CHECKS
			if(partition == -2) {
				// keep initialized partitions updated
				aggregate(vertexCountAggregatorNamesSampling[vertex.getValue().getNewPartition()], new LongWritable(1)); // Hung

				// test to see if we need to reactivate algorithm
				int potentiallySampled = ((IntWritable) getAggregatedValue(AGG_SAMPLE_SS)).get();
				int actuallySampled = ((IntWritable) getAggregatedValue(AGG_SAMPLE_SSR)).get();
				if(potentiallySampled==actuallySampled && potentiallySampled!=0) {
					sendMessageToAllEdges(vertex, new SamplingMessage(vid, -1));
					System.out.println("*SS"+superstep+":Algorithm Reactivation");
				}
			}

			//IF ALGORITHM IS DONE
			if (sampleSize >= BETA){
				// if all partitions are initialized, finish sampling
				if(partitionsInitialized()) {
					//AE:
					int numOutEdges = vertex.getNumEdges();
					if (directedGraph) {
						numOutEdges = vertex.getValue().getRealOutDegree();
					}

					//RR:
					if (partition==-2){
						partition = vertex.getValue().getNewPartition();
						vertex.getValue().setCurrentPartition(partition);
						//System.out.println("*VID_"+vid+":Partition_"+partition);

						//AE:
						aggregate(vertexCountAggregatorNames[partition], new LongWritable(1)); // Hung
						aggregate(loadAggregatorNames[partition], new LongWritable(numOutEdges));
						aggregate(AGG_INITIALIZED_VERTICES, new IntWritable(1));
						aggregate(AGG_FIRST_LOADED_EDGES, new LongWritable(numOutEdges));

						SamplingMessage message = new SamplingMessage(vertex.getId().get(), partition);
						sendMessageToAllEdges(vertex, message);
					}
					NEEDS_SAMPLE = false;

					//AE:
					aggregate(AGG_UPPER_TOTAL_COMM_VOLUME, new LongWritable(Math.min(numberOfPartitions, numOutEdges)));

				} else if (partition==-2) { // initialize all partitions while balancing loads
					int expectedNodes = Math.floorDiv(sampleSize, numberOfPartitions);
					partition = vertex.getValue().getNewPartition();
					long partitionSize = ((LongWritable) getAggregatedValue(vertexCountAggregatorNamesSampling[partition])).get(); // Hung

					if((partitionSize-expectedNodes)>0 && r.nextFloat() < (float)(partitionSize-expectedNodes)/partitionSize){
						vertex.getValue().setNewPartition(newPartition());
					}
					aggregate(vertexCountAggregatorNamesSampling[vertex.getValue().getNewPartition()], new LongWritable(1)); // Hung
				}
			}

			//IF ALGORITHM NEEDS TO CONTINUE
			else {
				//IF ALGORITHM IS INITIALIZING
				if(superstep == 2) {
					//System.out.println("MC1: SendFriendsList");

					// JC:  GET FRIENDS OF VERTEX AND SEND THE LIST IN THE MESSAGE

					final ArrayList<IntWritable> friends =  new ArrayList<IntWritable>() ;
					int num_friends = 0;
					for (Edge<IntWritable,EdgeValue> edge : vertex.getEdges()) {
						friends.add(new IntWritable(edge.getTargetVertexId().get()));
						num_friends++;
					}

					sendMessageToAllEdges(vertex, new SamplingMessage(vid,num_friends,friends));


				} else if(superstep == 3){
					//System.out.println("MC2: Clustering Coefficient");

					// JC:  CALCULATE CLUSTERING COEFFICIENT

					int edges = vertex.getNumEdges();
					int triangles = 0;
					double clusteringCoefficient = 0;
					int vertexDegree = vertex.getValue().getRealInDegree() + vertex.getValue().getRealOutDegree();
					
					if (edges>1){ // Ignore if this vertex has only 1 edge
						HashSet<IntWritable> friends = new HashSet<IntWritable>();
						for (Edge<IntWritable, EdgeValue> edge : vertex.getEdges()) {
							friends.add(new IntWritable(edge.getTargetVertexId().get()));
						}
						for (SamplingMessage msg : messages) {
							ArrayList<IntWritable>tmp = msg.getFriendlist();
							//friendsnum += msg.getPartition();
							if (tmp == null ){
								//System.out.println("No friends");
							}else{
								//System.out.println("Some friends");
								for (IntWritable id : tmp) {
									if (friends.contains(id)) {
										// Triangle found
										triangles++;
									}
								}
							}

						}
						clusteringCoefficient = ((double)triangles) / ((double)edges*(edges-1));
					}
					
					// Hung: score = vertexDegree + CC
					//clusteringCoefficient = (vertexDegree + clusteringCoefficient)*1000; // to cast to Short later
					clusteringCoefficient = clusteringCoefficient*1000;
					// clusteringCoefficient = (vertexDegree)*1000; // to cast to Short later

					// clusteringCoefficient = (clusteringCoefficient + Math.log10(vertexDegree))*1000;
					// clusteringCoefficient = (clusteringCoefficient)*1000;

					/* Assign Clustering Coefficient to vertex (reuse the Currentpartition state of the vertex)
					   Use 'CurrentPartition' to store clusteringCoefficient until superstep 4
					*/

					vertex.getValue().setCurrentPartition((short)clusteringCoefficient);
					coeffDictionary((short)clusteringCoefficient);
					sendMessageToAllEdges(vertex, new SamplingMessage(vid, -1)); //SEND MESSAGE TO KEEP ALIVE

				} else if(superstep == 4 || sampleSize == 0){
					int coefvalue = vertex.getValue().getCurrentPartition();
					if(superstep == 4)
						vertex.getValue().setCurrentPartition((short)-1);


					//System.out.println("*SS"+superstep+":InitializingVertices-"+vid);
					// JC:  SELECT INITIAL SEED BASED ON CC

					if(coefvalue > minCC){
						vertex.getValue().setCurrentPartition((short)-2);
						vertex.getValue().setNewPartition(newPartition());
						sendMessageToAllEdges(vertex, new SamplingMessage(vid, -1));
						aggregate(AGG_SAMPLE, new IntWritable(1));
						//System.out.println("*isSeed,"+vid);
					} else if (coefvalue == minCC){
						if(r.nextFloat() < probSigma){
							vertex.getValue().setCurrentPartition((short)-2);
							vertex.getValue().setNewPartition(newPartition());
							sendMessageToAllEdges(vertex, new SamplingMessage(vid, -1));
							aggregate(AGG_SAMPLE, new IntWritable(1));
						}
					}

				}

				//CORE ALGORITHM
				else {
					//READ MESSAGES
					//System.out.println("*SS"+superstep+":Messages-"+vid);
					ArrayList<SamplingMessage> answerNeighbor = new ArrayList<SamplingMessage>();
					ArrayList<SamplingMessage> rankedNeighbors = new ArrayList<SamplingMessage>();
					boolean getsSampled = false;
					forMessage : for (SamplingMessage m : messages) {
						switch(m.getPartition()){
							case -1: //Request vertex degree
								answerNeighbor.add(new SamplingMessage(m.getSourceId(),m.getPartition()));
								break;

							case -2: //Notify vertex has been sampled
								int sampledVerticesSS = ((IntWritable) getAggregatedValue(AGG_SAMPLE_SS)).get();
								float sampledProb = (float)1/((float)sampledVerticesSS/(BETA - sampleSize));
								if(r.nextFloat() < sampledProb){
									getsSampled = true;
								}
								aggregate(AGG_SAMPLE_SS, new IntWritable(1));	// these two are to make sure at least one gets sampled
								aggregate(AGG_SAMPLE_SSR, new IntWritable(1)); // otherwise we will need to reactivate the algorithm
								break forMessage;

							default: //Rank highest degree neighbors
								if(rankedNeighbors.isEmpty()||rankedNeighbors.size()<TAU)
									rankedNeighbors.add(new SamplingMessage(m.getSourceId(),m.getPartition()));
								else
									rankedNeighbors = replaceMin(rankedNeighbors, new SamplingMessage(m.getSourceId(),m.getPartition()));
								break;
						}
					}

					//ACTIONS ACCORDING TO CURRENT STATE AND MESSAGES
					if(partition == -2){
						if(!rankedNeighbors.isEmpty()){
							SamplingMessage nm = new SamplingMessage(vid, -2);
							for(int rn = 0; rn < rankedNeighbors.size(); rn++) {
								aggregate(AGG_SAMPLE_SS, new IntWritable(1));
								sendMessage(new IntWritable(rankedNeighbors.get(rn).getSourceId()), nm);
							}
						}
					} else {
						if(getsSampled){
							vertex.getValue().setCurrentPartition((short)-2);
							vertex.getValue().setNewPartition(newPartition());
							sendMessageToAllEdges(vertex, new SamplingMessage(vid, -1));
							aggregate(AGG_SAMPLE, new IntWritable(1));
							aggregate(vertexCountAggregatorNamesSampling[vertex.getValue().getNewPartition()], new LongWritable(1)); // Hung
							aggregate(AGG_SAMPLE_SSR, new IntWritable(-1)); // we deduct the ones that got sampled to avoid reactivation
							//System.out.println("*SS"+superstep+":isSampled-"+vid);
						} else {
							if(!answerNeighbor.isEmpty()){
								int vertexDegree = vertex.getValue().getRealInDegree() + vertex.getValue().getRealOutDegree();
								SamplingMessage nm = new SamplingMessage(vid, vertexDegree);
								for(int an = 0; an < answerNeighbor.size(); an++) {
									sendMessage(new IntWritable(answerNeighbor.get(an).getSourceId()), nm);
								}
							}
						}
					}
				}
			}
		}

		protected ArrayList<SamplingMessage> replaceMin(ArrayList<SamplingMessage> list, SamplingMessage message){
			int minValue = Integer.MAX_VALUE;
			int minIndex = Integer.MAX_VALUE;
			for (int i = 0; i < list.size() ; i++) {
				int m = list.get(i).getPartition();
				if(m<minValue){
					minValue = m;
					minIndex = i;
				}
			}
			if(message.getPartition() > minValue) {
				list.set(minIndex, message);
			}
			return list;
		}

		protected boolean partitionsInitialized() {
			for (int i = 0; i < numberOfPartitions; i++) {
				long partitionSize = ((LongWritable) getAggregatedValue(vertexCountAggregatorNamesSampling[i])).get(); // Hung
				if(partitionSize==0)
					return false;
			}
			return true;
		}

		protected short newPartition() {
			short newPartition;
			long partitionSize; // Hung

			do {
				newPartition = (short) r.nextInt(numberOfPartitions);
				if(partitionsInitialized()) {
					break;
				} else {
					partitionSize = ((LongWritable) getAggregatedValue(vertexCountAggregatorNamesSampling[newPartition])).get(); // Hung
				}
			} while(partitionSize!=0);
			return newPartition;
		}

		protected synchronized void addDegreeDist(int degree) {
			MapWritable temp = new  MapWritable();
			temp.put(new IntWritable(degree), new IntWritable(1));
			aggregate(AGG_DEGREE_DIST, temp);
		}

		protected synchronized void coeffDictionary(short coefvalue) {
			MapWritable temp = new  MapWritable();
			temp.put(new IntWritable(coefvalue), new IntWritable(1));
			aggregate(AGG_CL_COEFFICIENT, temp);
		}
	}

	/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//SS2: INITIALIZE SAMPLE GD : GRAPH DEGREE //////////////////////////////////////////////////////////////////////////////////////////////////////////
	/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	public static class InitializeSampleGD extends AbstractComputation<IntWritable, VertexValue, EdgeValue, SamplingMessage, SamplingMessage> {
		//AE:
		protected int numberOfPartitions;
		protected boolean directedGraph;
		protected String[] loadAggregatorNames;
		protected String[] vertexCountAggregatorNames;
		protected String[] vertexCountAggregatorNamesSampling;
		private boolean debug;

		@Override
		public void preSuperstep() {
			int superstep = (int) getSuperstep();
			//AE:
			directedGraph = getContext().getConfiguration().getBoolean(GRAPH_DIRECTED, DEFAULT_GRAPH_DIRECTED);
			numberOfPartitions = getContext().getConfiguration().getInt(NUM_PARTITIONS, DEFAULT_NUM_PARTITIONS);
			loadAggregatorNames = new String[numberOfPartitions];
			vertexCountAggregatorNames = new String[numberOfPartitions];
			vertexCountAggregatorNamesSampling = new String[numberOfPartitions];
			for (int i = 0; i < numberOfPartitions; i++) {
				loadAggregatorNames[i] = AGG_EGDES_LOAD_PREFIX + i;
				vertexCountAggregatorNames[i] = AGG_VERTEX_COUNT_PREFIX + i;
				vertexCountAggregatorNamesSampling[i] = AGG_VERTEX_COUNT_PREFIX + i+"_SAMPLING";
			}
			debug = getContext().getConfiguration().getBoolean(DEBUG, false);

			if(superstep == 3){
				degreeDist = (MapWritable) getAggregatedValue(AGG_DEGREE_DIST);

				//calculate graph degree probability
				int frequency;
				float prob = 0;
				float maxProb = 0;
				float sumproduct = 0;
				for (Entry<Writable, Writable> entry : degreeDist.entrySet()) {
					//System.out.println("SS"+superstep+": Key:"+entry.getKey()+": Value:"+entry.getValue());
					frequency = ((IntWritable) entry.getValue()).get();
					prob = ((float)frequency)/totalVertexNumber;
					sumproduct += frequency*prob;
					if(prob>maxProb)
						maxProb=prob;
					degreeProb.put(((IntWritable)entry.getKey()).get(), prob);
				}
				//adjusting probabilities
				adjustingFactorSeed = SIGMA/sumproduct;
				relaxingFactorPropagation = (1.0f-maxProb)/2;
			}
		}

		@Override
		public void compute(Vertex<IntWritable, VertexValue, EdgeValue> vertex, Iterable<SamplingMessage> messages) throws IOException {
			int sampleSize = ((IntWritable) getAggregatedValue(AGG_SAMPLE)).get();
			int superstep = (int) getSuperstep();
			int vid = vertex.getId().get();
			short partition = (short) vertex.getValue().getCurrentPartition();

			//MISC. CHECKS
			if(partition == -2) {
				// keep initialized partitions updated
				aggregate(vertexCountAggregatorNamesSampling[vertex.getValue().getNewPartition()], new LongWritable(1));  // Hung

				// test to see if we need to reactivate algorithm
				int potentiallySampled = ((IntWritable) getAggregatedValue(AGG_SAMPLE_SS)).get();
				int actuallySampled = ((IntWritable) getAggregatedValue(AGG_SAMPLE_SSR)).get();
				if(potentiallySampled==actuallySampled && potentiallySampled!=0) {
					sendMessageToAllEdges(vertex, new SamplingMessage(vid, -1));
					System.out.println("*SS"+superstep+":Algorithm Reactivation");
				}
			}
			//IF ALGORITHM IS DONE
			if (sampleSize >= BETA){
				// if all partitions are initialized, finish sampling
				if(partitionsInitialized()) {
					//AE:
					int numOutEdges = vertex.getNumEdges();
					if (directedGraph) {
						numOutEdges = vertex.getValue().getRealOutDegree();
					}

					//RR:
					if (partition==-2){
						partition = vertex.getValue().getNewPartition();
						vertex.getValue().setCurrentPartition(partition);
						//System.out.println("*VID_"+vid+":Partition_"+partition);

						//AE:
						aggregate(vertexCountAggregatorNames[partition], new LongWritable(1)); // Hung
						aggregate(loadAggregatorNames[partition], new LongWritable(numOutEdges));
						aggregate(AGG_INITIALIZED_VERTICES, new IntWritable(1));
						aggregate(AGG_FIRST_LOADED_EDGES, new LongWritable(numOutEdges));

						SamplingMessage message = new SamplingMessage(vertex.getId().get(), partition);
						sendMessageToAllEdges(vertex, message);
					}
					NEEDS_SAMPLE = false;

					//AE:
					aggregate(AGG_UPPER_TOTAL_COMM_VOLUME, new LongWritable(Math.min(numberOfPartitions, numOutEdges)));

				} else if (partition==-2) { // initialize all partitions while balancing loads
					int expectedNodes = Math.floorDiv(sampleSize, numberOfPartitions);
					partition = vertex.getValue().getNewPartition();
					Long partitionSize = ((LongWritable) getAggregatedValue(vertexCountAggregatorNamesSampling[partition])).get(); //Hung

					if((partitionSize-expectedNodes)>0 && r.nextFloat() < (float)(partitionSize-expectedNodes)/partitionSize){
						vertex.getValue().setNewPartition(newPartition());
					}
					aggregate(vertexCountAggregatorNamesSampling[vertex.getValue().getNewPartition()], new LongWritable(1)); // Hung
				}
			}
			//IF ALGORITHM NEEDS TO CONTINUE
			else {
				//IF ALGORITHM IS INITIALIZING
				if(superstep == 2){
					//System.out.println("*SS"+superstep+":FillingDegreeFrequency-"+vid);
					int vertexDegree = vertex.getValue().getRealOutDegree()+vertex.getValue().getRealInDegree();
					addDegreeDist(vertexDegree);
					sendMessageToAllEdges(vertex, new SamplingMessage(vid, -1)); //SEND MESSAGE TO KEEP ALIVE
				} else if(superstep == 3 || sampleSize == 0){
					//System.out.println("*SS"+superstep+":InitializingVertices-"+vid);
					int vertexDegree = vertex.getValue().getRealInDegree() + vertex.getValue().getRealOutDegree();

					float vertexProb = 0.0f;
					if(degreeProb.containsKey(vertexDegree))
						vertexProb = degreeProb.get(vertexDegree)*adjustingFactorSeed;
					if(r.nextFloat() < vertexProb){
						vertex.getValue().setCurrentPartition((short)-2);
						vertex.getValue().setNewPartition(newPartition());
						sendMessageToAllEdges(vertex, new SamplingMessage(vid, -1));
						aggregate(AGG_SAMPLE, new IntWritable(1));
						//System.out.println("*isSeed,"+vid);
					}
				}
				//CORE ALGORITHM
				else {
					//READ MESSAGES
					//System.out.println("*SS"+superstep+":Messages-"+vid);
					ArrayList<SamplingMessage> answerNeighbor = new ArrayList<SamplingMessage>();
					ArrayList<SamplingMessage> neighbors = new ArrayList<SamplingMessage>();
					boolean getsSampled = false;
					forMessage : for (SamplingMessage m : messages) {
						switch(m.getPartition()){
							case -1: //Request vertex degree
								answerNeighbor.add(new SamplingMessage(m.getSourceId(),m.getPartition()));
								break;

							case -2: //Notify vertex has been sampled
								int sampledVerticesSS = ((IntWritable) getAggregatedValue(AGG_SAMPLE_SS)).get();
								float sampledProb = (float)1/((float)sampledVerticesSS/(BETA - sampleSize));
								if(r.nextFloat() < sampledProb){
									getsSampled = true;
								}
								aggregate(AGG_SAMPLE_SS, new IntWritable(1));	// these two are to make sure at least one gets sampled
								aggregate(AGG_SAMPLE_SSR, new IntWritable(1)); // otherwise we will need to reactivate the algorithm
								break forMessage;

							default:
								neighbors.add(new SamplingMessage(m.getSourceId(),m.getPartition()));
						}
					}

					//ACTIONS ACCORDING TO CURRENT STATE AND MESSAGES
					if(partition == -2){
						if(!neighbors.isEmpty()){
							SamplingMessage nm = new SamplingMessage(vid, -2);
							for(int rn = 0; rn < neighbors.size(); rn++) {
								float vertexProb = 0.0f;
								if(degreeProb.containsKey(neighbors.get(rn).getPartition()))
									vertexProb = degreeProb.get(neighbors.get(rn).getPartition())+relaxingFactorPropagation;
								if(r.nextFloat() < vertexProb){
									aggregate(AGG_SAMPLE_SS, new IntWritable(1));
									sendMessage(new IntWritable(neighbors.get(rn).getSourceId()), nm);
								}
							}
						}
					} else {
						if(getsSampled){
							vertex.getValue().setCurrentPartition((short)-2);
							vertex.getValue().setNewPartition(newPartition());
							sendMessageToAllEdges(vertex, new SamplingMessage(vid, -1));
							aggregate(AGG_SAMPLE, new IntWritable(1));
							aggregate(vertexCountAggregatorNamesSampling[vertex.getValue().getNewPartition()], new LongWritable(1)); // Hung
							aggregate(AGG_SAMPLE_SSR, new IntWritable(-1)); // we deduct the ones that got sampled to avoid reactivation
							//System.out.println("*isSampled,"+vid);
						} else {
							if(!answerNeighbor.isEmpty()){
								int vertexDegree = vertex.getValue().getRealInDegree() + vertex.getValue().getRealOutDegree();
								SamplingMessage nm = new SamplingMessage(vid, vertexDegree);
								for(int an = 0; an < answerNeighbor.size(); an++) {
									sendMessage(new IntWritable(answerNeighbor.get(an).getSourceId()), nm);
								}
							}
						}
					}
				}
			}
		}

		protected boolean partitionsInitialized() {
			for (int i = 0; i < numberOfPartitions; i++) {
				long partitionSize = ((LongWritable) getAggregatedValue(vertexCountAggregatorNamesSampling[i])).get(); // Hung
				if(partitionSize==0)
					return false;
			}
			return true;
		}

		protected short newPartition() {
			short newPartition;
			long partitionSize; // Hung

			do {
				newPartition = (short) r.nextInt(numberOfPartitions);
				if(partitionsInitialized()) {
					break;
				} else {
					partitionSize = ((LongWritable) getAggregatedValue(vertexCountAggregatorNamesSampling[newPartition])).get(); // Hung
				}
			} while(partitionSize!=0);
			return newPartition;
		}

		protected synchronized void addDegreeDist(int degree) {
			MapWritable temp = new  MapWritable();
			temp.put(new IntWritable(degree), new IntWritable(1));
			aggregate(AGG_DEGREE_DIST, temp);
		}



	}

}