package com.onionnetworks.util;

import java.util.Random;

public class RateCalculatorTest {
    
    public static final int[] DOWNLOAD_RATES = new int[] {23,5,80};
    public static final int GROUP_SIZE = 32;
    public static final int MAX_GROUPS = 1000;
    
    public int eventCount;
    public int updates;
    public int[] counts = new int[DOWNLOAD_RATES.length];
    public RateCalculator rc;
    public Random rand = new Random();
    
    public RateCalculatorTest() throws Exception {
	rc = new RateCalculator(30000,100,.75f);
	new Thread(new Runnable() {
	    public void run() {
		while (true) {
		    try {
			Thread.sleep(100);
		    } catch (InterruptedException e) {
		    }
		    System.out.print
		      ("\revents="+eventCount+",est="+
		       (int) rc.getEstimatedEventCount
		       (MAX_GROUPS*GROUP_SIZE)+",updates="+
		       updates+",rate="+
		       (int) (rc.getRate()*1000)+",time="+
		       (int) (rc.getEstimatedTimeRemaining
		       (MAX_GROUPS*GROUP_SIZE)/1000)+"                 ");
		}
	    }
	}).start();

	while (eventCount < MAX_GROUPS*GROUP_SIZE) {
	    Thread.sleep(1000);
	    for (int i=0;i<DOWNLOAD_RATES.length;i++) {
		for (int j=0;j<DOWNLOAD_RATES[i];j++) {
		    counts[i]++;
		    eventCount++;
		    if (counts[i] == GROUP_SIZE) {
			rc.update(GROUP_SIZE);
			updates+=GROUP_SIZE;
			counts[i]=0;
		    }
		}
	    }
	}   
	System.exit(0);
    }
    
    public static final void main(String[] args) throws Exception {
	new RateCalculatorTest();
    }    
}
    
    
