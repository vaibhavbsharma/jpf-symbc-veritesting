package gov.nasa.jpf.symbc.veritesting;

import gov.nasa.jpf.symbc.VeritestingListener;

import java.io.*;
import java.util.concurrent.TimeUnit;

public class QueryWithZ3Binary {

    public static String runCommand(String command) throws IOException, InterruptedException {
        String ret = new String();
        Runtime r = Runtime.getRuntime();
        Process p = r.exec(command);
        p.waitFor(VeritestingListener.filterUnsatTimeout, TimeUnit.SECONDS);
        BufferedReader b = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line = "";

        while ((line = b.readLine()) != null) {
            System.out.println(line);
            ret += line;
        }

        b.close();
        return ret;
    }

    public static String runQuery(String query) throws IOException, InterruptedException {
        File file = new File("./1.smt2");

        //Create the file
        if (file.createNewFile()){
            System.out.println("File is created!");
        }else{
            System.out.println("File already exists.");
        }

        //Write Content
        FileWriter writer = new FileWriter(file);
        writer.write(query);
        writer.close();

        return runCommand(VeritestingListener.pathToZ3Binary + " ./1.smt2");
    }
}
