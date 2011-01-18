package com.onionnetworks.util;

import java.text.ParseException;

/**
 * This class represents a range of integers (incuding positive and negative 
 * infinity).
 */
public class Range {

    private boolean negInf, posInf;
    private long min,max;
   
    /**
     * Creates a new Range that is only one number, both min and max will
     * equal that number.
     * @param num The number that this range will encompass.
     */
    public Range(long num) {
        this(num,num,false,false);
    }

    /**
     * Creates a new Range from min and max (inclusive)
     * @param min The min value of the range.
     * @param max The max value of the range.
     */
    public Range(long min, long max) {
        this(min,max,false,false);
    }

    /**
     * Creates a new Range from min to postive infinity
     * @param min The min value of the range.
     * @param posInf Must be true to specify max == positive infinity
     */
    public Range(long min, boolean posInf) {
        this(min,Long.MAX_VALUE,false,posInf);
        if (!posInf) {
            throw new IllegalArgumentException("posInf must be true");
        }
    }

    /**
     * Creates a new Range from negative infinity to max.
     * @param negInf Must be true to specify min == negative infinity
     * @param max The max value of the range.
     */
    public Range(boolean negInf, long max) {
        this(Long.MIN_VALUE,max,negInf,false);
        if (!negInf) {
            throw new IllegalArgumentException("negInf must be true");
        }
    }

    /**
     * Creates a new Range from negative infinity to positive infinity.
     * @param negInf must be true.
     * @param posInf must be true.
     */
    public Range(boolean negInf, boolean posInf) {
        this(Long.MIN_VALUE,Long.MAX_VALUE,negInf,posInf);
        if (!negInf || !posInf) {
            throw new IllegalArgumentException
                ("negInf && posInf must be true");
        }
    }

    private Range(long min, long max, boolean negInf, boolean posInf) {
	if (min > max) {
	    throw new IllegalArgumentException
	      ("min cannot be greater than max");
	}
	// very common bug, its worth reporting for now.
	if (min == 0 && max == 0) {
	    System.err.println("Range.debug: 0-0 range detected. "+
			       "Did you intend to this? :");
	    new Exception().printStackTrace();
	}
        this.min = min;
        this.max = max;
        this.negInf = negInf;
        this.posInf = posInf;
    }
    
    /**
     * @return true if min is negative infinity.
     */
    public boolean isMinNegInf() {
        return negInf;
    }

    /**
     * @return true if max is positive infinity.
     */
    public boolean isMaxPosInf() {
        return posInf;
    }

    /**
     * @return the min value of the range.
     */
    public long getMin() {
	return min;
    }
    
    /**
     * @return the max value of the range.
     */
    public long getMax() {
	return max;
    }
    
    /**
     * @return The size of the range (min and max inclusive) or -1 if the range
     * is infinitly long.
     */
    public long size() {
        if (negInf || posInf) {
            return -1;
        }
        return max-min+1;
    }

    /**
     * @param i The integer to check to see if it is in the range.
     * @return true if i is in the range (min and max inclusive)
     */
    public boolean contains(long i) {
	return i >= min && i <= max;
    }
    
    /**
     * @param r The range to check to see if it is in this range.
     * @return true if this range contains the entirety of the passed range.
     */
    public boolean contains(Range r) {
	return r.min >= min && r.max <= max;
    }
    
    
    public int hashCode() {
	return (int) (min + 23 * max);
    }
    
    public boolean equals(Object obj) {
	if (obj instanceof Range &&
	    ((Range) obj).min == min && ((Range) obj).max == max &&
            ((Range) obj).negInf == negInf && ((Range) obj).posInf == posInf) {
	    return true;
	} else {
	    return false;
	}
    }
    
    public String toString() {
	if (!negInf && !posInf && min == max) {
	    return new Long(min).toString();
	} else {
	    return (negInf ? "(" : ""+min) + "-" + (posInf ? ")" : ""+max);
	}
    }
    
    /**
     * This method creates a new range from a String.
     * Allowable characters are all integer values, "-", ")", and "(".  The
     * open and closed parens indicate positive and negative infinity.
     * <pre>
     * Example strings would be:
     * "11" is the range that only includes 11
     * "-6" is the range that only includes -6
     * "10-20" is the range 10 through 20 (inclusive)
     * "-10--5" is the range -10 through -5
     * "(-20" is the range negative infinity through 20
     * "30-)" is the range 30 through positive infinity.
     * </pre>
     * @param s The String to parse
     * @return The resulting range
     * @throws ParseException, 
     */
    public static final Range parse(String s) throws ParseException {
        try {
            long min=0,max=0;
            boolean negInf=false,posInf=false;
            // search from the 1 pos because it may be a negative number.
            int dashPos = s.indexOf("-",1);
            if (dashPos == -1) { // no dash, one value.
                min = max = Long.parseLong(s);
            } else {
                if (s.indexOf("(") != -1) {
                    negInf = true;
                } else {
                    min = Long.parseLong(s.substring(0,dashPos));
                }
                if (s.indexOf(")") != -1) {
                    posInf = true;
                } else {
                    max = Long.parseLong(s.substring(dashPos+1,s.length()));
                }
            }
            if (negInf) {
                if (posInf) {
                    return new Range(true,true);
                } else {
                    return new Range(true,max);
                }
            } else if (posInf) {
                return new Range(min,true);
            } else {
                return new Range(min,max);
            }
        } catch (RuntimeException e) {
            throw new ParseException(e.getMessage(),-1);
        }
    }    
}
