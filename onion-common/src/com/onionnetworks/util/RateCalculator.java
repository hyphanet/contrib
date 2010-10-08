// (c) Copyright 2000 Justin F. Chapweske
// (c) Copyright 2000 Ry4an C. Brase

package com.onionnetworks.util;

/**
 * This class allows you to easily compute rate for various events.  A weighted
 * floating average is used and all parameters can be tweaked to fine tune 
 * your rate calculations.
 * 
 * @author Justin F. Chapweske
 */ 
public class RateCalculator {

    public static final int DEFAULT_INTERVAL_LENGTH = 300; 
    public static final int DEFAULT_HISTORY_SIZE = 100;     
    public static final float DEFAULT_HISTORY_WEIGHT = .8f; 
    
    protected int intervalLength;  // Number of milliseconds in interval.
    protected int historySize;      // Number of intervals to keep track of.
    protected float historyWeight;  // Multipler/weight of old intervals.
    protected double[] history;      // old intervals.
    protected int historyPos;       // history is circular, this is index.
    protected long lastIntervalTime = -1;  // last interval cutoff.
    protected double currentIntervalEvents;  // Num events this interval.
    protected double totalEvents;
    protected double lastEstimatedEventCount;  // monotincally increasing.
    protected long lastPositiveUpdateTime = -1;
    protected long pauseTime = -1; // for pausing
    
    /**
     * Construct a new RateCalculator using the default values.
     */
    public RateCalculator() {
	this(DEFAULT_INTERVAL_LENGTH,DEFAULT_HISTORY_SIZE,
             DEFAULT_HISTORY_WEIGHT);
    }

    /** 
     * Construct a new RateCalculator.
     * @param intervalLength The number of millis in each interval.
     * @param historySize The number of intervals to keep track of.
     * @param historyWeight The multipler used to determine the relevence of 
     * older intervals.
     *
     * Using a small historySize and/or a low historyWeight will cause the rate
     * to fit tightly against the current rate.  Using higher values allows
     * the rate to be much smoother.  If this is being used for direct UI it
     * is usually nice to have a frequently updated smooth rate.
     */
    public RateCalculator(int intervalLength, int historySize, 
                          float historyWeight) {

	this.intervalLength = intervalLength;
	this.historySize = historySize;
	this.historyWeight = historyWeight;

        history = new double[historySize];
        for (int i=0; i<history.length;i++) {
            history[i]=-1;
        }
    }

    public void pause() {
	pause(System.currentTimeMillis());
    }

    /**
     * Pauses the RateCalculator.  The RateCalculator will be resumed with the
     * next call to resume(). It is not advised to update events during the
     * paused period.
     */
    public void pause(long time) {
	if (pauseTime != -1) {
	    throw new IllegalStateException("RateCalculator already paused");
	}
	pauseTime = time;
    }

    /**
     * @return true if the RateCalculator is paused
     */
    public boolean isPaused() {
	return pauseTime != -1;
    }

    /**
     * Resumes the paused RateCalculator.
     */
    public void resume() {
	resume(System.currentTimeMillis());
    }

    public void resume(long time) {
	if (pauseTime == -1) {
	    throw new IllegalStateException("RateCalculator not paused");
	}
	update(0,time);
	pauseTime = -1;
    }
    
    /**
     * This is the preferred way to update the number of events for rate
     * calculation, it will simply use the time of the call to keep track of
     * when the events occured.
     */
    public void update(double numEvents) {
        update(numEvents,System.currentTimeMillis());
    }


    /**
     * If you wish to specify exactly the time at which the events occured you
     * can use this method.  It is very important that subsequent calls to
     * update have eventTime's monotonically increasing.
     */
    public void update(double numEvents, long eventTime) { 
	currentIntervalEvents += numEvents;
	totalEvents += numEvents;
        
	// paused
	if (pauseTime != -1) {
	    if (lastIntervalTime != -1) {
		lastIntervalTime += (eventTime-pauseTime);
	    }
	    if (lastPositiveUpdateTime != -1) {
		lastPositiveUpdateTime += (eventTime-pauseTime);
	    }
	    pauseTime = eventTime;
	}

        // first interval.
	if (lastIntervalTime == -1) {
	    lastIntervalTime = eventTime;
	    lastPositiveUpdateTime = eventTime;
            return;
        }
	
	if (numEvents > 0) {
	    lastPositiveUpdateTime = eventTime;
	}

        long deltaTime = eventTime-lastIntervalTime;

        if (deltaTime >= intervalLength) {
	    history[historyPos] = currentIntervalEvents / (double) deltaTime;
	    historyPos = (historyPos+1) % history.length; // circular
	    lastIntervalTime = eventTime;
	    currentIntervalEvents = 0;
	}
    }

    public double getRate() {
        return getRate(System.currentTimeMillis());
    }

    /**
     * If you are specifying a time with update(long time) then you must
     * specify the time at which you wish to view the rate for, this time
     * must be greater than the last time that was passed to the update
     * call.  This method DOES NOT allow you to see what the rate was at
     * during a previous interval.
     */
    public double getRate(long time) {
	update(0,time);
	double rate=0,total=0,weight=1;
	for (int i=history.length-1;i>=0;i--) {
	    double intervalRate = history[(historyPos + i) % history.length];

	    if (intervalRate == -1) { 
                continue; 
            }	    

	    rate += intervalRate*weight;
	    total += weight;
	    weight *= historyWeight;
	}

	if (total ==0 && rate == 0) {
	    return currentIntervalEvents / ((double)(time-lastIntervalTime+1));
	}
        
	return rate/total;
    }

    public double getEstimatedEventCount(double maxEvents) {
        return getEstimatedEventCount(maxEvents,System.currentTimeMillis());
    }
    
    public double getEstimatedEventCount(double maxEvents, long time) {
	double rate = getRate(time);
	long deltaTime = time-lastPositiveUpdateTime;
	double estimatedEventCount = totalEvents + (deltaTime * rate);

	return estimatedEventCount;
    }
    
    public long getEstimatedTimeRemaining(double maxEvents) {
	return getEstimatedTimeRemaining(maxEvents,
					 System.currentTimeMillis());
    }
      
    public long getEstimatedTimeRemaining(double maxEvents, long time) {
	double rate = getRate(time);
	double estimatedEventCount = getEstimatedEventCount(maxEvents,time);
	return (long) ((maxEvents-estimatedEventCount)/rate);
    }
}
