package junit.framework;
import java.lang.reflect.*;
/**
 * These classes are a GPLed reimplementation of the junit framework
 * for Freenet. They are done directly from the junit 3.7 javadocs, 
 * http://www.junit.org/junit/javadoc/3.7/index.htm
 * and without studying the unit code (which is under the IBM Public License).
 *
 * @author oskar
 */

public abstract class TestCase extends Assert implements Test {
    
    private String name;

    public TestCase(String name) {
        this.name = name;
    }
    
    public int countTestCases() {
        return 1;
    }

    protected TestResult createDefault() {
        return new TestResult();
    }

    public String getName() {
        return name;
    }

    /**
     * @deprecated
     */
    public String name() {
        return name;
    }

    public TestResult run() {
        TestResult defaul = new TestResult();
        run(defaul);
        return defaul;
    }

    public void run(TestResult tr) {
        try {
            tr.startTest(this);
            runBare();
        } catch (AssertionFailedError afe) {
            tr.addFailure(this, afe);
        } catch (Throwable t) {
            tr.addError(this, t);
        }
        tr.endTest(this);
    }

    public void runBare() throws Throwable {
        try {
            setUp();
            runTest();
        } finally {
            tearDown();
        }
    }

    public void setName(String name) {
        this.name = name;
    }

    protected void setUp() throws Exception {
    }

    protected void tearDown() throws Exception {
    }

    public String toString() {
        return getClass().getName() + ": " + name;
    }

    protected void runTest() throws Throwable {
        try {
            //System.err.println(this);
            Method m = this.getClass().getMethod(name, new Class[0]);
            //System.err.println(this.getClass() +" :    " + m);
            m.invoke(this, new Object[0]);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        } 
    }

}
