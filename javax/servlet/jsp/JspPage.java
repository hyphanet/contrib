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
 
package javax.servlet.jsp;

import javax.servlet.*;

/**
 * This is the interface that a JSP processor-generated class must
 * satisfy.
 * <p>
 * The interface defines a protocol with 3 methods; only two of
 * them: jspInit() and jspDestroy() are part of this interface as
 * the signature of the third method: _jspService() depends on
 * the specific protocol used and cannot be expressed in a generic
 * way in Java.
 * <p>
 * A class implementing this interface is responsible for invoking
 * the above methods at the apropriate time based on the
 * corresponding Servlet-based method invocations.
 * <p>
 * The jspInit(0 and jspDestroy() methods can be defined by a JSP
 * author, but the _jspService() method is defined authomatically
 * by the JSP processor based on the contents of the JSP page.
 */

public interface JspPage extends Servlet {

    /**
     * Methods that can be DEFINED BY THE JSP AUTHOR
     * either directly (via a declaration) or via an event handler
     * (in JSP 1.1)
     */

    /**
     * jsp_init() is invoked when the JspPage is initialized.
     * At this point getServletConfig() will return the desired value.
     */
    public void jspInit();

    /**
     * jsp_destroy() is invoked when the JspPage is about to be destroyed.
     */
    public void jspDestroy();

    /**
     * service is the main service entry from the superclass.  It is 
     * responsible from determine if the protocol is valid and to call
     * into the appropriate _jspService(), after the appropriate casting.
     */

    /**
     * _jspService corresponds to the body of the JSP page.
     * This method is defined automatically by the JSP processor
     * and should NEVER BE DEFINED BY THE JSP AUTHOR
     *
     * The specific signature depends on the protocol supported by the JSP page.
     *
     * public void _jspService(<ServletRequestSubtype> request,
     *                             <ServletResponseSubtype> response)
     *        throws ServletException, IOException;
     */
}
