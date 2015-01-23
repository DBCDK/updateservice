//-----------------------------------------------------------------------------
package dk.dbc.updateservice.validate;

//-----------------------------------------------------------------------------

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.utils.IOUtils;
import dk.dbc.updateservice.javascript.Scripter;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.ws.ValidationError;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

//-----------------------------------------------------------------------------
/**
 *
 * @author stp
 */
public class ValidatorTest {
    @Mock
    Scripter scripter;

    public ValidatorTest() {
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks( this );
    }

    //-------------------------------------------------------------------------
    //              Unittest
    //-------------------------------------------------------------------------
    
    //!\name Validation of a record
    //@{
    @Test
    public void testValidate_SingleRecordOK() throws Exception {
        Validator ejb = new Validator( scripter, new Properties() );

        when(scripter.callMethod( eq( "validator.js" ), eq( "validateRecord" ), eq( "bog" ), any(String.class), any(Properties.class) ) ).thenReturn("[]");

        List<ValidationError> errors = ejb.validateRecord( "bog", loadRecord( "single_record.json" ) );

        assertNotNull( errors );
        assertTrue( errors.isEmpty() );
    }

    @Test
    public void testValidate_JSValidationErrors() throws Exception {
        Validator ejb = new Validator( scripter, new Properties() );

        when(scripter.callMethod( eq( "validator.js" ), eq( "validateRecord" ), eq( "bog" ), any(String.class), any(Properties.class) ) ).thenReturn( "[\n" +
                "        {\n" +
                "            \"type\": \"ERROR\",\n" +
                "            \"params\": {\n" +
                "                \"url\": \"http://url.dbc.dk/path/doc.html\",\n" +
                "                \"message\": \"Problemer med posten.\"\n" +
                "            }\n" +
                "        }\n" +
                "    ]" );
        List<ValidationError> errors = ejb.validateRecord( "bog", loadRecord( "single_record.json" ) );

        assertNotNull( errors );
        assertEquals( 1, errors.size() );

        ValidationError err = errors.get( 0 );
        assertEquals( "Problemer med posten.", err.getParams().get( "message" ).toString() );
        assertEquals( "http://url.dbc.dk/path/doc.html", err.getParams().get( "url" ).toString() );
    }

    @Test( expected = ScripterException.class )
    public void testValidate_JSException() throws Exception {
        Validator ejb = new Validator( scripter, new Properties() );

        when(scripter.callMethod( eq( "validator.js" ), eq( "validateRecord" ), eq( "bog" ), any(String.class), any(Properties.class) ) ).thenThrow( new ScripterException( "message" ) );
        List<ValidationError> errors = ejb.validateRecord( "bog", loadRecord( "single_record.json" ) );
    }
    //@}
    
    //!\name Validate schema
    //@{
    @Test
    public void testValidate_ValidateSchemaNotFound() throws Exception {
        Validator ejb = new Validator( scripter, new Properties() );

        when(scripter.callMethod( eq( "validator.js" ), eq( "checkTemplate" ), eq( "bog" ), any(Properties.class) ) ).thenReturn( false );
        assertFalse( ejb.checkValidateSchema( "bog" ) );
    }
    //@}
    
    //!\name Helpers
    //@{
    private MarcRecord loadRecord( String jsonResource ) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue( IOUtils.readAll( JSON_RESOURCE_PATH + jsonResource ), MarcRecord.class );
    }
    //@}
    
    private static final String JSON_RESOURCE_PATH = "dk/dbc/updateservice/validate/";
    private static final String JS_RESOURCE_PATH = JSON_RESOURCE_PATH;
    

}
