date := $(shell date +'%m%d_%H%M%S')
code := $(GIRAPH_HOME)/giraph-examples/target/giraph-examples-1.2.0-for-hadoop-1.2.1-jar-with-dependencies.jar
sampling := 'InitializeSampleHD'
partitions := 8
#graph := c_graph_30_0.3_0.2_5
#graph := WikiTalk
#graph := BerkeleyStan
#graph := Flixster
#graph := DelaunaySC --no
#graph := Pokec --no
#graph := soc-LiveJournal
#graph := Orkut
#graph := graph500-scale23-ef16
graph := Twitter
#graph := sk-2005
w := 1
main := 'giraph.lri.rrojas.rankdegree.LPGPartitionner$$ConverterPropagate'
masterCompute := 'giraph.lri.rrojas.rankdegree.LPGPartitionner$$RDEBMasterCompute'
vif := giraph.format.personalized.BGRAPVertexInputFormat
vof := giraph.format.personalized.BGRAP_IdWithPartitionOuputFormat
vifo := giraph.lri.rrojas.rankdegree.RR_BGRAP_InputFormat
vofo := giraph.lri.rrojas.rankdegree.RR_BGRAP_OuputFormat
ip := /data/$(graph)
op := ~/bdrp_output/$(date)_$(sampling)_$(graph)_$(w)_folder
beta := 0.10 #0.4
sigma := 0.01 #0.05
tau := 5
save_dd := true

create_dirs:
	hadoop dfs -mkdir /rrojas/
	hadoop dfs -mkdir /rrojas/inputdata/
	hadoop dfs -mkdir /rrojas/outputdata/

ls_code:
	ls -l $(HADOOP_HOME)/lib/R*
hls:
	hadoop dfs -lsr /
put_data:
	hadoop dfs -put ~/testdata/$(graph) /bdrp_data/.
put_all_data:
	hadoop fs -put ~/testdata/* /bdrp_data/.
rm_data:
	hadoop fs -rm /rrojas/inputdata/$(graph)
get_results:
	$(HADOOP_HOME)/bin/hadoop fs -get /bdrp/output/ Results/
rm_logs:
	rm -fR $(HADOOP_LOGS)/*
rm_results:
	sudo rm -fR ~/Results/*
	sudo rm -fR ~/PaperResult/*
hrm_results:
	hadoop fs -rmr  /bdrp/output/*
clean_container:
	hadoop fs -rmr /user
	hadoop fs -rmr /tmp

clean_all:
	make rm_logs
	make rm_results
	make hrm_results
	make clean_container

run:
	giraph $(HADOOP_HOME)/lib/$(code) $(main) -w $(w) -vif $(vif) -vip $(ip) -vof $(vof) -op $(op) -ca giraph.masterComputeClass=$(masterCompute) -ca giraph.edgeValueClass=giraph.ml.grafos.okapi.spinner.EdgeValue -ca giraph.vertexValueClass=giraph.ml.grafos.okapi.spinner.VertexValue -ca giraph.outEdgesClass=giraph.ml.grafos.okapi.spinner.ShortBooleanHashMapsEdgesInOut -ca giraph.inputOutEdgesClass=giraph.ml.grafos.okapi.spinner.ShortBooleanHashMapsEdgesInOut -ca giraph.numComputeThreads=1,giraph.numInputThreads=1,giraph.numOutputThreads=1 -ca giraph.SplitMasterWorker=false -ca spinner.numberOfPartitions=$(partitions) -ca graph.directed=true -ca bgrap.SAMPLING_TYPE=$(sampling) -ca bgrap.GRAPH='$(graph)' -ca bgrap.DATE='$(date)' -ca rankdegree.TAU='$(tau)' -ca rankdegree.SIGMA='$(sigma)' -ca rankdegree.BETA='$(beta)' -ca rankdegree.SAVE_DD='$(save_dd)' 

run_optimized:
	sudo $(HADOOP_HOME)/bin/hadoop jar $(GIRAPH_HOME)/giraph-examples-1.2.0.jar org.apache.giraph.GiraphRunner $(main) -w $(w) -vif $(vifo) -vip $(ip) -vof $(vofo) -op $(op) -ca giraph.masterComputeClass=$(masterCompute) -ca giraph.edgeValueClass=giraph.ml.grafos.okapi.spinner.EdgeValue -ca giraph.vertexValueClass=giraph.ml.grafos.okapi.spinner.VertexValue -ca giraph.outEdgesClass=giraph.lri.rrojas.rankdegree.ShortBooleanHashMapsEdgesInOut -ca giraph.inputOutEdgesClass=giraph.lri.rrojas.rankdegree.ShortBooleanHashMapsEdgesInOut -ca giraph.numComputeThreads=1,giraph.numInputThreads=1,giraph.numOutputThreads=1 -ca giraph.SplitMasterWorker=false -ca spinner.numberOfPartitions=$(partitions) -ca graph.directed=true -ca bgrap.SAMPLING_TYPE=$(sampling) -ca bgrap.GRAPH='$(graph)' -ca bgrap.DATE='$(date)' -ca rankdegree.TAU='$(tau)' -ca rankdegree.SIGMA='$(sigma)' -ca rankdegree.BETA='$(beta)' -ca rankdegree.SAVE_DD='$(save_dd)'



