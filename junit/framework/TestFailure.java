package junit.framework;
import java.util.Vector;
/**
 * These classes are a GPLed reimplementation of the junit framework
 * for Freenet. They are done directly from the junit 3.7 javadocs, 
 * http://www.junit.org/junit/javadoc/3.7/index.htm
 * and without studying the unit code (which is under the IBM Public License).
 *
 * @author oskar
 */

public class TestFailure {

    protected Test fFailedTest;
    protected Throwable fThrownException;

    public TestFailure(Test failedTest, Throwable thrownException) {
        this.fFailedTest = failedTest;
        this.fThrownException = thrownException;
    } 

    public Test testFailed() {
        return fFailedTest;
    }

    public Throwable thrownException() {
        return fThrownException;
    }

    public String toString() {
        return fFailedTest + " failed with : " + fThrownException;
    }
}
