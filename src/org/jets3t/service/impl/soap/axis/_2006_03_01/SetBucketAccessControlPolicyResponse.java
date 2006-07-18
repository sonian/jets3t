/*
 * This class was automatically generated with 
 * <a href="http://www.castor.org">Castor 0.9.9.1</a>, using an XML
 * Schema.
 * $Id$
 */

package org.jets3t.service.impl.soap.axis._2006_03_01;

  //---------------------------------/
 //- Imported classes and packages -/
//---------------------------------/

import java.io.IOException;
import java.io.Reader;
import java.io.Serializable;
import java.io.Writer;
import org.exolab.castor.xml.MarshalException;
import org.exolab.castor.xml.Marshaller;
import org.exolab.castor.xml.Unmarshaller;
import org.exolab.castor.xml.ValidationException;
import org.xml.sax.ContentHandler;

/**
 * Class SetBucketAccessControlPolicyResponse.
 * 
 * @version $Revision$ $Date$
 */
public class SetBucketAccessControlPolicyResponse implements java.io.Serializable {


      //----------------/
     //- Constructors -/
    //----------------/

    public SetBucketAccessControlPolicyResponse() 
     {
        super();
    } //-- org.jets3t.service.soap._2006_03_01.SetBucketAccessControlPolicyResponse()


      //-----------/
     //- Methods -/
    //-----------/

    /**
     * Method isValid
     * 
     * 
     * 
     * @return boolean
     */
    public boolean isValid()
    {
        try {
            validate();
        }
        catch (org.exolab.castor.xml.ValidationException vex) {
            return false;
        }
        return true;
    } //-- boolean isValid() 

    /**
     * Method marshal
     * 
     * 
     * 
     * @param out
     */
    public void marshal(java.io.Writer out)
        throws org.exolab.castor.xml.MarshalException, org.exolab.castor.xml.ValidationException
    {
        
        Marshaller.marshal(this, out);
    } //-- void marshal(java.io.Writer) 

    /**
     * Method marshal
     * 
     * 
     * 
     * @param handler
     */
    public void marshal(org.xml.sax.ContentHandler handler)
        throws java.io.IOException, org.exolab.castor.xml.MarshalException, org.exolab.castor.xml.ValidationException
    {
        
        Marshaller.marshal(this, handler);
    } //-- void marshal(org.xml.sax.ContentHandler) 

    /**
     * Method unmarshalSetBucketAccessControlPolicyResponse
     * 
     * 
     * 
     * @param reader
     * @return SetBucketAccessControlPolicyResponse
     */
    public static org.jets3t.service.impl.soap.axis._2006_03_01.SetBucketAccessControlPolicyResponse unmarshalSetBucketAccessControlPolicyResponse(java.io.Reader reader)
        throws org.exolab.castor.xml.MarshalException, org.exolab.castor.xml.ValidationException
    {
        return (org.jets3t.service.impl.soap.axis._2006_03_01.SetBucketAccessControlPolicyResponse) Unmarshaller.unmarshal(org.jets3t.service.impl.soap.axis._2006_03_01.SetBucketAccessControlPolicyResponse.class, reader);
    } //-- org.jets3t.service.soap._2006_03_01.SetBucketAccessControlPolicyResponse unmarshalSetBucketAccessControlPolicyResponse(java.io.Reader) 

    /**
     * Method validate
     * 
     */
    public void validate()
        throws org.exolab.castor.xml.ValidationException
    {
        org.exolab.castor.xml.Validator validator = new org.exolab.castor.xml.Validator();
        validator.validate(this);
    } //-- void validate() 

}
