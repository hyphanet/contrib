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

import javax.servlet.jsp.*;


/**
 * The Tag interface defines the basic protocol between a Tag handler and JSP page
 * implementation class.  It defines the life cycle and the methods to be invoked at
 * start and end tag.
 * <p>
 * There are several methods that get invoked to set the state of a Tag handler.
 * The Tag handler is required to keep this state so the page compiler can
 * choose not to reinvoke some of the state setting.
 * <p>
 * The page compiler guarantees that setPageContext and setParent
 * will all be invoked on the Tag handler, in that order, before doStartTag() or
 * doEndTag() are invoked on it.
 * The page compiler also guarantees that release will be invoked on the Tag
 * handler before the end of the page.
 * <p>
 * Here is a typical invocation sequence:
 *
 * <pre>
 * <code>
 * 
 * ATag t = new ATag();
 *
 * -- need to set required information 
 * t.setPageContext(...);
 * t.setParent(...);
 * t.setAttribute1(value1);
 * t.setAttribute2(value2);
 *
 * -- all ready to go
 * t.doStartTag();
 * t.doEndTag();
 * 
 * ... other tags and template text
 *
 * -- say one attribute is changed, but parent and pageContext have not changed
 * t.setAttribute2(value3);
 * t.doStartTag()
 * t.doEndTag()
 *
 * ... other tags and template text
 *
 * -- assume that this new action happens to use the same attribute values
 * -- it is legal to reuse the same handler instance,  with no changes...
 * t.doStartTag();
 * t.doEndTag();
 *
 * -- OK, all done
 * t.release()
 * </code>
 * </pre>
 *
 * <p>
 * The Tag interface also includes methods to set a parent chain, which is used
 * to find enclosing tag handlers.
 *
 */

public interface Tag {

    /**
     * Skip body evaluation.
     * Valid return value for doStartTag and doAfterBody.
     */
 
    public final static int SKIP_BODY = 0;
 
    /**
     * Evaluate body into existing out stream.
     * Valid return value for doStartTag.
     * This is an illegal return value for doStartTag when the class implements
     * BodyTag, since BodyTag implies the creation of a new BodyContent.
     */
 
    public final static int EVAL_BODY_INCLUDE = 1;

    /**
     * Skip the rest of the page.
     * Valid return value for doEndTag.
     */

    public final static int SKIP_PAGE = 5;

    /**
     * Continue evaluating the page.
     * Valid return value for doEndTag().
     */

    public final static int EVAL_PAGE = 6;

    // Setters for Tag handler data

    /**
     * Set the current page context.
     * Called by the page implementation prior to doStartTag().
     * <p>
     * This value is *not* reset by doEndTag() and must be explicitly reset
     * by a page implementation
     */

    void setPageContext(PageContext pc);

    /**
     * Set the current nesting Tag of this Tag.
     * Called by the page implementation prior to doStartTag().
     * <p>
     * This value is *not* reset by doEndTag() and must be explicitly reset
     * by a page implementation.  Code can assume that setPageContext
     * has been called with the proper values before this point.
     */

    void setParent(Tag t);

    /**
     * @return the current parent
     * @seealso TagSupport.findAncestorWithClass().
     */

    Tag getParent();


    // Actions for basic start/end processing.

    /**
     * Process the start tag for this instance.
     *
     * @returns EVAL_BODY_INCLUDE if the tag wants to process body, SKIP_BODY if it
     * does not want to process it.
     *
     * When a Tag returns EVAL_BODY_INCLUDE the body (if any) is evaluated
     * and written into the current "out" JspWriter then doEndTag() is invoked.
     *
     * @see BodyTag
     */
 
    int doStartTag() throws JspException;
 

    /**
     * Process the end tag. This method will be called on all Tag objects.
     */

    int doEndTag() throws JspException;

    /**
     * Called on a Tag handler to release state.
     * The page compiler guarantees this method will be called on all tag handlers,
     * but there may be multiple invocations on doStartTag and doEndTag in between.
     */

    void release();

}
