package junit.framework;
import java.util.Vector;
import java.util.Enumeration;
/**
 * These classes are a GPLed reimplementation of the junit framework
 * for Freenet. They are done directly from the junit 3.7 javadocs, 
 * http://www.junit.org/junit/javadoc/3.7/index.htm
 * and without studying the unit code (which is under the IBM Public License).
 *
 * @author oskar
 */

public class TestResult {

    private boolean stopped = false;;

    protected Vector fErrors;
    protected Vector fFailures;
    protected Vector fListeners;
    protected Vector fRunTests;

    public TestResult() {
        fErrors = new Vector();
        fFailures = new Vector();
        fListeners = new Vector();
        fRunTests = new Vector();
    }

    public synchronized void addError(Test t, Throwable thrown) {
        fErrors.addElement(new TestFailure(t, thrown));
        for (Enumeration e = fListeners.elements() ; e.hasMoreElements();) {
            ((TestListener) e.nextElement()).addError(t, thrown);
        }
    }

    public synchronized void addFailure(Test t, AssertionFailedError afe) {
        fFailures.addElement(new TestFailure(t, afe));
        for (Enumeration e = fListeners.elements() ; e.hasMoreElements();) {
            ((TestListener) e.nextElement()).addFailure(t, afe);
        }
    }
    
    public synchronized void addListener(TestListener l) {
        fListeners.addElement(l);
    }

    public synchronized void endTest(Test t) {
        fRunTests.addElement(t);
        for (Enumeration e = fListeners.elements() ; e.hasMoreElements();) {
            ((TestListener) e.nextElement()).endTest(t);
        }
    }

    public int errorCount() {
        return fErrors.size();
    }

    /**
     * I'm guessing that this is supposed to be an enumeration of
     * TestFailure objects.
     */
    public Enumeration errors() {
        return fErrors.elements();
    }

    public int failureCount() {
        return fFailures.size();
    }

    /**
     * I'm guessing that this is supposed to be an enumeration of
     * TestFailure objects.
     */
    public Enumeration failures() {
        return fFailures.elements();
    }

    public synchronized void removeListener(TestListener tl) {
        fListeners.removeElement(tl);
    }

    /**
     * Guessing this as tc.run(this)
     */
    public void run(TestCase tc) {
        tc.run(this);
    }

    public int runCount() {
        return fRunTests.size();
    }

    /**
     * This isn't very well documented, but I'm guessing we should run
     * p.protect(), catch any Throwables and log them as errors belonging to
     * test.
     */
    public void runProtected(Test test, Protectable p) {
        try {
            p.protect();
        } catch (Throwable t) {
            addError(test, t);
        }
    }

    /**
     * No clue what this is. Guessing.
     * @return true unless stop() has been called.
     */
    public synchronized boolean shouldStop() {
        return stopped;
    }

    public synchronized void startTest(Test t) {
        fRunTests.addElement(t);
        for (Enumeration e = fListeners.elements() ; e.hasMoreElements();) {
            ((TestListener) e.nextElement()).startTest(t);
        }
    }

    /**
     * No clue about this.
     */
    public synchronized void stop() {
        stopped = true;
    }

    public boolean wasSuccessful() {
        return fFailures.isEmpty() && fErrors.isEmpty();
    }

    /**
     * @deprecated
     */
    public int runTests() {
        return runCount();
    }

    /**
     * @deprecated
     */
    public int testErrors() {
        return errorCount();
    }

    /**
     * @deprecated
     */
    public int testFailures() {
        return failureCount();
    }

}
