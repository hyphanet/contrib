package junit.xrunners;
import junit.framework.*;

/**
 * A simple program that runs any tests found in a list of classes.
 *
 * This is not interface compatible with any of the (more advanced) actual
 * JUnit test runners, wherefore the package name.
 *
 * @author oskar
 */

public class SimpleTestRunner {

    /**
     * Assumes args is a list of classes, and iterates through it, running
     * all tests for any that are subclasses of junit.framework.TestCase.
     */
    public static void main(String[] args) {
        try {
            TestSuite ts = new TestSuite("SimpleTestRunner suite");
            for (int i = 0 ; i < args.length ; i++) {
                try {
                    Class cl = Class.forName(args[i]);
                    if (TestCase.class.isAssignableFrom(cl)) {
                        ts.addTestSuite(cl);
                    }
                } catch (ClassNotFoundException e) {
                    System.out.println("Class " + args[i] + " not found.");
                }
            }
            TestResult tr = new TestResult();
            TestListener tl = new SimpleTestListener();
            tr.addListener(tl);
            ts.run(tr);
            if (!tr.wasSuccessful()) {
                System.exit(1);
            }
        } catch (Exception e) {
            e.printStackTrace(System.out);
            System.exit(1);
        }
    }

    private static class SimpleTestListener implements TestListener {
        
        public void addError(Test test, Throwable t) {
            System.out.println("Test \"" + test + "\" failed do to error: " + t);
            t.printStackTrace(System.out);
        }

        public void addFailure(Test test, AssertionFailedError afe) {
            System.out.println("Test \"" + test + "\" failed do to assertion: " 
                              + afe.getMessage());
        }

        public void endTest(Test test) {
            System.out.println("Test \"" + test + "\" ended");
        }
        
        public void startTest(Test test) {
            System.out.println("Test \"" + test + "\" started");
        }
    }
}
