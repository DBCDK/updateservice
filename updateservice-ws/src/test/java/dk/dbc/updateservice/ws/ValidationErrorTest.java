//-----------------------------------------------------------------------------
package dk.dbc.updateservice.ws;

//-----------------------------------------------------------------------------
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;
import org.junit.Test;

//-----------------------------------------------------------------------------
/**
 *
 * @author stp
 */
public class ValidationErrorTest {
    
    public ValidationErrorTest() {
    }
    
    //-------------------------------------------------------------------------
    //              Unittest
    //-------------------------------------------------------------------------
    
    //!\name Unittests
    //@{
    //@}
    
    //!\name Helpers
    //@{
    private String loadResource( String resName ) {
        return null;        
    }
    
    @Test
    public void testGson() {
        Map<String, Object> map = new HashMap<>();
        map.put( "k1", "v1" );
        map.put( "k2", "v2" );
        map.put( "k3", 5 );
        map.put( "k4", true );
        
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        assertNotEquals( "", gson.toJson( map ) );
        
        map = gson.fromJson( "{\n" +
"  \"k3\": 5,\n" +
"  \"k4\": true,\n" +
"  \"k1\": \"v1\",\n" +
"  \"k2\": \"v2\"\n" +
"}", HashMap.class );
        
        assertEquals( "v1", map.get( "k1" ) );
        assertEquals( "v2", map.get( "k2" ) );
        assertEquals( new Double( 5 ), map.get( "k3" ) );
        assertEquals( true, map.get( "k4" ) );
    }
    //@}
    
}
