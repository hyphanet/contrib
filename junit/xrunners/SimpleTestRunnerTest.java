package junit.xrunners;
import junit.framework.*;


public class SimpleTestRunnerTest extends TestCase {

    public String test;

    public SimpleTestRunnerTest(String name) {
        super(name);
    }

    public void setUp() {
        assertNull("tearDone worked",test);
        test = "testing";
    }

    public void tearDown() {
        assertNotNull("setUp worked",test);
        test = null;
    }

    public void testEquals() {
        assertEquals("Positive 1",1,1);
        assertEquals("0",0,0);
        assertEquals("Negative 1",-1,-1);
        assertEquals("String object","Hello", "Hello");
    }

    public void testSame() {
        assertSame(this,this);
    }

    public void testFailure() {
        TestCase tc = new FailureCase();
        TestResult tr = tc.run();
        assertTrue("Testing that failure was noted by testresult.",
                   !tr.wasSuccessful());
        assertEquals("Testing failure count",tr.failureCount(),1);
        assertEquals("Testing error count",tr.errorCount(),0);
        TestFailure tf = (TestFailure) tr.failures().nextElement();
        assertSame("Testing that recorded test is that which failed",
                   tf.testFailed(),tc);
        assertTrue("Testing that exception instanceof AssertionFailedError",
                   tf.thrownException() instanceof AssertionFailedError);
    }

    private class FailureCase extends TestCase {
        public FailureCase() {
            super("Failure case");
        }

        public void runTest() throws Throwable {
            fail("This test always fails");
        }
    }

    public void testError() {
        ErrorCase tc = new ErrorCase();
        TestResult tr = tc.run();
        assertTrue("Testing that error was noted by testresult.",
                   !tr.wasSuccessful());
        assertEquals("Testing failure count",
                     tr.failureCount(),0);
        assertEquals("Testing error count: ",
                     tr.errorCount(),1);
        TestFailure tf = (TestFailure) tr.errors().nextElement();
        assertSame("Testing that recorded test is that which failed",
                   tf.testFailed(),tc);
        assertSame("Testing that exception is that thrown",
                   tf.thrownException(), tc.e);
    }

    private class ErrorCase extends TestCase {
        public Exception e;
        public ErrorCase() {
            super("Error case");
        }

        public void runTest() throws Throwable {
            e = new IllegalArgumentException("this test always throws an illegal argumentexception");
            throw e;
        }
    }

    public void testNull() {
        assertNull("Testing null", null);
        assertNotNull("Testing not null",this);
    }


}
