package junit.framework;

/**
 * These classes are a GPLed reimplementation of the junit framework
 * for Freenet. They are done directly from the junit 3.7 javadocs, 
 * http://www.junit.org/junit/javadoc/3.7/index.htm
 * and without studying the unit code (which is under the IBM Public License).
 *
 * @author oskar
 */

public class Assert {

    protected Assert() {
    }

    /**
     * @deprecated
     */
    public static void assert(boolean condition) {
        if (!condition)
            throw new AssertionFailedError();
    }

    /**
     * @deprecated
     */
    public static void assert(String message, boolean condition) {
        if (!condition)
            throw new AssertionFailedError(message);
    }

    public static void assertEquals(boolean expected, boolean actual) {
        if (!(expected && actual) && (actual || expected))
            throw new AssertionFailedError(expected + " != " + actual);
    }

    public static void assertEquals(String message, boolean expected, 
                                    boolean actual) {
        if (!(expected && actual) && (actual || expected))
            throw new AssertionFailedError(message);
    }

    public static void assertEquals(byte expected, byte actual) {
        if (expected != actual)
            throw new AssertionFailedError(expected + " != " + actual);
    }

    public static void assertEquals(String message, byte expected, 
                                    byte actual) {
        if (expected != actual)
            throw new AssertionFailedError(message);
    }

    public static void assertEquals(char expected, char actual) {
        if (expected != actual)
            throw new AssertionFailedError(expected + " != " + actual);
    }

    public static void assertEquals(String message, char expected, char actual) {
        if (expected != actual)
            throw new AssertionFailedError(message);
    }

    public static void assertEquals(double expected, double actual, 
                                    double delta) {
        if (Math.abs(expected - actual) > delta)
            throw new AssertionFailedError(expected + " != " + actual + 
                                               " within " + delta);
    }

    public static void assertEquals(String message, double expected, 
                                    double actual, double delta) {
        if (Math.abs(expected - actual) > delta)
            throw new AssertionFailedError(message);
    }

    public static void assertEquals(float expected, float actual, 
                                    double delta) {
        if (Math.abs(expected - actual) > delta)
            throw new AssertionFailedError(expected + " != " + actual + 
                                               " within " + delta);
    }

    public static void assertEquals(String message, float expected, 
                                    float actual, double delta) {
        if (Math.abs(expected - actual) > delta)
            throw new AssertionFailedError(message);
    }

    public static void assertEquals(int expected, int actual) {
        if (expected != actual)
            throw new AssertionFailedError(expected + " != " + actual);
    }

    public static void assertEquals(String message, int expected, int actual) {
        if (expected != actual)
            throw new AssertionFailedError(message);
    }

    public static void assertEquals(long expected, long actual) {
        if (expected != actual)
            throw new AssertionFailedError(expected + " != " + actual);
    }

    public static void assertEquals(String message, long expected, 
                                    long actual) {
        if (expected != actual)
            throw new AssertionFailedError(message);
    }

    public static void assertEquals(Object expected, Object actual) {
        if (expected.equals(actual))
            throw new AssertionFailedError(expected + " != " + actual);
    }

    public static void assertEquals(String message, Object expected, 
                                    Object actual) {
        if (expected.equals(actual))
            throw new AssertionFailedError(message);
    }

    public static void assertEquals(short expected, short actual) {
        if (expected != actual)
            throw new AssertionFailedError(expected + " != " + actual);
    }

    public static void assertEquals(String message, short expected, 
                                    short actual) {
        if (expected != actual)
            throw new AssertionFailedError(message);
    }

    public static void assertNotNull(Object o) {
        if (o == null)
            throw new AssertionFailedError("Object null");
    }

    public static void assertNotNull(String m, Object o) {
        if (o != null)
            throw new AssertionFailedError(m);
    }

    public static void assertNull(Object o) {
        if (o != null)
            throw new AssertionFailedError("Object not null");
    }

    public static void assertNull(String m, Object o) {
        if (o != null)
            throw new AssertionFailedError(m);
    }

    public static void assertSame(Object expected, Object actual) {
        if (expected != actual)
            throw new AssertionFailedError(expected + " not same as " +
                                               actual);
    } 

    public static void assertSame(String message, Object expected, 
                                  Object actual) {
        if (expected != actual)
            throw new AssertionFailedError(message);
    }

    public static void assertTrue(boolean condition) {
        if (!condition)
            throw new AssertionFailedError("Assertion not true");
    }

    public static void assertTrue(String message, boolean condition) {
        if (!condition)
            throw new AssertionFailedError(message);
    }

    public static void fail() {
        throw new AssertionFailedError("Failed");
    }

    public static void fail(String m) {
        throw new AssertionFailedError(m);
    }

}
