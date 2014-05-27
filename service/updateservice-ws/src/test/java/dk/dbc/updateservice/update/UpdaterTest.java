//-----------------------------------------------------------------------------
package dk.dbc.updateservice.update;

//-----------------------------------------------------------------------------
import org.junit.Test;

//-----------------------------------------------------------------------------
/**
 *
 * @author stp
 */
public class UpdaterTest {
    public UpdaterTest() {
    }
    
    @Test( expected = NullPointerException.class )
    public void testUpdateRecord_NullRecord() throws Exception {
        Updater updater = new Updater();
        updater.updateRecord( null );
    }
    
}
