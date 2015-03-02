package dk.dbc.updateservice.integration.testcase;

import dk.dbc.holdingsitems.HoldingsItemsDAO;
import dk.dbc.holdingsitems.HoldingsItemsException;
import dk.dbc.holdingsitems.RecordCollection;
import dk.dbc.iscrum.records.*;
import dk.dbc.iscrum.utils.IOUtils;
import dk.dbc.iscrum.utils.json.Json;
import dk.dbc.rawrepo.RawRepoDAO;
import dk.dbc.rawrepo.RawRepoException;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.integration.BibliographicRecordFactory;
import dk.dbc.updateservice.integration.ExternWebServers;
import dk.dbc.updateservice.integration.UpdateServiceCaller;
import dk.dbc.updateservice.integration.service.*;
import dk.dbc.updateservice.update.RawRepo;
import junit.framework.Assert;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import org.xml.sax.SAXException;

import javax.xml.bind.JAXBException;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.*;

/**
 * Created by stp on 24/02/15.
 */
public class TestcaseRunner {
    public TestcaseRunner( File dir, String trackingId ) {
        this.dir = dir;
        this.trackingId = trackingId;
    }

    public Record saveRecord( RawRepoDAO dao, String filename, String mimetype ) throws IOException, RawRepoException, JAXBException {
        logger.entry();

        try {
            MarcRecord record = loadRecord( filename );
            RecordId recId = getRecordId( record );

            Record newRecord = dao.fetchRecord( recId.getBibliographicRecordId(), recId.getAgencyId() );
            newRecord.setDeleted( false );
            newRecord.setMimeType( mimetype );
            newRecord.setContent( new RawRepo().encodeRecord( record ) );
            dao.saveRecord( newRecord );

            return newRecord;
        }
        finally {
            logger.exit();
        }
    }

    public void linkRecord( RawRepoDAO dao, String fromFilename, String toFilename ) throws IOException, RawRepoException, JAXBException {
        logger.entry();

        try {
            MarcRecord fromRecord = loadRecord( fromFilename );
            MarcRecord toRecord = loadRecord( toFilename );

            final HashSet<RecordId> references = new HashSet<>();
            references.add( getRecordId( toRecord ) );

            dao.setRelationsFrom( getRecordId( fromRecord ), references );
        }
        finally {
            logger.exit();
        }
    }

    public void linkSibling( RawRepoDAO dao, String commonFilename, String enrichmentFilename ) throws IOException, RawRepoException, JAXBException {
        linkRecord( dao, enrichmentFilename, commonFilename );
    }

    public void linkChildren( RawRepoDAO dao, String mainFilename, String volumeFilename ) throws IOException, RawRepoException, JAXBException {
        linkRecord( dao, volumeFilename, mainFilename );
    }

    public void addHoldings( HoldingsItemsDAO dao, String recordFilename, List<Integer> agencies ) throws IOException, HoldingsItemsException {
        logger.entry();

        try {
            RecordId recordId = getRecordId( loadRecord( recordFilename ) );

            for( Integer agencyId : agencies ) {
                RecordCollection collection = new RecordCollection( recordId.getBibliographicRecordId(), agencyId, "issue", dao );
                collection.save();
            }
        }
        finally {
            logger.exit();
        }
    }

    public UpdateRecordResult sendRequest() throws IOException, JAXBException, SAXException, ParserConfigurationException {
        logger.entry();

        try {
            TestcaseConfig config = loadConfig();

            ExternWebServers servers = new ExternWebServers( this.dir );
            servers.startServers();

            try {
                UpdateRecordRequest request = new UpdateRecordRequest();

                Authentication auth = new Authentication();
                auth.setUserIdAut( config.getAuthentication().getUserIdAut() );
                auth.setGroupIdAut( config.getAuthentication().getGroupIdAut() );
                auth.setPasswordAut( config.getAuthentication().getPasswordAut() );

                request.setAuthentication( auth );
                request.setSchemaName( config.getTemplateName() );
                request.setTrackingId( trackingId );

                File recordFile = new File( dir.getCanonicalPath() + "/request.marc" );
                request.setBibliographicRecord( BibliographicRecordFactory.loadMarcRecordInLineFormat( recordFile ) );

                logger.debug( "Headers: {}", config.getHeaders() );

                String[] X_FORWARDED_KEYS = {
                    X_FORWARDED_FOR_HEADER,
                    X_FORWARDED_HOST_HEADER,
                    X_FORWARDED_SERVER_HEADER
                };

                for( String key : X_FORWARDED_KEYS ) {
                    if( config.getHeaders().containsKey( key ) ) {
                        String value = config.getHeaders().get( key ).toString();
                        config.getHeaders().put( key, Collections.singletonList( value ) );
                    }
                }

                UpdateServiceCaller caller = new UpdateServiceCaller( config.getHeaders() );
                return caller.updateRecord( request );
            }
            finally {
                servers.stopServers();
            }
        }
        finally {
            logger.exit();
        }
    }

    public void checkResponseForUpdateIsOk( UpdateRecordResult response ) {
        logger.entry();

        try {
            assertNotNull( response );
            if( response.getValidateInstance() != null &&
                response.getValidateInstance().getValidateEntry() != null &&
                !response.getValidateInstance().getValidateEntry().isEmpty() )
            {
                ValidateEntry entry = response.getValidateInstance().getValidateEntry().get( 0 );
                Assert.fail( String.format( "Update error at field %s: %s", entry.getOrdinalPositionOfField(), entry.getMessage() ) );
            }
            assertNull( response.getValidateInstance() );

            assertEquals( UpdateStatusEnum.OK, response.getUpdateStatus() );
        }
        finally {
            logger.exit();
        }
    }

    public void checkRawRepoRecord( RawRepoDAO dao, String filename, String mimetype ) {
        logger.entry();

        try {
            MarcRecord marcRecord = loadRecord( filename );
            RecordId recId = getRecordId( marcRecord );

            assertTrue( dao.recordExists( recId.getBibliographicRecordId(), recId.getAgencyId() ) );

            Record rawRecord = dao.fetchRecord( recId.getBibliographicRecordId(), recId.getAgencyId() );
            assertNotNull( rawRecord );
            assertFalse( rawRecord.isDeleted() );
            assertEquals( mimetype, rawRecord.getMimeType() );
            assertNotNull( rawRecord.getContent() );

            MarcRecord rawRepoMarcRecord = MarcConverter.convertFromMarcXChange( new String( rawRecord.getContent(), "UTF-8" ) );
            if( marcRecord.getFields().size() != rawRepoMarcRecord.getFields().size() ) {
                String message = String.format( "Number of fields differ. Expected record:\n%s\nActual record:\n%s",
                                                marcRecord.toString(), rawRepoMarcRecord.toString() );
                fail( message );
            }

            for( int i = 0; i < marcRecord.getFields().size(); i++ ) {
                MarcField expected = marcRecord.getFields().get( i );
                MarcField actual = rawRepoMarcRecord.getFields().get( i );

                if( expected.getName().equals( "001" ) ) {
                    assertEquals( "Compare field name of 001", expected.getName(), actual.getName() );
                    assertEquals( "Compare indicator of 001", expected.getIndicator(), actual.getIndicator() );

                    for( int k = 0; k < expected.getSubfields().size(); k++ ) {
                        MarcSubField expectedSubField = expected.getSubfields().get( k );

                        if( expectedSubField.getName().equals( "c" ) ) {
                            continue;
                        }
                        if( expectedSubField.getName().equals( "d" ) ) {
                            continue;
                        }

                        MarcSubField actualSubField = actual.getSubfields().get( k );
                        assertEquals( "Compare 001" + expectedSubField.getName(), expectedSubField.toString(), actualSubField.toString() );
                    }
                }
                else {
                    assertEquals( "Compare field " + expected.getName(), expected.toString(), actual.toString() );
                }
            }
        }
        catch( IOException | RawRepoException ex ) {
            String message = String.format( "Unable to check record '%s': %s", filename, ex.getMessage() );
            Assert.fail( message );
            logger.error( message, ex );
        }
        finally {
            logger.exit();
        }
    }

    public void checkRawRecordDoesNotExist( RawRepoDAO dao, String recordFilename, Integer agency ) {
        logger.entry();

        RecordId recordId = null;
        try {
            recordId = getRecordId( loadRecord( recordFilename ) );

            assertFalse( dao.recordExists( recordId.getBibliographicRecordId(), agency ) );
        }
        catch( IOException | RawRepoException ex ) {
            String message = String.format( "Unable to check record [%s:%s] does not exist: %s", recordId.getBibliographicRecordId(), agency, ex.getMessage() );
            Assert.fail( message );
            logger.error( message, ex );
        }
        finally {
            logger.exit();
        }
    }

    public void checkRawRepoSibling( RawRepoDAO dao, String fromFilename, String toFilename ) {
        logger.entry();

        try {
            MarcRecord fromRecord = loadRecord( fromFilename );
            MarcRecord toRecord = loadRecord( toFilename );

            logger.info( "From record: {}", getRecordId( fromRecord ) );
            logger.info( "To record: {}", getRecordId( toRecord ) );

            Set<RecordId> siblings = dao.getRelationsSiblingsToMe( getRecordId( fromRecord ) );
            logger.info( "Siblings: {}", siblings );

            assertTrue( siblings.contains( getRecordId( toRecord ) ) );
        }
        catch( IOException | RawRepoException ex ) {
            String message = String.format( "Unable to check links: %s", ex.getMessage() );
            Assert.fail( message );
            logger.error( message, ex );
        }
        finally {
            logger.exit();
        }
    }

    public void checkRawRepoNoSibling( RawRepoDAO dao, String fromFilename, String toFilename ) {
        logger.entry();

        try {
            MarcRecord fromRecord = loadRecord( fromFilename );
            MarcRecord toRecord = loadRecord( toFilename );

            logger.info( "From record: {}", getRecordId( fromRecord ) );
            logger.info( "To record: {}", getRecordId( toRecord ) );

            Set<RecordId> siblings = dao.getRelationsSiblingsToMe( getRecordId( fromRecord ) );
            logger.info( "Siblings: {}", siblings );

            assertFalse( siblings.contains( getRecordId( toRecord ) ) );
        }
        catch( IOException | RawRepoException ex ) {
            String message = String.format( "Unable to check links: %s", ex.getMessage() );
            Assert.fail( message );
            logger.error( message, ex );
        }
        finally {
            logger.exit();
        }
    }

    public void checkRawRepoChildren( RawRepoDAO dao, String mainFilename, String volumeFilename ) {
        logger.entry();

        try {
            MarcRecord mainRecord = loadRecord( mainFilename );
            MarcRecord volumeRecord = loadRecord( volumeFilename );

            logger.info( "Main record: {}", getRecordId( mainRecord ) );
            logger.info( "Volume record: {}", getRecordId( volumeRecord ) );

            Set<RecordId> children = dao.getRelationsChildren( getRecordId( mainRecord ) );
            logger.info( "Children: {}", children );

            assertTrue( children.contains( getRecordId( volumeRecord ) ) );
        }
        catch( IOException | RawRepoException ex ) {
            String message = String.format( "Unable to check links: %s", ex.getMessage() );
            Assert.fail( message );
            logger.error( message, ex );
        }
        finally {
            logger.exit();
        }
    }

    private TestcaseConfig loadConfig() {
        String filename;

        try {
            filename = dir.getCanonicalPath() + "/tc.json";
            logger.debug( "Using config file: {}", filename );
            return Json.decode( new File( filename ), TestcaseConfig.class );
        }
        catch( IOException ex ) {
            Assert.fail( "Unable to load testcase config: " + dir.getAbsolutePath() + "/tc.json: " + ex.getMessage() );
        }

        return null;
    }

    private MarcRecord loadRecord( String filename ) throws IOException {
        logger.entry();

        try {
            File file = new File( dir.getCanonicalPath() + "/" + filename );
            logger.debug( "Loading record file: {}", file.getCanonicalPath() );
            InputStream stream = new FileInputStream( file );

            return MarcRecordFactory.readRecord( IOUtils.readAll( stream, "UTF-8" ) );
        }
        finally {
            logger.exit();
        }
    }

    private RecordId getRecordId( MarcRecord record ) {
        logger.entry();

        try {
            String recId = MarcReader.getRecordValue( record, "001", "a" );
            int agencyId = Integer.valueOf( MarcReader.getRecordValue( record, "001", "b" ), 10 );

            return new RecordId( recId, agencyId );
        }
        finally {
            logger.exit();
        }
    }

    private final XLogger logger = XLoggerFactory.getXLogger( TestcaseRunner.class );

    private static final String X_FORWARDED_FOR_HEADER = "x-forwarded-for";
    private static final String X_FORWARDED_HOST_HEADER = "x-forwarded-host";
    private static final String X_FORWARDED_SERVER_HEADER = "x-forwarded-server";

    private File dir;
    private String trackingId;
}
