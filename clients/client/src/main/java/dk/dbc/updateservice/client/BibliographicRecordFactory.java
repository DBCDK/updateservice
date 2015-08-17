//-----------------------------------------------------------------------------
package dk.dbc.updateservice.client;

//-----------------------------------------------------------------------------
import dk.dbc.iscrum.records.MarcConverter;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordFactory;
import dk.dbc.iscrum.utils.IOUtils;
import dk.dbc.updateservice.service.api.BibliographicRecord;
import dk.dbc.updateservice.service.api.ExtraRecordData;
import dk.dbc.updateservice.service.api.RecordData;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
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
 * Factory class to construct BibliographicRecord instances.
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

    public static BibliographicRecord loadMarcRecordInLineFormat( String resourceName ) throws ParserConfigurationException, SAXException, IOException, JAXBException {
        return loadMarcRecordInLineFormat( BibliographicRecordFactory.class.getResourceAsStream( resourceName ) );
    }

    public static BibliographicRecord loadMarcRecordInLineFormat( File file ) throws ParserConfigurationException, SAXException, IOException, JAXBException {
        logger.entry();

        try {
            return loadMarcRecordInLineFormat( new FileInputStream( file ) );
        }
        finally {
            logger.exit();
        }
    }

    public static BibliographicRecord loadMarcRecordInLineFormat( InputStream inputStream ) throws ParserConfigurationException, SAXException, IOException, JAXBException {
        logger.entry();

        try {
            return newMarcRecord( MarcRecordFactory.readRecord( IOUtils.readAll( inputStream, "UTF-8" ) ) );
        }
        finally {
            logger.exit();
        }
    }

    public static BibliographicRecord newMarcRecord( MarcRecord record ) throws ParserConfigurationException, SAXException, IOException, JAXBException {
        logger.entry();

        try {
            BibliographicRecord bibRecord = new BibliographicRecord();
            bibRecord.setRecordSchema( "info:lc/xmlns/marcxchange-v1" );
            bibRecord.setRecordPacking( "xml" );

            RecordData recData = new RecordData();
            recData.getContent().add( "\n" );
            recData.getContent().add( MarcConverter.convertToMarcXChangeAsDocument( record ).getDocumentElement() );
            recData.getContent().add( "\n" );
            bibRecord.setRecordData( recData );

            ExtraRecordData extraRecordData = new ExtraRecordData();
            bibRecord.setExtraRecordData( extraRecordData );

            return bibRecord;
        }
        finally {
            logger.exit();
        }
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    private static final XLogger logger = XLoggerFactory.getXLogger( BibliographicRecordFactory.class );
}
