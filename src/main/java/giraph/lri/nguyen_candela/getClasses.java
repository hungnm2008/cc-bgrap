package giraph.lri.nguyen_candela;

import java.io.*;
import java.nio.file.Files;

public class getClasses {


    public void listFilesForFolder(final File folder) {

    }

    public static void main(String[] args) {

        long startTime = System.currentTimeMillis();

        String input_folder = args[0];
        String mode = args[1];
        String output = args[2];
        BufferedReader reader;

        try {

            FileWriter myWriter = new FileWriter(output + ".csv");
            String line_csv = "";
            String csv_header = "MODE,JOB_ID,WORKERS,FILE,ALGO,TAU,SIGMA,BETA,NUMPARTITIONS,VERTICES"+
                                    ",REAL EDGES, VIRTUAL EDGES,Time,Sampling Time,Total LP,Score,Migrations,"+
                                "R MaxNorm unbalance,V MaxNorm unbalance,Vertex MaxNorm unbalance,#real EC,#real EC (%%),"+
                                "#virtual EC,#virtual EC (%%),Vertex Balance JSD,Real Edge Balance JSD,Virtual Edge Balance JSD\n";
            myWriter.write(csv_header);


            File folder = new File(input_folder);
            for (File fileEntry : folder.listFiles()) {
                if (!fileEntry.getName().contains("GDD")) {
                    reader = new BufferedReader(new FileReader(
                            fileEntry));
                    String line = reader.readLine();
                    String JobID = "";

                    String par_1 = "";
                    String par_2 = "";
                    String par_3 = "";
                    String par_4 = "";
                    String par_5 = "";
                    String par_6 = "";
                    String par_7 = "";
                    String par_8 = "";
                    String par_9 = "";
                    String par_10 = "";
                    String par_11 = "";
                    String par_12 = "";
                    String par_13 = "";
                    String par_14 = "";
                    String par_15 = "";
                    String par_16 = "";
                    String par_17 = "";
                    String par_18 = "";
                    String par_19 = "";
                    String par_20 = "";
                    String par_21 = "";
                    String par_22 = "";
                    String par_23 = "";
                    String par_24 = "";
                    String par_25 = "";
                    String par_26 = "";

                    while (line != null) {
                        if (line.contains("JOB ID")){
                            par_1 = line.split(",")[1];
                        }
                        if (line.contains("WORKERS")){
                            par_2 = line.split(",")[1];
                        }
                        if (line.contains("GRAPH")){
                            par_3 = line.split(",")[1];
                        }
                        if (line.contains("ALGORITHM")){
                            par_4 = line.split(",")[1];
                        }
                        if (line.contains("TAU")){
                            par_5 = line.split(",")[1];
                        }
                        if (line.contains("SIGMA")){
                            par_6 = line.split(",")[1];
                        }
                        if (line.contains("BETA")){
                            par_7 = line.split(",")[1];
                        }
                        if (line.contains("PARITIONS")){
                            par_8 = line.split(",")[1];
                        }
                        if (line.contains("VERTICES")){
                            par_9 = line.split(",")[1];
                        }
                        if (line.contains("REAL EDGES")){
                            par_10 = line.split(",")[1];
                        }
                        if (line.contains("VIRTUAL EDGES")){
                            par_11 = line.split(",")[1];
                        }
                        if (line.contains("TOTAL TIME")){
                            par_12 = line.split(",")[1];
                        }
                        if (line.contains("Sampling time")){
                            par_13 = line.split(",")[1];
                        }
                        if (line.contains("LP time")){
                            par_14 = line.split(",")[1];
                        }

                        if (line.contains("REAL SCORE")){
                            par_15 = line.split(",")[1];
                        }
                        if (line.contains("TOT MIGRATIONS")){
                            par_16 = line.split(",")[1];
                        }
                        if (line.contains("REAL E MAXNORMLOAD")){
                            par_17 = line.split(",")[1];
                        }
                        if (line.contains("VIRTUAL E MAXNORM LOAD")){
                            par_18 = line.split(",")[1];
                        }
                        if (line.contains("REAL V MAXNORMLOAD")){
                            par_19 = line.split(",")[1];
                        }
                        if (line.contains("REAL EC")){
                            par_20 = line.split(",")[1];
                        }
                        if (line.contains("REAL EC PCT")){
                            par_21 = line.split(",")[1];
                        }
                        if (line.contains("VIRTUAL EC")){
                            par_22 = line.split(",")[1];
                        }
                        if (line.contains("VIRTUAL EC PCT")){
                            par_23 = line.split(",")[1];
                        }
                        if (line.contains("REAL VB JSD")){
                            par_24 = line.split(",")[1];
                        }
                        if (line.contains("REAL EB JSD")){
                            par_25 = line.split(",")[1];
                        }
                        if (line.contains("VIRTUAL ED JSD")){
                            par_26 = line.split(",")[1];
                        }

                        // read next line
                        line = reader.readLine();
                    }

                    line_csv = mode +","+par_1 +","+par_2 +","+par_3 +","+par_4 +","+par_5 +","+par_6 +","+par_7 +","+
                            par_8 +","+par_9 +","+par_10 +","+par_11 +","+par_12 +","+par_13 +","+
                            par_14 +","+par_15 +","+par_16 +","+par_17 +","+par_18 +","+par_19 +","+par_20+","+
                            par_21 +","+par_22 +","+par_23 +","+par_24 +","+par_25 +","+par_26 ;

                    myWriter.write(line_csv + "\n");
                    line_csv="";
                    reader.close();

                }
            }

            myWriter.close();

        }catch (IOException e) {
            e.printStackTrace();
        }

        /*
        try {
            reader = new BufferedReader(new FileReader(
                    file));

            FileWriter myWriter = new FileWriter(output + ".csv");

            String line = reader.readLine();
            String csv_line = "";
            String csv_header = "FILE,ALGO,TAU,SIGMA,BETA,NUMPARTITIONS,Time,Score,Migrations,Total LP,R MaxNorm unbalance (x1000),V MaxNorm unbalance (x1000),#real EC,#real EC (%%),#virtrual EC,Vertex Balance JSD,Sampling Time\n";
            myWriter.write(csv_header);
            int counter = 0;

            String BETA = "";
            String TAU = "";
            String SIGMA = "";
            String GRAPH = "";
            String SAMPLING_TYPE = "";
            String numberOfPartitions = "";
            String Time = "";
            String Score = "";
            String Migrations = "";
            String TotalLP = "";

            String r_MNU = "";
            String v_MNU = "";
            String r_EC = "";
            String r_EC_per = "";
            String v_EC = "";
            String JSD = "";
            String ST = "";


            while (line != null) {
                if (line.contains("sudo /usr/local/hadoop/bin/hadoop jar")){

                    if (counter > 0){


                        csv_line = GRAPH + "," + SAMPLING_TYPE + "," +  TAU + "," + SIGMA + "," +
                                BETA + "," + numberOfPartitions +"," + Time +
                                "," + Score +"," + Migrations +"," + TotalLP +
                                "," + r_MNU +"," + v_MNU +"," + r_EC +
                                "," + r_EC_per +"," + v_EC +"," + JSD +
                                "," + ST +"\n";

                        myWriter.write(csv_line);

                        BETA = "";
                        TAU = "";
                        SIGMA = "";
                        GRAPH = "";
                        SAMPLING_TYPE = "";
                        numberOfPartitions = "";
                        Time = "";
                        Score = "";
                        Migrations = "";
                        TotalLP = "";
                        r_MNU = "";
                        v_MNU = "";
                        r_EC = "";
                        r_EC_per = "";
                        v_EC = "";
                        JSD = "";
                        ST = "";

                    }
                    counter++;





                    String[] args_line = line.split(" -ca ");
                    for (String arg_unit : args_line) {
                        String[] arg_name = arg_unit.split("=");
                        if (arg_name[0].contains("BETA")){
                            BETA = arg_name[1].replace("'","");
                        }
                        if (arg_name[0].contains("TAU")){
                            TAU = arg_name[1].replace("'","");
                        }

                        if (arg_name[0].contains("SIGMA")){
                            SIGMA = arg_name[1].replace("'","");
                        }

                        if (arg_name[0].contains("GRAPH")){
                            GRAPH = arg_name[1].replace("'","");
                        }

                        if (arg_name[0].contains("SAMPLING_TYPE")){
                            SAMPLING_TYPE = arg_name[1].replace("'","");
                        }

                        if (arg_name[0].contains("numberOfPartitions")){
                            numberOfPartitions = arg_name[1].replace("'","");
                        }


                    }
                    System.out.println(line);


                }

                if (line.contains("Score (x1000)")){
                    String[] args_line = line.split("=");
                    Score = args_line[1];
                }

                if (line.contains("Migrations")){
                    String[] args_line = line.split("=");
                    Migrations = args_line[1];
                }

                if (line.contains("Total LP (ms)")){
                    String[] args_line = line.split("=");
                    TotalLP = args_line[1];
                }

                if (line.contains("Total (ms)")){
                    String[] args_line = line.split("=");
                    Time = args_line[1];
                }

                if (line.contains("R MaxNorm unbalance (x1000)")){
                    String[] args_line = line.split("=");
                    r_MNU = args_line[1];
                }

                if (line.contains("V MaxNorm unbalance (x1000)")){
                    String[] args_line = line.split("=");
                    v_MNU = args_line[1];
                }

                if (line.contains("#real EC")){
                    String[] args_line = line.split("=");
                    r_EC = args_line[1];
                }

                if (line.contains("#real EC (%%)")){
                    String[] args_line = line.split("=");
                    r_EC_per = args_line[1];
                }

                if (line.contains("#virtrual EC")){
                    String[] args_line = line.split("=");
                    v_EC = args_line[1];
                }

                if (line.contains("Vertex Balance JSD")){
                    String[] args_line = line.split("=");
                    JSD = args_line[1];
                }

                if (line.contains("Sampling Time")){
                    String[] args_line = line.split("=");
                    ST = args_line[1];
                }


                // read next line
                line = reader.readLine();
            }

            csv_line = GRAPH + "," + SAMPLING_TYPE + "," +  TAU + "," + SIGMA + "," +
                    BETA + "," + numberOfPartitions +"," + Time +
                    "," + Score +"," + Migrations +"," + TotalLP +
                    "," + r_MNU +"," + v_MNU +"," + r_EC +
                    "," + r_EC_per +"," + v_EC +"," + JSD +
                    "," + ST +"\n";

            myWriter.write(csv_line);

            reader.close();
            myWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println(file);
        */


        long stopTime = System.currentTimeMillis();
        long elapsedTime = stopTime - startTime;
        System.out.println(elapsedTime/1000);
    }

}
