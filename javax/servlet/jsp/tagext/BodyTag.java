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
 * The BodyTag interface extends Tag by defining additional methods that let
 * a Tag handler access its body.
 * <p>
 * The interface provides two new methods: one is to be invoked with the BodyContent
 * for the evaluation of the body, the other is to be reevaluated after every body
 * evaluation.
 * <p>
 * Without repeating the portions described in Tag.java, a typical invocation sequence is:
 *
 * <pre>
 * <code>
 *
 * -- we are picking up after all the setters have been done
 * t.doStartTag();
 * out = pageContext.pushBody();
 * -- prepare for body
 * t.setBodyContent(out);
 * -- preamble
 * t.doBodyInit();
 * -- BODY evaluation into out
 * t.doAfterBody();
 * -- while doAfterBody returns EVAL_BODY_TAG we iterate
 * -- BODY evaluation into out
 * t.doAfterBody()
 * -- done
 * t.doEndTag()
 *
 * </code>
 * </pre>
 */

public interface BodyTag extends Tag {

    /**
     * Request the creation of new BodyContent on which to evaluate the
     * body of this tag.
     * Returned from doStartTag and doAfterBody.
     * This is an illegal return value for doStartTag when the class does not
     * implement BodyTag, since BodyTag is needed to manipulate the new Writer.
     */
 
    public final static int EVAL_BODY_TAG = 2;

    /**
     * Setter method for the bodyContent property.
     * <p>
     * This method will not be invoked if there is no body evaluation.
     *
     * @param b the BodyContent
     * @seealso #doInitBody
     * @seealso #doAfterBody
     */

    void setBodyContent(BodyContent b);

    /**
     * Prepare for evaluation of the body.
     * <p>
     * The method will be invoked once per action invocation by the page implementation
     * after a new BodyContent has been obtained and set on the tag handler
     * via the setBodyContent() method and before the evaluation
     * of the tag's body into that BodyContent.
     * <p>
     * This method will not be invoked if there is no body evaluation.
     *
     * @seealso #doAfterBody
     */

    void doInitBody() throws JspException;

    /**
     * Actions after some body has been evaluated.
     * <p>
     * Not invoked in empty tags or in tags returning SKIP_BODY in doStartTag()
     * This method is invoked after every body evaluation.
     * The pair "BODY -- doAfterBody()" is invoked initially if doStartTag()
     * returned EVAL_BODY_TAG, and it is repeated as long
     * as the doAfterBody() evaluation returns EVAL_BODY_TAG
     * <p>
     * The method re-invocations may be lead to different actions because
     * there might have been some changes to shared state, or because
     * of external computation.
     *
     * @returns whether additional evaluations of the body are desired
     * @seealso #doInitBody
     */

    int doAfterBody() throws JspException;
}
