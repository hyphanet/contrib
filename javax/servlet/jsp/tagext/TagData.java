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

import java.util.Hashtable;

/**
 * Tag instance attribute(s)/value(s); often this data is fully static in the
 * case where none of the attributes have runtime expresssions as their values.
 * Thus this class is intended to expose an immutable interface to a set of
 * immutable attribute/value pairs.
 *
 * This class is cloneable so implementations can create a static instance
 * and then just clone it before adding the request-time expressions.
 */

public class TagData implements Cloneable {
    /**
     * Distinguished value for an attribute to indicate its value
     * is a request-time expression which is not yet available because
     * this TagData instance is being used at translation-time.
     */
    // TODO -- revisit clonable; do we need a clone() method?
    // TODO -- Should we just use an array?
    // TODO -- should there be a factory?

    public static final Object REQUEST_TIME_VALUE = new Object();

    /**
     * Constructor for a TagData
     *
     * For simplicity and speed, we are just using primitive types.
     * A typical constructor may be
     *
     * static final Object[][] att = {{"connection", "conn0"}, {"id", "query0"}};
     * static final TagData td = new TagData(att);
     *
     * In an implementation that uses the clonable approach sketched
     * above all values must be Strings except for those holding the
     * distinguished object REQUEST_TIME_VALUE.

     * @param atts the static attribute and values.  May be null.
     */
    public TagData(Object[] atts[]) {
	if (atts == null) {
	    attributes = new Hashtable();
	} else {
	    attributes = new Hashtable(atts.length);
	}

	if (atts != null) {
	    for (int i = 0; i < atts.length; i++) {
		attributes.put(atts[i][0], atts[i][1]);
	    }
	}
    }

    /**
     * Constructor for a TagData
     *
     * If you already have the attributes in a hashtable, use this
     * constructor. 
     *

     */
    public TagData(Hashtable attrs) {
        this.attributes = attrs;
    }

    /**
     * @return the value of the id attribute or null
     */

    public String getId() {
	return getAttributeString(TagAttributeInfo.ID);
    }

    /**
     * @return the attribute's value object. Returns the
     * distinguished object REQUEST_TIME_VALUE if the value is
     * request time and we are using TagData at translation time.
     * Returns null if the attribute is not set.
     */
    // TODO -- means we cannot distinguish from an unset attribute an
    // TODO -- one that is set to null.

    public Object getAttribute(String attName) {
	return attributes.get(attName);
    }

    /**
     * Set the value of this attribute to be 
     */
    public void setAttribute(String attName,
			     Object value) {
	attributes.put(attName, value);
    }

    /**
     * @return the attribute value string
     *
     * @throw ClassCastException if attribute value is not a String
     */

    public String getAttributeString(String attName) {
	Object o = attributes.get(attName);
	if (o == null) {
	    return null;
	} else {
	    return (String) o;
	}	
    }

    /**
     * Enumerates the attributes
     *@return An enumeration of the attributes in a TagData
     */
    public java.util.Enumeration getAttributes() {
        return attributes.keys();
    };

    // private data

    private Hashtable attributes;	// the tagname/value map
}
