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
 * Extra Tag Information for a Custom Tag;
 * this class is mentioned in the Tag Library Descriptor file (TLD).
 *
 * This class must be used:
 *  - if the tag defines any scripting variables
 *  - if the tag wants to provide translation-time validation of the tag
 *    attributes.
 *
 */

public abstract class TagExtraInfo {

    /**
     * information on scripting variables defined by this tag
     *
     * @param data The translation-time TagData instance.
     */
    public VariableInfo[] getVariableInfo(TagData data) {
	return new VariableInfo[0];
    }

    /**
     * Translation-time validation of the attributes.  The argument is a
     * translation-time, so request-time attributes are indicated as such.
     *
     * @param data The translation-time TagData instance.
     */

    public boolean isValid(TagData data) {
	return true;
    }

    /**
     * Set the TagInfo for this class
     *
     * @param tagInfo The TagInfo this instance is extending
     */
    public final void setTagInfo(TagInfo tagInfo) {
	this.tagInfo = tagInfo;
    }

    /**
     * Get the TagInfo for this class
     *
     * @returns the taginfo instnace this instance is extending
     */
    public final TagInfo getTagInfo() {
	return tagInfo;
    }
    
    // private data
    private TagInfo tagInfo;
}

