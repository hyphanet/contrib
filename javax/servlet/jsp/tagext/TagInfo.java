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
 * Tag information for a tag in a Tag Library;
 * this class is instantiated from the Tag Library Descriptor file (TLD).
 *
 */

public class TagInfo {

    /**
     * static constant for getBodyContent() when it is JSP
     */

    public static final String BODY_CONTENT_JSP = "JSP";

    /**
     * static constant for getBodyContent() when it is Tag dependent
     */

    public static final String BODY_CONTENT_TAG_DEPENDENT = "TAGDEPENDENT";


    /**
     * static constant for getBodyContent() when it is empty
     */

    public static final String BODY_CONTENT_EMPTY = "EMPTY";

    /**
     * Constructor for TagInfo.
     * No public constructor; this class is to be instantiated only from the
     * TagLibrary code under request from some JSP code that is parsing a
     * TLD (Tag Library Descriptor).
     *
     * @param tagName The name of this tag
     * @param tagClassName The name of the tag handler class
     * @param bodycontent Information on the body content of these tags
     * @param infoString The (optional) string information for this tag
     * @param taglib The instance of the tag library that contains us.
     * @param tagExtraInfo The instance providing extra Tag info.  May be null
     * @param attributeInfo An array of AttributeInfo data from descriptor.
     * May be null;
     *
     */
    public TagInfo(String tagName,
	    String tagClassName,
	    String bodycontent,
	    String infoString,
	    TagLibraryInfo taglib,
	    TagExtraInfo tagExtraInfo,
	    TagAttributeInfo[] attributeInfo) {
	this.tagName       = tagName;
	this.tagClassName  = tagClassName;
	this.bodyContent   = bodycontent;
	this.infoString    = infoString;
	this.tagLibrary    = taglib;
	this.tagExtraInfo  = tagExtraInfo;
	this.attributeInfo = attributeInfo;

	if (tagExtraInfo != null)
            tagExtraInfo.setTagInfo(this);
    }
			 
    /**
     * Tag name
     */

    public String getTagName() {
	return tagName;
    }

    /**
     * A null return means no information on attributes
     */

   public TagAttributeInfo[] getAttributes() {
       return attributeInfo;
   }

    /**
     * Information on the object created by this tag at runtime.
     * Null means no such object created.
     *
     * Default is null if the tag has no "id" attribute,
     * otherwise, {"id", Object}
     */

   public VariableInfo[] getVariableInfo(TagData data) {
       TagExtraInfo tei = getTagExtraInfo();
       if (tei == null) {
	   return null;
       }
       return tei.getVariableInfo(data);
   }

    /**
     * Translation-time validation of the attributes.  The argument is a
     * translation-time, so request-time attributes are indicated as such.
     *
     * @param data The translation-time TagData instance.
     */


   public boolean isValid(TagData data) {
       TagExtraInfo tei = getTagExtraInfo();
       if (tei == null) {
	   return true;
       }
       return tei.isValid(data);
   }


    /**
      The instance (if any) for extra tag information
      */
    public TagExtraInfo getTagExtraInfo() {
	return tagExtraInfo;
    }


    /**
     * Name of the class that provides the (run-time handler for this tag
     */
    
    public String getTagClassName() {
	return tagClassName;
    }


    /**
     * @return the body content (hint) string
     */

    public String getBodyContent() { return bodyContent; }

    /**
     * @return the info string
     */

    public String getInfoString() { return infoString; }

    /**
     * @return the tab library instance we belong to
     */

    public TagLibraryInfo getTagLibrary() { return tagLibrary; }


    /**
     * Stringify for debug purposes...
     */
    public String toString() {
        StringBuffer b = new StringBuffer();
        b.append("name = "+tagName+" ");
        b.append("class = "+tagClassName+" ");
        b.append("body = "+bodyContent+" ");
        b.append("info = "+infoString+" ");
        b.append("attributes = {\n");
        for(int i = 0; i < attributeInfo.length; i++)
            b.append("\t"+attributeInfo[i].toString());
        b.append("\n}\n");
        return b.toString();
    }

    /*
     * private fields
     */

    private String             tagName; // the name of the tag
    private String             tagClassName;
    private String             bodyContent;
    private String             infoString;
    private TagLibraryInfo     tagLibrary;
    private TagExtraInfo       tagExtraInfo; // instance of TagExtraInfo
    private TagAttributeInfo[] attributeInfo;
}
