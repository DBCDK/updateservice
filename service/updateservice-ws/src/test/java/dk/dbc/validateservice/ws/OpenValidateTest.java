//-----------------------------------------------------------------------------
package dk.dbc.validateservice.ws;

//-----------------------------------------------------------------------------
import dk.dbc.updateservice.ws.OpenValidate;
//import dk.dbc.oss.ns.opencataloging.datacontracts.CDTOBibliographicalRecord;
//import dk.dbc.oss.ns.opencataloging.datacontracts.CDTOValidateError;
//import dk.dbc.oss.ns.opencataloging.datacontracts.CDTOValidateErrorEntry;
//import dk.dbc.oss.ns.opencataloging.datacontracts.CDTOValidateErrorEntryCollectionUnstructuredValidateError;
//import dk.dbc.oss.ns.opencataloging.datacontracts.CDTOValidateFailureInternalError;
//import dk.dbc.oss.ns.opencataloging.datacontracts.CDTOValidateFailureInvalidContentOfBibliographicalRecord;
//import dk.dbc.oss.ns.opencataloging.datacontracts.CDTOValidateFailureInvalidValidateSchema;
//import dk.dbc.oss.ns.opencataloging.datacontracts.CDTOValidateInstance;
//import dk.dbc.oss.ns.opencataloging.datacontracts.CDTOValidateRequest;
//import dk.dbc.oss.ns.opencataloging.datacontracts.CDTOValidateResult;
//import dk.dbc.oss.ns.opencataloging.datacontracts.CDTOValidateSuccess;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import javax.xml.bind.JAXB;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.MimeHeaders;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPBodyElement;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.transform.dom.DOMSource;
import static org.junit.Assert.*;
import org.junit.Test;
import org.w3c.dom.Element;

//-----------------------------------------------------------------------------
/**
 *
 * @author stp
 */
public class OpenValidateTest {

    public OpenValidateTest() {
    }

    //-------------------------------------------------------------------------
    //              Unittest
    //-------------------------------------------------------------------------
    //!\name Records
    //@{
    @Test
    public void testValidate_SingleRecordOK() throws IOException {
        /*
        OpenValidate service = OpenValidateMock.newInstance( "dk/dbc/validateservice/ws/record_ok.js" );
        CDTOValidateRequest params = createRequestFromResource( "single_record.xml" );
        CDTOValidateResult response = service.validate( params );

        CDTOValidateInstance validateResponse = response.getValidateInstance();
        assertNotNull( validateResponse );
        assertNull( response.getBibliographicalRecord() );
        assertEquals( CDTOValidateSuccess.class.getName(), validateResponse.getClass().getName() );
        assertTrue( true );
        */
    }

    @Test
    public void testValidate_WrongRecordFormat() throws IOException {
        /*
        OpenValidate service = OpenValidateMock.newInstance( "dk/dbc/validateservice/ws/record_ok.js" );
        CDTOValidateRequest params = createRequestFromResource( "wrong_record_schema.xml" );
        CDTOValidateResult response = service.validate( params );

        CDTOValidateInstance validateResponse = response.getValidateInstance();
        assertNotNull( validateResponse );
        assertNull( response.getBibliographicalRecord() );
        assertTrue( validateResponse.getClass() == CDTOValidateFailureInvalidValidateSchema.class );

        params = createRequestFromResource( "wrong_record_packing.xml" );
        response = service.validate( params );

        validateResponse = response.getValidateInstance();
        assertNotNull( validateResponse );
        assertNull( response.getBibliographicalRecord() );
        assertTrue( validateResponse.getClass() == CDTOValidateFailureInvalidValidateSchema.class );
        */
    }

    @Test
    public void testValidate_WrongNumberOfRecords() throws IOException {
        /*
        OpenValidate service = OpenValidateMock.newInstance( "dk/dbc/validateservice/ws/record_ok.js" );
        CDTOValidateRequest params = createRequestFromResource( "multible_records.xml" );
        CDTOValidateResult response = service.validate( params );

        CDTOValidateInstance validateResponse = response.getValidateInstance();
        assertNotNull( validateResponse );
        assertNull( response.getBibliographicalRecord() );
        assertTrue( validateResponse.getClass() == CDTOValidateFailureInvalidContentOfBibliographicalRecord.class );
        */
    }

    @Test
    public void testValidate_JSValidationErrors() throws IOException {
        /*
        OpenValidate service = OpenValidateMock.newInstance( "dk/dbc/validateservice/ws/record_error.js" );
        CDTOValidateRequest params = createRequestFromResource( "single_record.xml" );
        CDTOValidateResult response = service.validate( params );

        CDTOValidateInstance validateResponse = response.getValidateInstance();
        assertNotNull( validateResponse );
        assertNull( response.getBibliographicalRecord() );
        assertTrue( validateResponse.getClass() == CDTOValidateError.class );

        CDTOValidateError valError = ( CDTOValidateError ) validateResponse;
        assertNotNull( valError );
        assertNotNull( valError.getCDTOValidateErrorEntry() );
        assertTrue( valError.getCDTOValidateErrorEntry().isEmpty() == false );

        List<CDTOValidateErrorEntry> entries = valError.getCDTOValidateErrorEntry();
        assertNotNull( entries );
        assertEquals( 1, entries.size() );

        assertEquals( CDTOValidateErrorEntryCollectionUnstructuredValidateError.class, entries.get( 0 ).getClass() );
        CDTOValidateErrorEntryCollectionUnstructuredValidateError valRecord = ( CDTOValidateErrorEntryCollectionUnstructuredValidateError ) entries.get( 0 );
        assertEquals( "Problemer med posten.", valRecord.getValidateError() );
        assertEquals( "http://url.dbc.dk/path/doc.html", valRecord.getUrlForDocumentation() );
        */
    }

    @Test
    public void testValidate_JSException() throws IOException {
        /*
        OpenValidate service = OpenValidateMock.newInstance( "dk/dbc/validateservice/ws/validate_exception.js" );
        CDTOValidateRequest params = createRequestFromResource( "single_record.xml" );
        CDTOValidateResult response = service.validate( params );

        CDTOValidateInstance validateResponse = response.getValidateInstance();
        assertNotNull( validateResponse );
        assertNull( response.getBibliographicalRecord() );
        assertEquals( CDTOValidateFailureInternalError.class.getName(), validateResponse.getClass().getName() );

        CDTOValidateFailureInternalError err = ( CDTOValidateFailureInternalError ) validateResponse;
        assertNotEquals( "", err.getErrorMessage() );
        */
    }

    @Test
    public void testCreateRequestFromResource() throws Exception {
        /*
        CDTOValidateRequest request = createRequestFromResource( "single_record.xml" );

        assertNotNull( request );
        assertEquals( "bog", request.getValidateSchema() );

        CDTOBibliographicalRecord record = request.getBibliographicalRecord();
        assertNotNull( record );
        assertEquals( "info:lc/xmlns/marcxchange-v1", record.getRecordSchema() );
        assertEquals( "xml", record.getRecordPacking() );
        */
    }
    //@}

    //!\name Validate schema
    //@{
    @Test
    public void testValidate_ValidateSchemaNotFound() throws IOException {
        /*
        OpenValidate service = OpenValidateMock.newInstance( "dk/dbc/validateservice/ws/validate_schema_not_found.js" );
        CDTOValidateRequest params = createRequestFromResource( "single_record.xml" );
        CDTOValidateResult response = service.validate( params );

        CDTOValidateInstance validateResponse = response.getValidateInstance();
        assertNotNull( validateResponse );
        assertNull( response.getBibliographicalRecord() );
        assertEquals( CDTOValidateFailureInvalidValidateSchema.class.getName(), validateResponse.getClass().getName() );
        */
    }

    @Test
    public void testValidate_ValidateSchemaWrongResultTypeFromJS() throws IOException {
        /*
        OpenValidate service = OpenValidateMock.newInstance( "dk/dbc/validateservice/ws/validate_schema_wrong_result_type.js" );
        CDTOValidateRequest params = createRequestFromResource( "single_record.xml" );
        CDTOValidateResult response = service.validate( params );

        CDTOValidateInstance validateResponse = response.getValidateInstance();
        assertNotNull( validateResponse );
        assertNull( response.getBibliographicalRecord() );
        assertEquals( CDTOValidateFailureInternalError.class.getName(), validateResponse.getClass().getName() );

        CDTOValidateFailureInternalError err = ( CDTOValidateFailureInternalError ) validateResponse;
        assertNotEquals( "", err.getErrorMessage() );
        */
    }

//    @Test
//    public void testValidate_ModulePathInJS() throws IOException {
//        OpenValidate service = OpenValidateMock.newInstance( "JavaScript/entrypoint.js" );
//        Validate params = createRequestFromResource( "single_record.xml" );
//        ValidateResponse response = service.validate( params );
//
//        CDTOValidationResponse validateResponse = response.getValidateResult();
//        assertNotNull( validateResponse );
//        assertNull( validateResponse.getBibliographicalRecord() );
//        assertEquals( CDTOValidationResponseSuccess.class.getName(), validateResponse.getClass().getName() );
//    }
    //@}
    //!\name Helpers
    //@{
    /*
    private CDTOValidateRequest createRequestFromResource( String resName ) {
        CDTOValidateRequest cDTOValidateRequest = null;
        try {
            InputStream in = getClass().getResourceAsStream( resName );
            MessageFactory mf = MessageFactory.newInstance();

            MimeHeaders mimeHeaders = new MimeHeaders();
            mimeHeaders.addHeader( "Content-Type", "text/xml; charset=UTF-8" );
            SOAPMessage sm = mf.createMessage( mimeHeaders, in );
            SOAPBody body = sm.getSOAPBody();

            Iterator validateChildElements = body.getChildElements();
            while (validateChildElements.hasNext()) {
                Object node = validateChildElements.next();
                if ( node instanceof Element ) {
                    SOAPBodyElement bodyElement = ( SOAPBodyElement ) node;
                    Iterator validateRequestChildElements = bodyElement.getChildElements();
                    while (validateRequestChildElements.hasNext()) {
                        Object validateRequestNode = validateRequestChildElements.next();
                        if ( validateRequestNode instanceof Element ) {
                            SOAPBodyElement validateRequestElement = ( SOAPBodyElement ) validateRequestNode;
                            DOMSource dOMSource = new DOMSource( validateRequestElement );
                            cDTOValidateRequest = JAXB.unmarshal( dOMSource, CDTOValidateRequest.class );
                            break;
                        }
                    }
                }
            }
        } catch ( IOException | SOAPException ex ) {
            ex.printStackTrace();
            fail( ex.getMessage() );
        }
        return cDTOValidateRequest;
    }
    */
    //@}
}
