/*
 * Copyright 2015 - Regents of the University of California, San
 * Francisco.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package pdc;

import ec.util.ParameterDatabase;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class PDC {

  public static void main(String[] args) {
    CommandLineParser parser = new DefaultParser();

    Options options = new Options();
    options.addOption( "f0", "file0", true, "The 1st parameter file." );
    options.addOption( "f1", "file1", true, "The 2nd parameter file." );

    CommandLine cla = null;
    try {
      cla = parser.parse(options, args);
    } catch (ParseException exp) {
      System.out.println( "Unexpected exception:" + exp.getMessage() ); 
    }

    if (cla == null || !cla.hasOption("file0") || !cla.hasOption("file1")) 
      printUsage();
    
    String[] fn = new String[2];
    fn[0] = cla.getOptionValue("file0");
    fn[1] = cla.getOptionValue("file1");
    System.out.println("Parsing "+fn[0]+" and "+fn[1]);
    ParameterDatabase[] pd = new ParameterDatabase[2];
    try {
      for (int ndx=0 ; ndx<2 ; ndx++)
        pd[ndx] = new ParameterDatabase( new java.io.FileInputStream(new java.io.File(fn[ndx])));
    } catch (java.io.IOException ioe) {
      System.err.println( ioe.getMessage() );
      System.exit( -1 );
    }
    for (int ndx=0 ; ndx<2 ; ndx++) {
      for (java.util.Map.Entry<Object,Object> me : pd[ndx].entrySet()) {
        String thisKey = (String)me.getKey();
        Object thisVal = me.getValue();
        ParameterDatabase that = pd[(ndx+1)%2];
        if (!that.exists(new ec.util.Parameter((String)me.getKey()), null)) System.out.println(me.getKey().toString() + ":  only in file "+ndx);
        else {
          Object thatVal = that.getProperty(thisKey);
          if (!thisVal.equals(thatVal)) System.out.print(thisKey+":\n - "+thisVal+"\n + "+thatVal+"\n");
        }
      }
    }
  }
  
  static void printUsage() {
    System.out.println("Usage: java pdc.PDC -f1 <1st parameter file> -f2 <2nd parameter file>");
    System.exit(-1);
  }
}
