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

import java.io.Reader;
import java.io.Writer;
import java.io.IOException;
import javax.servlet.jsp.*;

/**
 * A JspWriter subclass that can be used to process body evaluations
 * so they can re-extracted later on.
 */

public abstract class BodyContent extends JspWriter {
    
    /**
     * Protected constructor.
     *
     * Unbounded buffer,  no autoflushing.
     */

    protected BodyContent(JspWriter e) {
	super(UNBOUNDED_BUFFER , false);
	this.enclosingWriter = e;
    }

    /**
     * Redefine flush().
     * It is not valid to flush.
     */

    public void flush() throws IOException {
	throw new IOException("Illegal to flush within a custom tag");
    }

    /**
     * Clear the body.
     */
    
    public void clearBody() {
	try {
	    this.clear();
	} catch (IOException ex) {
	    // TODO -- clean this one up.
	    throw new Error("internal error!;");
	}
    }

    /**
     * Return the value of this BodyContent as a Reader.
     * Note: this is after evaluation!!  There are no scriptlets,
     * etc in this stream.
     *
     * @returns the value of this BodyContent as a Reader
     */
    public abstract Reader getReader();

    /**
     * Return the value of the BodyContent as a String.
     * Note: this is after evaluation!!  There are no scriptlets,
     * etc in this stream.
     *
     * @returns the value of the BodyContent as a String
     */
    public abstract String getString();
	
    /**
     * Write the contents of this BodyContent into a Writer.
     * Subclasses are likely to do interesting things with the
     * implementation so some things are extra efficient.
     *
     * @param out The writer into which to place the contents of
     * this body evaluation
     */

    public abstract void writeOut(Writer out) throws IOException;

    /**
     * Get the enclosing JspWriter
     *
     * @returns the enclosing JspWriter passed at construction time
     */

    public JspWriter getEnclosingWriter() {
	return enclosingWriter;
    }

    /**
     * private fields
     */
    
    private JspWriter enclosingWriter;
 }
