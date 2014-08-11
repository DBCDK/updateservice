//-----------------------------------------------------------------------------
package dk.dbc.updateservice.update;

//-----------------------------------------------------------------------------
import dk.dbc.iscrum.records.MarcReader;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcXchangeFactory;
import dk.dbc.iscrum.records.marcxchange.CollectionType;
import dk.dbc.iscrum.records.marcxchange.ObjectFactory;
import dk.dbc.rawrepo.RawRepoDAO;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;
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
        this( null );
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
     * @param rawRepoDAO The rawRepoDAO to "inject".
     * 
     */
    public Updater( RawRepoDAO rawRepoDAO ) {
        this.rawRepoDAO = rawRepoDAO;
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
        
        if( dataSource == null ) {
            logger.error( "DataSource for the RawRepo is not initialized." );
            return;
        }
        
        if( rawRepoDAO == null ) {
            try {
                rawRepoDAO = RawRepoDAO.newInstance( dataSource.getConnection() );
            }
            catch( ClassNotFoundException | SQLException ex ) {
                logger.error( "Unable to initialize the rawRepo", ex );
            }
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
            byte[] content = encodeRecord( record );

            String recId = MarcReader.getRecordValue( record, "001", "a" );
            int libraryId = Integer.parseInt( MarcReader.getRecordValue( record, "001", "b" ) );
            String parentId = MarcReader.getRecordValue( record, "014", "a" );

            saveRecord( content, recId, libraryId, parentId );
        }
        catch( SQLException | JAXBException | UnsupportedEncodingException ex ) {
            logger.error( "Update error: " + ex.getMessage(), ex );
            throw new UpdateException( ex.getMessage(), ex );
        }
        
        logger.exit();
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
    private byte[] encodeRecord( MarcRecord record ) throws JAXBException, UnsupportedEncodingException {
        logger.entry( record );
        
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
     * Injected DataSource to access the rawrepo database.
     */
    @Resource( lookup = JNDI_JDBC_RAW_REPO_NAME )
    private DataSource dataSource;
    
    /**
     * Class instance of the rawrepo to use.
     */
    private RawRepoDAO rawRepoDAO;
}
