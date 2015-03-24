//-----------------------------------------------------------------------------
package dk.dbc.updateservice.integration;

//-----------------------------------------------------------------------------

import dk.dbc.updateservice.client.BibliographicRecordFactory;
import dk.dbc.updateservice.service.api.BibliographicRecord;
import org.junit.Test;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;

import static org.junit.Assert.*;

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
        assertNotNull( getClass().getResourceAsStream( "/settings.properties" ) );
        BibliographicRecord record = BibliographicRecordFactory.loadResource( getClass().getResourceAsStream( "single_record.xml" ) );
        
        assertNotNull( record );
        assertEquals( "info:lc/xmlns/marcxchange-v1", record.getRecordSchema() );
        assertEquals( "xml", record.getRecordPacking() );
        assertNotNull( record.getRecordData() );
        assertEquals( 3, record.getRecordData().getContent().size() );
        assertTrue( record.getRecordData().getContent().get( 1 ) instanceof Node );
    }
    
}
