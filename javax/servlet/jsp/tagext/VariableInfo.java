/*
 * The Apache Software License, Version 1.1
 *
 * Copyright (c) 1999 The Apache Software Foundation.  All rights 
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer. 
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution, if
 *    any, must include the following acknowlegement:  
 *       "This product includes software developed by the 
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowlegement may appear in the software itself,
 *    if and wherever such third-party acknowlegements normally appear.
 *
 * 4. The names "The Jakarta Project", "Tomcat", and "Apache Software
 *    Foundation" must not be used to endorse or promote products derived
 *    from this software without prior written permission. For written 
 *    permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache"
 *    nor may "Apache" appear in their names without prior written
 *    permission of the Apache Group.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */ 
 
package javax.servlet.jsp.tagext;

/**
 * Information on the scripting variables that are created/modified by
 * a tag (at run-time); this information is provided by TagExtraInfo
 * classes and it is used by the translation phase of JSP.
 *
 */

public class VariableInfo {
    /**
     * Different types of scope for an scripting variable introduced
     * by this action
     *
     * <pre>
     * NESTED ==> variable is visible only within the start/end tags
     * AT_BEGIN ==> variable is visible after start tag
     * AT_END ==> variable is visible after end tag
     * </pre>
     */
    public static final int NESTED = 0;
    public static final int AT_BEGIN = 1;
    public static final int AT_END = 2;


    /**
     * Constructor
     * These objects can be created (at translation time) by the TagExtraInfo
     * instances.
     *
     * @param id The name of the scripting variable
     * @param className The name of the scripting variable
     * @param declare If true, it is a new variable (in some languages this will
     * require a declaration)
     * @param scope Indication on the lexical scope of the variable
     */

    public VariableInfo(String varName,
			String className,
			boolean declare,
			int scope) {
	this.varName = varName;
	this.className = className;
	this.declare = declare;
	this.scope = scope;
    }

    // Accessor methods
    public String getVarName() { return varName; }
    public String getClassName() { return className; }
    public boolean getDeclare() { return declare; }
    public int getScope() { return scope; }



    // == private data
    private String varName;
    private String className;
    private boolean declare;
    private int scope;
}

