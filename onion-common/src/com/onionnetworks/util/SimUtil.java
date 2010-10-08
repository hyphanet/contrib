package com.onionnetworks.util;

public class SimUtil {

    /**
     * Prints a command to be redirected to a file, and run as a shell script
     * for invoking the GNU plotutils "graph" command.
     */
    public static final void printGraphCommand(String title, String x,
                                               String y, int[][] plots, 
                                               String[] graphNames) {
        System.out.println("#!/bin/sh");
        System.out.println("# By default (no args) it will display it in X, "+
                           "use $1=gif,png,for images");
        System.out.println("if [ -n \"$1\" ]");
        System.out.println("then type=$1");
        System.out.println("else type=\"X\"");
        System.out.println("fi");

        System.out.print("echo \"");
        for (int i=0;i<plots.length;i++) {
            for (int j=0;j<plots[i].length;j++) {
                System.out.print(plots[i][j] + " ");
            }
            System.out.println("\n"); //blank line
        }
        System.out.print("\" | graph -W .003 -C -T $type -L \""+title+
                         "\" -X \""+x+"\" -Y \""+y+"\"");
    }


    /**
     * Yes, this could be one line, but this really needs to be readable
     * as an error in this method could really screw things up.
     */
    public static final int getMedian(int[] data) {
        // do a stupid bubble sort then pick middle.
        // FIX, this sort doesn't have to suck.
        for (int i=0;i<data.length-1;i++) {
            for (int j=i+1;j<data.length;j++) {
                if (data[i] > data[j]) {
                    swap(data,i,j);
                }
            }
        }
        for (int i=0;i<data.length;i++) {
            System.err.print(data[i]+" ");
        }
        System.err.println();
        return data[data.length / 2];
    }

    public static final void swap(int[] data, int posA, int posB) {
        int tmp = data[posA];
        data[posA] = data[posB];
        data[posB] = tmp;
    }
}
    
