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

public class PDC {

  public static void main(String[] args) {
    File[] dirs = new File[2];
    if (!testArgs(dirs,args))  printUsage();
    
    String[] dn = new String[2];
    dn[0] = args[0];
    dn[1] = args[1];
    
    ArrayList<ArrayList> files = buildFileList(dirs, ".properties");
    ArrayList<ArrayList> jsFiles = buildFileList(dirs, ".js");
    files.get(0).addAll(jsFiles.get(0));
    files.get(1).addAll(jsFiles.get(1));
    ArrayList<ArrayList> jsonFiles = buildFileList(dirs, ".json");
    files.get(0).addAll(jsonFiles.get(0));
    files.get(1).addAll(jsonFiles.get(1));
    parseProperties(dn, files);
  }
  
  private static boolean testArgs(File[] fl, String[] a) {
    if (a.length <= 0) return false;
    for (int aNdx=0 ; aNdx<a.length ; aNdx++) {
      if (a[aNdx] != null) fl[aNdx] = new File(a[aNdx]);
      else {
        return false;
      }
      if (!fl[aNdx].exists()) {
        System.err.println(a[aNdx]+" does not exist.");
        return false;
      }
      if (!fl[aNdx].isDirectory()) {
        System.err.println(a[aNdx]+" is not a directory.");
        return false;
      }
    }
    return true;
  }

  private static ArrayList<ArrayList> buildFileList(File[] dn, String suffix) {
    // get all the .properties files in each dir
    ArrayList<ArrayList> files = new ArrayList<>(2);
    for (int dNdx=0 ; dNdx<dn.length ; dNdx++) {
      File[] file_array = dn[dNdx].listFiles((File dir, String name) -> (name.endsWith(suffix) ));
      ArrayList<File> file_list = new ArrayList(Arrays.asList(file_array));
      file_list.sort(null);
      files.add(file_list);
    }
    return files;
  }
  
  private static void parseProperties(String[] dn, ArrayList<ArrayList> files) {
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
          if (f.getName().contains("hepstructspec") || f.getName().endsWith(".js")) {
            diffTextFiles(fn);
          } else if (f.getName().endsWith(".properties")) {
            diffPropertiesFiles(fn);
          } else { // .json files
            diffJSONFiles(fn);
          }
          
        }
      }
    }
  }

  private static void diffJSONFiles(File[] fn) {
    boolean foundDiffs=false;
    StringBuffer output = new StringBuffer("--- "+fn[0].getPath()+"\n+++ "+fn[1].getPath()+"\n");
    javax.json.JsonReader thisReader = null, thatReader = null;
    try {
      thisReader = javax.json.Json.createReader(new FileReader(fn[0]));
      thatReader = javax.json.Json.createReader(new FileReader(fn[1]));
    } catch (java.io.FileNotFoundException fnfe) { throw new RuntimeException(fnfe); }
    
    javax.json.JsonObject thisObj = thisReader.readObject();
    javax.json.JsonObject thatObj = thatReader.readObject();
    
    for (java.util.Map.Entry me : thisObj.entrySet()) {
      javax.json.JsonValue thisV = (javax.json.JsonValue) me.getValue();
      javax.json.JsonValue thatV = (javax.json.JsonValue) thatObj.get(me.getKey());
      if (thisV instanceof javax.json.JsonString 
              || thisV instanceof javax.json.JsonNumber
              || thisV instanceof javax.json.JsonArray) {
        if (!thisV.equals(thatV)) {
          output.append("- "+me.getKey() + " = "+thisV+"\n+ "+me.getKey()+ " = " + thatV+"\n");
          foundDiffs = true;
        }
      } else if (thisV instanceof javax.json.JsonStructure) {
        javax.json.JsonStructure thisA = (javax.json.JsonStructure) thisV;
        javax.json.JsonStructure thatA = (javax.json.JsonStructure) thatV;
        if (!thisA.equals(thatA)) {
          String thisS = thisV.toString();
          String thatS = thatV.toString();
          int diffNdx = indexOfDifference(thisS, thatS);
          
          int begin = 0;
          int nextBracket = thisS.substring(diffNdx,thisS.length()).indexOf("}")+1;
          int prevBracket = thisS.substring(0,diffNdx).lastIndexOf("{");
          int prevComma = thisS.substring(0,diffNdx).lastIndexOf(",");
          //if no previous comma, at the first entry, go back to previous bracket
          //if comma to diff contains dCV, go back 2 commas
          //if not go back 1 comma
          if (prevComma < 0) {
            begin = prevBracket+1;
          } else if (thisS.substring(prevComma, diffNdx).contains("dPV")) {
            begin = thisS.substring(0,prevComma).lastIndexOf(",")+1;
          } else {
            begin = prevComma+1;
          }
          int end = diffNdx+nextBracket;
          //output.append("begin = "+begin+", end = "+end+"\n");
          output.append("- "+me.getKey() + " ...first diff... "+thisS.substring(begin, end) +" ...\n+ "+me.getKey()+ " ...first diff... " + thatS.substring(begin, end) +" ...\n");
          foundDiffs = true;
        }
      }
    }
    if (foundDiffs) System.out.println(output.toString());
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
  
  private static File listContainsTail(ArrayList<File> l, String fn) {
    for (File f : l) {
      if (f.getName().equals(fn)) return f;
    }
    return null;
  }
  
  private static void diffPropertiesFiles(File[] fn) {
    boolean diffsFound = false;
    StringBuffer output = new StringBuffer("--- "+fn[0]+"\n+++ "+fn[1]+"\n");
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
          output.append(me.getKey().toString() + ":  only in "+fn[ndx].getParent()+"\n");
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
    System.out.println("Usage: java pdc.PDC <1st cfg dir> <2nd cfg dir>");
    System.exit(-1);
  }
  
    /**
     * NOTICE
     * below is copied from: https://commons.apache.org/proper/commons-lang/
     * LICENSE: http://www.apache.org/licenses/LICENSE-2.0
     */
  
    /**
     * Represents a failed index search.
     * @since 2.1
     */
    public static final int INDEX_NOT_FOUND = -1;
    /** 
     * <p>Compares two CharSequences, and returns the index at which the
     * CharSequences begin to differ.</p>
     *
     * <p>For example,
     * {@code indexOfDifference("i am a machine", "i am a robot") -> 7}</p>
     *
     * <pre>
     * StringUtils.indexOfDifference(null, null) = -1
     * StringUtils.indexOfDifference("", "") = -1
     * StringUtils.indexOfDifference("", "abc") = 0
     * StringUtils.indexOfDifference("abc", "") = 0
     * StringUtils.indexOfDifference("abc", "abc") = -1
     * StringUtils.indexOfDifference("ab", "abxyz") = 2
     * StringUtils.indexOfDifference("abcde", "abxyz") = 2
     * StringUtils.indexOfDifference("abcde", "xyz") = 0
     * </pre>
     *
     * @param cs1  the first CharSequence, may be null
     * @param cs2  the second CharSequence, may be null
     * @return the index where cs1 and cs2 begin to differ; -1 if they are equal
     * @since 2.0
     * @since 3.0 Changed signature from indexOfDifference(String, String) to
     * indexOfDifference(CharSequence, CharSequence)
     */
    public static int indexOfDifference(final CharSequence cs1, final CharSequence cs2) {
        if (cs1 == cs2) {
            return INDEX_NOT_FOUND;
        }
        if (cs1 == null || cs2 == null) {
            return 0;
        }
        int i;
        for (i = 0; i < cs1.length() && i < cs2.length(); ++i) {
            if (cs1.charAt(i) != cs2.charAt(i)) {
                break;
            }
        }
        if (i < cs2.length() || i < cs1.length()) {
            return i;
        }
        return INDEX_NOT_FOUND;
    }
}
