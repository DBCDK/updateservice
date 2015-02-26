//-----------------------------------------------------------------------------
package dk.dbc.updateservice.integration;

//-----------------------------------------------------------------------------

import dk.dbc.iscrum.records.MarcConverter;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordFactory;
import dk.dbc.iscrum.utils.IOUtils;
import dk.dbc.updateservice.integration.service.BibliographicRecord;
import dk.dbc.updateservice.integration.service.ExtraRecordData;
import dk.dbc.updateservice.integration.service.RecordData;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.bind.JAXBException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

//-----------------------------------------------------------------------------
/**
 *
 * @author stp
 */
public class BibliographicRecordFactory {
    public static BibliographicRecord loadResource( String resourceName ) throws ParserConfigurationException, SAXException, IOException {
        return loadResource( BibliographicRecordFactory.class.getResourceAsStream( resourceName ) );
    }

    public static BibliographicRecord loadMarcRecordInLineFormat( File file ) throws ParserConfigurationException, SAXException, IOException, JAXBException {
        FileInputStream stream = null;

        try {
            stream = new FileInputStream( file );
            MarcRecord marcRecord = MarcRecordFactory.readRecord( IOUtils.readAll( stream, "UTF-8" ) );

            BibliographicRecord bibRecord = new BibliographicRecord();
            bibRecord.setRecordSchema( "info:lc/xmlns/marcxchange-v1" );
            bibRecord.setRecordPacking( "xml" );

            RecordData recData = new RecordData();
            recData.getContent().add( "\n" );
            recData.getContent().add( MarcConverter.convertToMarcXChangeAsDocument( marcRecord ).getDocumentElement() );
            recData.getContent().add( "\n" );
            bibRecord.setRecordData( recData );

            ExtraRecordData extraRecordData = new ExtraRecordData();
            bibRecord.setExtraRecordData( extraRecordData );

            return bibRecord;
        }
        finally {
            if( stream != null ) {
                stream.close();
            }
        }
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
