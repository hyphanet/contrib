package junit.framework;

/**
 * These classes are a GPLed reimplementation of the junit framework
 * for Freenet. They are done directly from the junit 3.7 javadocs, 
 * http://www.junit.org/junit/javadoc/3.7/index.htm
 * and without studying the unit code (which is under the IBM Public License).
 *
 * @author oskar
 */

public interface Test {

    int countTestCases();

    void run(TestResult test);

}
