//-----------------------------------------------------------------------------
package dk.dbc.updateservice.ws;

//-----------------------------------------------------------------------------
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import static org.junit.Assert.*;
import org.junit.Test;

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
        err.setType( "type" );
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
    
}
