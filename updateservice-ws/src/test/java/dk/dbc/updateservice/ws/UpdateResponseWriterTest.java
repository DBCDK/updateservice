//-----------------------------------------------------------------------------
package dk.dbc.updateservice.ws;

//-----------------------------------------------------------------------------

import dk.dbc.oss.ns.catalogingupdate.ValidateWarningOrErrorEnum;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.*;

//-----------------------------------------------------------------------------
/**
 *
 * @author stp
 */
public class UpdateResponseWriterTest {
    
    public UpdateResponseWriterTest() {
    }

    @Test
    public void testAddValidateResults() {
        List<ValidationError> valErrors = new ArrayList<>();
        UpdateResponseWriter instance = new UpdateResponseWriter();
        instance.addValidateResults( valErrors );

        ValidationError err = new ValidationError();
        err.setType( ValidateWarningOrErrorEnum.ERROR );
        HashMap<String, Object> params = new HashMap<>();
        params.put( "url", "url" );
        params.put( "message", "message" );
        params.put( "fieldno", 1.0 );
        params.put( "subfieldno", 3.0 );
        err.setParams( params );
        valErrors.add( err );
        
        instance = new UpdateResponseWriter();
        instance.addValidateResults( valErrors );
    }

    @Test
    public void testBoolean() {
        assertEquals( "true", Boolean.toString( true ) );
        assertEquals( "false", Boolean.toString( false ) );
        assertTrue( Boolean.valueOf( "True" ) );
        assertFalse( Boolean.valueOf( "False" ) );
    }
}
