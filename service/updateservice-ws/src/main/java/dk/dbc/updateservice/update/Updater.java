//-----------------------------------------------------------------------------
package dk.dbc.updateservice.update;

//-----------------------------------------------------------------------------
import dk.dbc.holdingsitems.HoldingsItemsDAO;
import dk.dbc.iscrum.records.MarcFactory;
import dk.dbc.iscrum.records.MarcField;
import dk.dbc.iscrum.records.MarcReader;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcXchangeFactory;
import dk.dbc.iscrum.records.marcxchange.CollectionType;
import dk.dbc.iscrum.records.marcxchange.ObjectFactory;
import dk.dbc.rawrepo.RawRepoDAO;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
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
import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import javax.sql.DataSource;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
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
            catch( ClassNotFoundException | SQLException ex ) {
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
            this.recordsHandler = new LibraryRecordsHandler();
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
                    updateLibraryExtendedRecord( commonRecord, decodeRecord( commonRecord.getContent() ), record );
                }
                else {
                    updateLibraryLocalRecord( record );
                }
            }
        }
        catch( SQLException | JAXBException | UnsupportedEncodingException ex ) {
            logger.error( "Update error: " + ex.getMessage(), ex );
            throw new UpdateException( ex.getMessage(), ex );
        }
        
        logger.exit();
    }
    
    void updateCommonRecord( MarcRecord record ) throws SQLException, UpdateException, JAXBException, UnsupportedEncodingException {
        String recId = MarcReader.getRecordValue( record, "001", "a" );
        int libraryId = Integer.parseInt( MarcReader.getRecordValue( record, "001", "b" ) );
        String parentId = MarcReader.getRecordValue( record, "014", "a" );
        
        MarcRecord oldRecord = null;
        if( rawRepoDAO.recordExists( recId, libraryId ) ) {
            Record rawRecord = rawRepoDAO.fetchRecord( recId, libraryId );
            if( rawRecord.hasContent() ) {
                oldRecord = decodeRecord( rawRecord.getContent() );
            }
        }
        
        if( oldRecord == null ) {
            oldRecord = new MarcRecord();
            oldRecord.setFields(  new ArrayList<MarcField>() );
        }
        
        saveRecord( encodeRecord( record ), recId, libraryId, parentId );
        
        if( recordsHandler.hasClassificationsChanged( oldRecord, record ) ) {
            Set<Integer> holdingsLibraries = holdingItemsDAO.getAgenciesThatHasHoldingsFor( recId );
            for( Integer id : holdingsLibraries ) {
                if( rawRepoDAO.recordExists( recId, id ) ) {
                    Record extRecord = rawRepoDAO.fetchRecord( recId, id );
                    updateLibraryExtendedRecord( rawRepoDAO.fetchRecord( recId, libraryId ), oldRecord, decodeRecord( extRecord.getContent() ) );
                }
                else {
                    createLibraryExtendedRecord( rawRepoDAO.fetchRecord( recId, libraryId ), oldRecord, id );
                }
            }
        }
    }
    
    void updateLibraryLocalRecord( MarcRecord record ) throws SQLException, UpdateException, JAXBException, UnsupportedEncodingException {
        String recId = MarcReader.getRecordValue( record, "001", "a" );
        int libraryId = Integer.parseInt( MarcReader.getRecordValue( record, "001", "b" ) );
        String parentId = MarcReader.getRecordValue( record, "014", "a" );
        
        saveRecord( encodeRecord( record ), recId, libraryId, parentId );        
    }
    
    void createLibraryExtendedRecord( Record commonRecord, MarcRecord oldCommonRecordData, int libraryId ) throws SQLException, UpdateException, JAXBException, UnsupportedEncodingException {
        MarcRecord extRecord = recordsHandler.createLibraryExtendedRecord( oldCommonRecordData, libraryId );
        
        String recId = MarcReader.getRecordValue( extRecord, "001", "a" );
        
        saveRecord( encodeRecord( extRecord ), recId, libraryId, "" );
        rawRepoDAO.enqueue( new RecordId( recId, libraryId ), PROVIDER, true, true );
    }

    void updateLibraryExtendedRecord( Record commonRecord, MarcRecord oldCommonRecordData, MarcRecord record ) throws SQLException, UpdateException, JAXBException, UnsupportedEncodingException {
        MarcRecord extRecord = recordsHandler.updateLibraryExtendedRecord( oldCommonRecordData, record );
        
        String recId = MarcReader.getRecordValue( record, "001", "a" );
        int libraryId = Integer.parseInt( MarcReader.getRecordValue( record, "001", "b" ) );
        String parentId = MarcReader.getRecordValue( record, "014", "a" );
        
        saveRecord( encodeRecord( extRecord ), recId, libraryId, parentId );
        rawRepoDAO.enqueue( new RecordId( recId, libraryId ), PROVIDER, true, true );
    }
    
    MarcRecord decodeRecord( byte[] bytes ) throws UnsupportedEncodingException {
        String recData = new String( bytes, "UTF-8" );

        MarcFactory factory = new MarcFactory();
        List<MarcRecord> records = factory.createFromMarcXChange( new StringReader( recData ) );
        
        if( records.size() != 1 ) {
            return null;
        }
        
        return records.get( 0 );
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
        
        MarcXchangeFactory marcXchangeFactory = new MarcXchangeFactory();
        CollectionType marcXchangeCollectionType = marcXchangeFactory.createMarcXchangeFromMarc( record );

        ObjectFactory objectFactory = new ObjectFactory();
        JAXBElement<CollectionType> jAXBElement = objectFactory.createCollection( marcXchangeCollectionType );
        
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
     */
    private void saveRecord( byte[] content, String recId, int libraryId, String parentId ) throws SQLException, UpdateException {
        logger.entry( content, recId, libraryId, parentId );
                
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
     * @param rawRepo  rawrepo to use.
     * @param id       The id of the record to link from.
     * @param refer_id The id of the record to link to.
     * 
     * @throws SQLException JDBC errors.
     */
    private void linkToRecord( RecordId id, RecordId refer_id ) throws SQLException {
        final HashSet<RecordId> references = new HashSet<>();
        references.add( refer_id );
        rawRepoDAO.setRelationsFrom( id, references );
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
    static final String PROVIDER = "updateservice";
       
    /**
     * Name of the JDBC resource that points to the rawrepo database.
     */
    private static final String JNDI_JDBC_RAW_REPO_NAME = "jdbc/updateservice/raw-repo";

    /**
     * Name of the JDBC resource that points to the holdingitems database.
     */
    private static final String JNDI_JDBC_HOLDINGITEMS_NAME = "jdbc/updateservice/holdingitems";

    /**
     * Injected DataSource to access the rawrepo database.
     */
    @Resource( lookup = JNDI_JDBC_RAW_REPO_NAME )
    private DataSource rawRepoDataSource;
    
    /**
     * Injected DataSource to access the holdingitems database.
     */
    @Resource( lookup = JNDI_JDBC_HOLDINGITEMS_NAME )
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
