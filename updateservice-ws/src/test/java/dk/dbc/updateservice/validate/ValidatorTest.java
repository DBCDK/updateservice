//-----------------------------------------------------------------------------
package dk.dbc.updateservice.validate;

//-----------------------------------------------------------------------------
import com.google.gson.Gson;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.utils.IOUtils;
import dk.dbc.iscrumjs.ejb.JavaScriptException;
import dk.dbc.updateservice.ws.ValidationError;
import java.io.IOException;
import java.util.List;
import static org.junit.Assert.*;
import org.junit.Test;

//-----------------------------------------------------------------------------
/**
 *
 * @author stp
 */
public class ValidatorTest {

    public ValidatorTest() {
    }

    //-------------------------------------------------------------------------
    //              Unittest
    //-------------------------------------------------------------------------
    
    //!\name Validation of a record
    //@{
    @Test
    public void testValidate_SingleRecordOK() throws IOException {
        Validator ejb = ValidatorMock.newInstance( JS_RESOURCE_PATH + "record_ok.js" );
        List<ValidationError> errors = ejb.validateRecord( "bog", loadRecord( "single_record.json" ) );

        assertNotNull( errors );
        assertTrue( errors.isEmpty() );
    }

    @Test
    public void testValidate_JSValidationErrors() throws IOException {
        Validator ejb = ValidatorMock.newInstance( JS_RESOURCE_PATH + "record_error.js" );
        List<ValidationError> errors = ejb.validateRecord( "bog", loadRecord( "single_record.json" ) );

        assertNotNull( errors );
        assertEquals( 1, errors.size() );

        ValidationError err = errors.get( 0 );
        assertEquals( "Problemer med posten.", err.getParams().get( "message" ).toString() );
        assertEquals( "http://url.dbc.dk/path/doc.html", err.getParams().get( "url" ).toString() );
    }

    @Test( expected = org.mozilla.javascript.JavaScriptException.class )
    public void testValidate_JSException() throws IOException {
        Validator ejb = ValidatorMock.newInstance( JS_RESOURCE_PATH + "validate_exception.js" );
        List<ValidationError> errors = ejb.validateRecord( "bog", loadRecord( "single_record.json" ) );
    }
    //@}
    
    //!\name Validate schema
    //@{
    @Test
    public void testValidate_ValidateSchemaNotFound() throws IOException, JavaScriptException {
        Validator ejb = ValidatorMock.newInstance( JS_RESOURCE_PATH + "validate_schema_not_found.js" );
        assertFalse( ejb.checkValidateSchema( "bog" ) );
    }

    @Test( expected = JavaScriptException.class )
    public void testValidate_ValidateSchemaWrongResultTypeFromJS() throws IOException, JavaScriptException {
        Validator ejb = ValidatorMock.newInstance( JS_RESOURCE_PATH + "validate_schema_wrong_result_type.js" );
        assertFalse( ejb.checkValidateSchema( "bog" ) );
    }
    //@}
    
    //!\name Helpers
    //@{
    private MarcRecord loadRecord( String jsonResource ) throws IOException {
        Gson gson = new Gson();
        return gson.fromJson( IOUtils.readAll( JSON_RESOURCE_PATH + jsonResource ), MarcRecord.class );
    }
    //@}
    
    private static final String JSON_RESOURCE_PATH = "dk/dbc/updateservice/validate/";
    private static final String JS_RESOURCE_PATH = JSON_RESOURCE_PATH;
    

}
