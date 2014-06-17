//-----------------------------------------------------------------------------
package dk.dbc.updateservice.integration;

//-----------------------------------------------------------------------------
import dk.dbc.updateservice.integration.service.BibliographicRecord;
import java.io.IOException;
import javax.xml.parsers.ParserConfigurationException;
import static org.junit.Assert.*;
import org.junit.Test;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

//-----------------------------------------------------------------------------
/**
 *
 * @author stp
 */
public class BibliographicRecordFactoryTest {
    
    public BibliographicRecordFactoryTest() {
    }

    @Test
    public void testLoadResource() throws ParserConfigurationException, SAXException, IOException {
        BibliographicRecord record = BibliographicRecordFactory.loadResource( "single_record.xml" );
        
        assertNotNull( record );
        assertEquals( "info:lc/xmlns/marcxchange-v1", record.getRecordSchema() );
        assertEquals( "xml", record.getRecordPacking() );
        assertNotNull( record.getRecordData() );
        assertEquals( 3, record.getRecordData().getContent().size() );
        assertTrue( record.getRecordData().getContent().get( 1 ) instanceof Node );
    }
    
}
