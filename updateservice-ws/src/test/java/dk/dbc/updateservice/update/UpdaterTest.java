//-----------------------------------------------------------------------------
package dk.dbc.updateservice.update;

//-----------------------------------------------------------------------------
import dk.dbc.holdingsitems.HoldingsItemsDAO;
import dk.dbc.rawrepo.RawRepoDAO;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

//-----------------------------------------------------------------------------
/**
 * Tests the Updater EJB for code mistakes like NullPointerException, 
 * IlligalArgumentException, etc.
 * 
 * @author stp
 */
public class UpdaterTest {
    //-------------------------------------------------------------------------
    //              Constructors
    //-------------------------------------------------------------------------

    public UpdaterTest() {
    }
    
    @Mock
    RawRepoDAO rawRepoDAO;
    
    @Mock
    HoldingsItemsDAO holdingsItemsDAO;
    
    @Mock
    LibraryRecordsHandler recordsHandler;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }    
    
    //-------------------------------------------------------------------------
    //              Tests of updateRecord arguments
    //-------------------------------------------------------------------------

    /**
     * Update of a null record
     * 
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          An empty rawrepo
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update a null record
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Throw NullPointerException.
     *      </dd>
     * </dl>
     * 
     * @throws Exception 
     */
    @Test( expected = NullPointerException.class )
    public void testUpdateRecord_NullRecord() throws Exception {
        Updater updater = new Updater( rawRepoDAO, holdingsItemsDAO, recordsHandler );
        updater.init();
        updater.updateRecord( null );
    }
        
}
