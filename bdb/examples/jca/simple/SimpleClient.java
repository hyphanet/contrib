package jca.simple;

import javax.naming.InitialContext;

public class SimpleClient {

    public static void main(String args[])
	throws Exception {

	InitialContext iniCtx = new InitialContext();
	Object ref = iniCtx.lookup("SimpleBean");
	SimpleHome home = (SimpleHome) ref;
	Simple simple = home.create();
	System.out.println("Created Simple");
	simple.put(args[0], args[1]);
	System.out.println("Simple.get('" + args[0] + "') = " +
			   simple.get(args[0]));
	simple.removeDatabase();
	System.out.println("simple.removeDatabase()");
    }
}
