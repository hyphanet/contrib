package junit.framework;
import java.util.Vector;
import java.util.Enumeration;
import java.lang.reflect.*;
/**
 * These classes are a GPLed reimplementation of the junit framework
 * for Freenet. They are done directly from the junit 3.7 javadocs, 
 * http://www.junit.org/junit/javadoc/3.7/index.htm
 * and without studying the unit code (which is under the IBM Public License).
 *
 * @author oskar
 */

public class TestSuite implements Test {

    private Vector tests;
    private String name;

    public TestSuite() {
        this("");
    }

    public TestSuite(Class c) {
        this("");
        addTestSuite(c);
    }

    public TestSuite(String name) {
        this.name = name;
        this.tests = new Vector();
    }

    
    public void addTest(Test t) {
        tests.addElement(t);
    }

    public void addTestSuite(Class c) {
        Constructor con;
        try {
            if (!TestCase.class.isAssignableFrom(c)) 
                throw new IllegalArgumentException("Class arg not TestCase");
            con = c.getConstructor(new Class[] { String.class });
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Class arg has no String " 
                                                + "constructor");
        }

        Method[] m = c.getMethods();
        for (int i = 0; i < m.length ; i++) {
            if(m[i].getParameterTypes().length == 0 &&
               m[i].getName().startsWith("test")) {
                try {
                    Object tc = 
                        con.newInstance(new Object[] {m[i].getName()});
                    tests.addElement(tc);
                } catch (InvocationTargetException e) {
                    // shit happens...
                } catch (InstantiationException e) {
                } catch (IllegalAccessException e) {
                }
            }
        }
    }

    public int countTestCases() {
        int res = 0;
        for (Enumeration e = tests.elements() ; e.hasMoreElements() ;) {
            res += ((Test) e.nextElement()).countTestCases();
        }
        return res;
    }

    public void run(TestResult tr) {
        for (Enumeration e = tests.elements() ; e.hasMoreElements() ;) {
            ((Test) e.nextElement()).run(tr);
        }
    }

    /**
     * What?
     */
    public void runTest(Test test, TestResult tr) {
        test.run(tr);
    }

    public Test testAt(int index) {
        return (Test) tests.elementAt(index);
    }

    public Enumeration tests() {
        return tests.elements();
    }

    public String toString() {
        return "TestSuite: " + name;
    }

    public void setName(String n) {
        this.name = n;
    }

    public String getName() {
        return name;
    }

}

