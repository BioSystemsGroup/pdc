/*
 * Copyright 2015-2016 - Regents of the University of California, San
 * Francisco.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package pdc;

import java.io.File;
import java.util.List;
import java.util.ArrayList;
import difflib.DiffUtils;
import difflib.Patch;
import ec.util.ParameterDatabase;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class PDC {

  public static void main(String[] args) {
    CommandLineParser parser = new DefaultParser();

    Options options = new Options();
    options.addOption( "d0", "dir0", true, "The 1st configuration directory." );
    options.addOption( "d1", "dir1", true, "The 2nd configuration directory." );

    CommandLine cla = null;
    try {
      cla = parser.parse(options, args);
    } catch (ParseException exp) {
      System.out.println( "Unexpected exception:" + exp.getMessage() ); 
    }

    if (cla == null || !cla.hasOption("dir0") || !cla.hasOption("dir1")) 
      printUsage();
    
    String[] dn = new String[2];
    dn[0] = cla.getOptionValue("dir0");
    dn[1] = cla.getOptionValue("dir1");
    
    ArrayList<ArrayList> files = buildFileList(dn, ".properties");
    ArrayList<ArrayList> jsFiles = buildFileList(dn, ".js");
    files.get(0).addAll(jsFiles.get(0));
    files.get(1).addAll(jsFiles.get(1));
    parseProperties(dn, files);
  }

  private static ArrayList<ArrayList> buildFileList(String[] dn, String suffix) {
    File[] dirs = new File[dn.length]; // get dirs from names
    // get all the .properties files in each dir
    ArrayList<ArrayList> files = new ArrayList<>(2);
    for (int dNdx=0 ; dNdx<dn.length ; dNdx++) {
      dirs[dNdx] = new File(dn[dNdx]);
      File[] file_array = dirs[dNdx].listFiles((File dir, String name) -> (name.endsWith(suffix) ));
      ArrayList<File> file_list = new ArrayList(Arrays.asList(file_array));
      files.add(file_list);
    }
    return files;
  }
  
  public static void parseProperties(String[] dn, ArrayList<ArrayList> files) {
    // for each file in each dir, check for that file in the other dir
    for (int lNdx=0 ; lNdx< 2 ; lNdx++) {
      ArrayList<File> this_list = files.get(lNdx);
      for (File f : this_list) {
        ArrayList<File> that_list = files.get((lNdx+1)%2);
        File that_file = null;
        if ((that_file = listContainsTail(that_list, f.getName())) == null) 
          System.out.println(f+" only in "+dn[lNdx]);
        else if (lNdx==0) {
          File[] fn = {f, that_file};
          if (f.getName().contains("hepstructspec") || f.getName().contains(".js")) {
            diffTextFiles(fn);
          } else {
            diffPropertiesFiles(fn);
          }
        }
      }
    }
  }

  private static void diffTextFiles(File[] fn) {
    List<LinkedList<String>> lines  = new ArrayList<>();
    String line = "";
    for (int ndx=0 ; ndx<fn.length ; ndx++) {
      LinkedList<String> ls = new LinkedList<>();
      try {
        BufferedReader in = new BufferedReader(new FileReader(fn[ndx]));
        while ((line = in.readLine()) != null) {
          ls.add(line);
        }
      } catch (IOException e) { throw new RuntimeException(e); }
      lines.add(ls);
    }
    Patch patch = DiffUtils.diff(lines.get(0), lines.get(1));
    List<String> uList = DiffUtils.generateUnifiedDiff(fn[0].getPath(), fn[1].getPath(), lines.get(0), patch, 1);
    for (String s : uList) {
      System.out.println(s);
    }
  }
  
  public static File listContainsTail(ArrayList<File> l, String fn) {
    for (File f : l) {
      if (f.getName().equals(fn)) return f;
    }
    return null;
  }
  
  public static void diffPropertiesFiles(File[] fn) {
    boolean diffsFound = false;
    StringBuffer output = new StringBuffer("--- "+fn[0]+"\n+++ "+fn[1]);
    ParameterDatabase[] pd = new ParameterDatabase[2];
    try {
      for (int ndx=0 ; ndx<2 ; ndx++)
        pd[ndx] = new ParameterDatabase( new FileInputStream(fn[ndx]));
    } catch (IOException ioe) {
      System.err.println( ioe.getMessage() );
      System.exit( -1 );
    }
    for (int ndx=0 ; ndx<2 ; ndx++) {
      for (java.util.Map.Entry<Object,Object> me : pd[ndx].entrySet()) {
        String thisKey = (String)me.getKey();
        Object thisVal = me.getValue();
        ParameterDatabase that = pd[(ndx+1)%2];
        if (!that.exists(new ec.util.Parameter((String)me.getKey()), null)) {
          output.append(me.getKey().toString() + ":  only in file "+ndx+"\n");
          diffsFound = true;
        } else if (ndx==0) {
          Object thatVal = that.getProperty(thisKey);
          if (!thisVal.equals(thatVal)) {
            output.append(thisKey+":\n- "+thisVal+"\n+ "+thatVal+"\n");
            diffsFound = true;
          }
        }
      }
    }
    if (diffsFound) System.out.println(output.toString());
  }
  
  static void printUsage() {
    System.out.println("Usage: java pdc.PDC -d0 <1st cfg dir> -d1 <2nd cfg dir>");
    System.exit(-1);
  }
}
