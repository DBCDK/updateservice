//-----------------------------------------------------------------------------
package dk.dbc.updateservice.integration;

//-----------------------------------------------------------------------------
import dk.dbc.updateservice.integration.service.BibliographicRecord;
import dk.dbc.updateservice.integration.service.ExtraRecordData;
import dk.dbc.updateservice.integration.service.RecordData;
import java.io.IOException;
import java.io.InputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

//-----------------------------------------------------------------------------
/**
 *
 * @author stp
 */
public class BibliographicRecordFactory {
    public static BibliographicRecord loadResource( String resourceName ) throws ParserConfigurationException, SAXException, IOException {
        return loadResource( BibliographicRecordFactory.class.getResourceAsStream( resourceName ) );
    }

    public static BibliographicRecord loadResource( InputStream in ) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory xmlFactory = DocumentBuilderFactory.newInstance();
	DocumentBuilder xmlBuilder = xmlFactory.newDocumentBuilder();
	Document doc = xmlBuilder.parse( in );
        
        BibliographicRecord record = new BibliographicRecord();
        record.setRecordSchema( "info:lc/xmlns/marcxchange-v1" );
        record.setRecordPacking( "xml" );
        
        RecordData recData = new RecordData();
        recData.getContent().add( "\n" );
        recData.getContent().add( doc.getDocumentElement() );
        recData.getContent().add( "\n" );
        record.setRecordData( recData );
        
        ExtraRecordData extraRecordData = new ExtraRecordData();
        record.setExtraRecordData( extraRecordData );
        
        return record;
    }
}
