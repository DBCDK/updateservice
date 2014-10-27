//-----------------------------------------------------------------------------
package dk.dbc.updateservice.update;

//-----------------------------------------------------------------------------
import dk.dbc.holdingsitems.HoldingsItemsDAO;
import dk.dbc.iscrum.records.MarcConverter;
import dk.dbc.iscrum.records.MarcField;
import dk.dbc.iscrum.records.MarcReader;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcXchangeFactory;
import dk.dbc.iscrum.records.marcxchange.CollectionType;
import dk.dbc.iscrum.records.marcxchange.ObjectFactory;
import dk.dbc.iscrum.records.marcxchange.RecordType;
import dk.dbc.iscrum.utils.IOUtils;
import dk.dbc.iscrumjs.ejb.JSEngine;
import dk.dbc.iscrumjs.ejb.JavaScriptException;
import dk.dbc.rawrepo.RawRepoDAO;
import dk.dbc.rawrepo.RawRepoException;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import javax.sql.DataSource;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import dk.dbc.updateservice.ws.JNDIResources;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

//-----------------------------------------------------------------------------
/**
 * EJB to update a record in the rawrepo database.
 * <p>
 * 
 * 
 * @author stp
 */
@Stateless
@LocalBean
public class Updater {
    //-------------------------------------------------------------------------
    //              Constructors
    //-------------------------------------------------------------------------

    /**
     * Constructs a new instance with a null rawRepoDAO.
     */
    public Updater() {
        this( null, null, null );
    }
    
    /**
     * Constructs with a rawRepoDAO.
     * 
     * <dl>
     *      <dt>Note</dt>
     *      <dd>
     *          This constructor is added to "inject" a rawRepoDAO from 
     *          unit tests.
     *      </dd>
     * </dl>
     * 
     * @param rawRepoDAO      The rawRepoDAO to "inject".
     * @param holdingItemsDAO The holdingsItems to "inject".
     * @param recordsHandler  The records handler to use.
     * 
     */
    public Updater( RawRepoDAO rawRepoDAO, HoldingsItemsDAO holdingItemsDAO, LibraryRecordsHandler recordsHandler ) {
        this.rawRepoDAO = rawRepoDAO;
        this.holdingItemsDAO = holdingItemsDAO;
        this.recordsHandler = recordsHandler;
    }
    
    //-------------------------------------------------------------------------
    //              Java EE
    //-------------------------------------------------------------------------

    /**
     * Initialization of the EJB after it has been created by the JavaEE 
     * container.
     * <p>
     * It simply initialize the XLogger instance for logging.
     */
    @PostConstruct
    public void init() {
        logger = XLoggerFactory.getXLogger( this.getClass() );
        
        if( rawRepoDataSource == null ) {
            logger.error( "DataSource for the RawRepo is not initialized." );
            return;
        }
        
        if( holdingItemsDataSource == null ) {
            logger.error( "DataSource for the HoldingsItems is not initialized." );
            return;
        }

        if( rawRepoDAO == null ) {
            try {
                rawRepoDAO = RawRepoDAO.newInstance( rawRepoDataSource.getConnection() );
            }
            catch( RawRepoException | SQLException ex ) {
                logger.error( "Unable to initialize the rawRepo", ex );
			}
        }
        
        if( holdingItemsDAO == null ) {
            try {
                holdingItemsDAO = HoldingsItemsDAO.newInstance( holdingItemsDataSource.getConnection() );
            }
            catch( ClassNotFoundException | SQLException ex ) {
                logger.error( "Unable to initialize the holdingsitems database source", ex );
            }
        }

        if( recordsHandler == null ) {
            if ( jsProvider != null ) {
                try {
                    jsProvider.initialize( IOUtils.loadProperties( Updater.class.getClassLoader(),
                            ";", "dk/dbc/updateservice/ws/settings.properties",
                            "javascript/iscrum/settings.properties" ) );
                } catch ( IOException | IllegalArgumentException ex ) {
                    logger.catching( XLogger.Level.WARN, ex );
                }
            }
            this.recordsHandler = new LibraryRecordsHandler( jsProvider );
        }
    }

    //-------------------------------------------------------------------------
    //              Business logic
    //-------------------------------------------------------------------------
    
    /**
     * Updates the record in rawrepo.
     * 
     * @param record The record to update.
     * 
     * @throws UpdateException thrown in case the business errors. For instance a parent record does not exist.
     */
    public void updateRecord( MarcRecord record ) throws UpdateException {
        logger.entry( record );
        
        try {
            String recId = MarcReader.getRecordValue( record, "001", "a" );
            int libraryId = Integer.parseInt( MarcReader.getRecordValue( record, "001", "b" ) );

            if( libraryId == RawRepoDAO.COMMON_LIBRARY ) {
                updateCommonRecord( record );
            }
            else {
                if( rawRepoDAO.recordExists( recId, RawRepoDAO.COMMON_LIBRARY ) ) {
                    Record commonRecord = rawRepoDAO.fetchRecord( recId, RawRepoDAO.COMMON_LIBRARY );
                    saveLibraryExtendedRecord( commonRecord, record );
                }
                else {
                    updateLibraryLocalRecord( record );
                }
            }
        }
        catch( RawRepoException | SQLException | JAXBException | UnsupportedEncodingException | JavaScriptException ex ) {
            logger.error( "Update error: " + ex.getMessage(), ex );
            throw new UpdateException( ex.getMessage(), ex );
        }
        
        logger.exit();
    }
    
    void updateCommonRecord( MarcRecord record ) throws SQLException, UpdateException, JAXBException, UnsupportedEncodingException, JavaScriptException, RawRepoException {
        logger.entry( record );
        
        String recId = MarcReader.getRecordValue( record, "001", "a" );
        int libraryId = Integer.parseInt( MarcReader.getRecordValue( record, "001", "b" ) );
        String parentId = MarcReader.getRecordValue( record, "014", "a" );
        
        logger.info(  "Record id: [{}:{}]", recId, libraryId );
        logger.info(  "Parent Record id: {}", parentId );
        
        // Fetch ids of local libraries for recId.
        // Note: We remove RawRepoDAO.COMMON_LIBRARY from the set because the interface
        // 		 returns *all* libraries and not only local libraries.
        Set<Integer> localLibraries = rawRepoDAO.allAgenciesForBibliographicRecordId( recId );
        localLibraries.remove( RawRepoDAO.COMMON_LIBRARY );
        logger.info(  "Local libraries: {}", localLibraries );
        
        if( !rawRepoDAO.recordExists( recId, libraryId ) && !localLibraries.isEmpty() ) {
            logger.error( "Try to update common record [{}:{}], but local records exists: {}",
                          recId, libraryId, recId, localLibraries );
            throw new UpdateException( String.format( "It is not posible to update a common record when local records exists: {}", localLibraries ) );
        }
        
        MarcRecord oldRecord = null;
        if( rawRepoDAO.recordExists( recId, libraryId ) ) {
            Record rawRecord = rawRepoDAO.fetchRecord( recId, libraryId );
            if( rawRecord.getContent() != null ) {
                oldRecord = decodeRecord( rawRecord.getContent() );
            }
        }
        
        if( oldRecord == null ) {
            oldRecord = new MarcRecord();
            oldRecord.setFields(  new ArrayList<MarcField>() );
        }
        
        logger.info( "Current record in rawrepo:\n{}", oldRecord.toString() );
        logger.info( "Saves record:\n{}", record.toString() );
        saveRecord( encodeRecord( record ), recId, libraryId, parentId );
        
        if( recordsHandler.hasClassificationData( oldRecord ) && recordsHandler.hasClassificationData( record ) ) {
            if( recordsHandler.hasClassificationsChanged( oldRecord, record ) ) {
                logger.info(  "Classifications was changed for common record [{}:{}]", recId, libraryId );
                Set<Integer> holdingsLibraries = holdingItemsDAO.getAgenciesThatHasHoldingsFor( recId );
                for( Integer id : holdingsLibraries ) {
                    logger.info(  "Local library for record: {}", id );
                    if( rawRepoDAO.recordExists( recId, id ) ) {
                        Record extRecord = rawRepoDAO.fetchRecord( recId, id );
                        MarcRecord extRecordData = decodeRecord( extRecord.getContent() );
                        if( !recordsHandler.hasClassificationData( extRecordData ) ) {
                            logger.info( "Update classifications for extended library record: [{}:{}]", recId, id );
                            updateLibraryExtendedRecord( rawRepoDAO.fetchRecord( recId, libraryId ), oldRecord, decodeRecord( extRecord.getContent() ) );
                        }
                        else {
                            logger.info( "Extended library record, [{}:{}], has its own classifications. Record not updated.", recId, id );
                            enqueueRecord( extRecord.getId() );                        
                        }
                    }
                    else {
                        logger.info( "Create new extended library record: [{}:{}].", recId, id );
                        createLibraryExtendedRecord( rawRepoDAO.fetchRecord( recId, libraryId ), oldRecord, id );
                    }
                }
            }
        }
        enqueueExtendedRecords( new RecordId( recId, libraryId ) );
        
        logger.exit();
    }
    
    void updateLibraryLocalRecord( MarcRecord record ) throws SQLException, UpdateException, JAXBException, UnsupportedEncodingException, RawRepoException {
        String recId = MarcReader.getRecordValue( record, "001", "a" );
        int libraryId = Integer.parseInt( MarcReader.getRecordValue( record, "001", "b" ) );
        String parentId = MarcReader.getRecordValue( record, "014", "a" );
        
        saveRecord( encodeRecord( record ), recId, libraryId, parentId );        
    }
    
    void createLibraryExtendedRecord( Record commonRecord, MarcRecord oldCommonRecordData, int libraryId ) throws SQLException, UpdateException, JAXBException, UnsupportedEncodingException, JavaScriptException, RawRepoException {
        logger.entry( commonRecord, oldCommonRecordData, libraryId );
        MarcRecord extRecord = recordsHandler.createLibraryExtendedRecord( oldCommonRecordData, libraryId );
        
        String recId = MarcReader.getRecordValue( extRecord, "001", "a" );
        logger.info( "Record id: [{}:{}]", recId, libraryId );
        
        logger.info(  "Save new library extended record:\n{}", extRecord );
        saveRecord( encodeRecord( extRecord ), recId, libraryId, "" );
        enqueueRecord( new RecordId( recId, libraryId ) );
        
        logger.exit();
    }

    void updateLibraryExtendedRecord( Record commonRecord, MarcRecord oldCommonRecordData, MarcRecord record ) throws SQLException, UpdateException, JAXBException, UnsupportedEncodingException, JavaScriptException, RawRepoException {
        logger.entry( commonRecord, oldCommonRecordData, record );
        MarcRecord extRecord = recordsHandler.updateLibraryExtendedRecord( oldCommonRecordData, record );
        
        String recId = MarcReader.getRecordValue( record, "001", "a" );
        int libraryId = Integer.parseInt( MarcReader.getRecordValue( record, "001", "b" ) );
        String parentId = MarcReader.getRecordValue( record, "014", "a" );
        
        logger.info( "Record id: [{}:{}]", recId, libraryId );
        logger.info( "Parent Record id: {}", parentId );
        logger.info( "Original library extended record:\n{}", record );
        logger.info( "Overwrite existing library extended record:\n{}", extRecord );
        saveRecord( encodeRecord( extRecord ), recId, libraryId, parentId );
        enqueueRecord( new RecordId( recId, libraryId ) );

        logger.exit();
    }

    void saveLibraryExtendedRecord( Record commonRecord, MarcRecord record ) throws UnsupportedEncodingException, JavaScriptException, SQLException, UpdateException, JAXBException, RawRepoException {
        logger.entry( commonRecord, record );
        MarcRecord extRecord = record;
        
        if( commonRecord.getContent() != null ) {
            MarcRecord commonRecData = decodeRecord( commonRecord.getContent() );
            extRecord = recordsHandler.correctLibraryExtendedRecord( commonRecData, record );
        }
        
        String recId = MarcReader.getRecordValue( record, "001", "a" );
        int libraryId = Integer.parseInt( MarcReader.getRecordValue( record, "001", "b" ) );
        String parentId = MarcReader.getRecordValue( record, "014", "a" );

        logger.info( "Record id: [{}:{}]", recId, libraryId );
        logger.info( "Parent Record id: {}", parentId );
        logger.info( "Original library extended record:\n{}", record );

        if( extRecord.getFields().isEmpty() ) {
            logger.info( "Overwrite existing library extended record: Empty!" );
            if( rawRepoDAO.recordExists( recId, libraryId ) ) {
                saveRecord( null, recId, libraryId, parentId );
            }
        }
        else {
            logger.info( "Overwrite existing library extended record:\n{}", extRecord );
            saveRecord( encodeRecord( extRecord ), recId, libraryId, parentId );
        }
        enqueueRecord( new RecordId( recId, libraryId ) );

        logger.exit();
    }
    
    public MarcRecord decodeRecord( byte[] bytes ) throws UnsupportedEncodingException {
        return MarcConverter.convertFromMarcXChange( new String( bytes, "UTF-8" ) );                
    }
    
    /**
     * Encodes the record as marcxchange.
     * 
     * @param record The record to encode.
     * 
     * @return The encoded record as a sequence of bytes.
     * 
     * @throws JAXBException if the record can not be encoded in marcxchange.
     * @throws UnsupportedEncodingException if the record can not be encoded in UTF-8
     */
    byte[] encodeRecord( MarcRecord record ) throws JAXBException, UnsupportedEncodingException {
        logger.entry( record );
        
        if( record.getFields().isEmpty() ) {
            return null;
        }
        
        RecordType marcXchangeType = MarcXchangeFactory.createMarcXchangeFromMarc( record );

        ObjectFactory objectFactory = new ObjectFactory();
        JAXBElement<RecordType> jAXBElement = objectFactory.createRecord( marcXchangeType );
        
        JAXBContext jc = JAXBContext.newInstance( CollectionType.class );
        Marshaller marshaller = jc.createMarshaller();
        marshaller.setProperty( Marshaller.JAXB_SCHEMA_LOCATION, "http://www.loc.gov/standards/iso25577/marcxchange-1-1.xsd" );
        
        StringWriter recData = new StringWriter();        
        marshaller.marshal( jAXBElement, recData );
        
        logger.info(  "Marshelled record: {}", recData.toString() );
        byte[] result = recData.toString().getBytes( "UTF-8" );
        
        logger.exit( result );
        return result;
    }
          
    /**
     * Saves the record in a rawrepo.
     * 
     * @param rawRepo   The rawrepo to use.
     * @param content   The encoded content of the record.
     * @param recId     The id from the record.
     * @param libraryId The library number from the record.
     * @param parentId  The parent id to any parent record, from the record.
     * 
     * @throws SQLException JDBC errors.
     * @throws UpdateException if a parent record does not exist.
     * @throws RawRepoException 
     */
    private void saveRecord( byte[] content, String recId, int libraryId, String parentId ) throws SQLException, UpdateException, RawRepoException {
        logger.entry( content, recId, libraryId, parentId );
                
        if( content == null ) {
            if( !rawRepoDAO.recordExists( recId, libraryId ) ) {
                String err = String.format( "Record [%s|%s] can not be deleted, because it does not exist.", recId, libraryId );
                logger.warn( err );
                throw new UpdateException( err );
            }

            logger.info( "Deleting record [{}:{}]", recId, libraryId );
            rawRepoDAO.deleteRecord( new RecordId( recId, libraryId ) );
            return;
        }
        
        if( !parentId.isEmpty() && !rawRepoDAO.recordExists( parentId, libraryId ) ) {
            String err = String.format( "Record [%s|%s] points to [%s|%s], but the referenced record does not exist in this rawrepo.", recId, libraryId, parentId, libraryId );
            logger.warn( err );
            throw new UpdateException( err );
        }
        
        final Record rawRepoRecord = rawRepoDAO.fetchRecord( recId, libraryId );
        rawRepoRecord.setContent( content );
        rawRepoDAO.saveRecord( rawRepoRecord );

        if( RawRepoDAO.COMMON_LIBRARY == libraryId ) {
            if( !parentId.isEmpty() ) {
                linkToRecord( rawRepoRecord.getId(), new RecordId( parentId, libraryId ) );
            }
        }
        else {
            if( rawRepoDAO.recordExists( recId, RawRepoDAO.COMMON_LIBRARY ) ) {
                logger.info( "Linker record [{}] -> [{}]", rawRepoRecord.getId(), new RecordId( recId, RawRepoDAO.COMMON_LIBRARY ) );
                linkToRecord( rawRepoRecord.getId(), new RecordId( recId, RawRepoDAO.COMMON_LIBRARY ) );
            }
        }
                
        rawRepoDAO.changedRecord( PROVIDER, rawRepoRecord.getId() );
        logger.exit();
    }
    
    /**
     * Links a record to another record in the rawrepo.
     * 
     * @param id       The id of the record to link from.
     * @param refer_id The id of the record to link to.
     * 
     * @throws SQLException JDBC errors.
     * @throws RawRepoException 
     */
    private void linkToRecord( RecordId id, RecordId refer_id ) throws SQLException, RawRepoException {
        final HashSet<RecordId> references = new HashSet<>();
        references.add( refer_id );
        rawRepoDAO.setRelationsFrom( id, references );
    }
    
    private void enqueueRecord( RecordId id ) throws SQLException {
        logger.entry( id );
        
        logger.info(  "Enqueue record: [{}:{}]", id.getBibliographicRecordId(), id.getAgencyId() );
        //rawRepoDAO.enqueue( id, PROVIDER, true, true );
        logger.exit();
    }
    
    private void enqueueExtendedRecords( RecordId commonRecId ) throws SQLException, RawRepoException {
        Set<Integer> extLibraries = rawRepoDAO.allAgenciesForBibliographicRecordId( commonRecId.getBibliographicRecordId() );
        for( Integer libId : extLibraries ) {
            enqueueRecord( new RecordId( commonRecId.getBibliographicRecordId(), libId ) );
        }
    }
    
    //------------------------------------------------------------------------
    //              Testing
    //------------------------------------------------------------------------
    
    public void setLogger( XLogger logger ) {
        this.logger = logger;
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    /**
     * Logger instance to write entries to the log files.
     */
    private XLogger logger;    

    /**
     * Rawrepo requires a provider name for the service that changes its 
     * content. This constant defines the provider name for the update web 
     * service.
     */
    static final String PROVIDER = "opencataloging-update";

    @EJB
    private JSEngine jsProvider;
    
    /**
     * Injected DataSource to access the rawrepo database.
     */
    @Resource( lookup = JNDIResources.JDBC_RAW_REPO_NAME)
    private DataSource rawRepoDataSource;
    
    /**
     * Injected DataSource to access the holdingitems database.
     */
    @Resource( lookup = JNDIResources.JDBC_HOLDINGITEMS_NAME )
    private DataSource holdingItemsDataSource;

    /**
     * Class instance of the rawrepo to use.
     */
    private RawRepoDAO rawRepoDAO;
    
    /**
     * Class instance of the holdingsitems DAO to use.
     */
    private HoldingsItemsDAO holdingItemsDAO;
    private LibraryRecordsHandler recordsHandler;
}
