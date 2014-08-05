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
 *
 * @author stp
 */
@Stateless
@LocalBean
public class Updater {
    //-------------------------------------------------------------------------
    //              Java EE
    //-------------------------------------------------------------------------
    
    //!\name Construction
    //@{
    @PostConstruct
    public void init() {
        logger = XLoggerFactory.getXLogger( this.getClass() );        
    }
    //@}

    //-------------------------------------------------------------------------
    //              Business logic
    //-------------------------------------------------------------------------

    public void updateRecord( MarcRecord record ) throws SQLException, ClassNotFoundException, JAXBException, UpdateException, UnsupportedEncodingException {
        logger.entry( record );
        
        try( Connection conn = dataSource.getConnection() ) {
            DatabaseMetaData metaData = conn.getMetaData();
            logger.info( "Connection: Database product name: {}", metaData.getDatabaseProductName() );
            logger.info( "Connection: JDBC driver: {} {}", metaData.getDriverName(), metaData.getDriverVersion() );
            
            final RawRepoDAO rawRepoDAO = RawRepoDAO.newInstance( conn );
           
            byte[] content = encodeRecord( record );
            
            String recId = MarcReader.getRecordValue( record, "001", "a" );
            int libraryId = Integer.parseInt( MarcReader.getRecordValue( record, "001", "b" ) );
            String parentId = MarcReader.getRecordValue( record, "014", "a" );
             
            saveRecord( rawRepoDAO, content, recId, libraryId, parentId );            
        }
        
        logger.exit();
    }
    
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
    
    private void saveRecord( RawRepoDAO rawRepo, byte[] content, String recId, int libraryId, String parentId ) throws SQLException, UpdateException {
        logger.entry( rawRepo, content, recId, libraryId, parentId );
                
        if( !parentId.isEmpty() && !rawRepo.recordExists( parentId, libraryId ) ) {
            String err = String.format( "Record [%s|%s] points to [%s|%s], but the referenced record does not exist in this rawrepo.", recId, libraryId, parentId, libraryId );
            logger.warn( err );
            throw new UpdateException( err );
        }
        
        final Record rawRepoRecord = rawRepo.fetchRecord( recId, libraryId );
        rawRepoRecord.setContent( content );
        rawRepo.saveRecord( rawRepoRecord );

        if( RawRepoDAO.COMMON_LIBRARY == libraryId ) {
            if( !parentId.isEmpty() ) {
                linkToRecord( rawRepo, rawRepoRecord.getId(), new RecordId( parentId, libraryId ) );
            }
        }
        else {
            if( rawRepo.recordExists( recId, RawRepoDAO.COMMON_LIBRARY ) ) {
                logger.info( "Linker record [{}] -> [{}]", rawRepoRecord.getId(), new RecordId( recId, RawRepoDAO.COMMON_LIBRARY ) );
                linkToRecord( rawRepo, rawRepoRecord.getId(), new RecordId( recId, RawRepoDAO.COMMON_LIBRARY ) );
            }
        }
                
        rawRepo.changedRecord( PROVIDER, rawRepoRecord.getId() );
        logger.exit();
    }
    
    private void linkToRecord( RawRepoDAO rawRepo, RecordId id, RecordId refer_id ) throws SQLException {
        final HashSet<RecordId> references = new HashSet<>();
        references.add( refer_id );
        rawRepo.setRelationsFrom( id, references );
    }
    
    //------------------------------------------------------------------------
    //              Testing
    //------------------------------------------------------------------------
    
    //!\name Operations used for testing
    //@{
    /**
     * @brief Test properties. Properties that are only used by JUnit to set the
     * instance of members that is injected by the Java EE Container.
     * @param logger Logger to use for this run
     */
    public void setLogger( XLogger logger ) {
        this.logger = logger;
    }
    //@}

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    private static final String JNDI_JDBC_RAW_REPO_NAME = "jdbc/updateservice/raw-repo";
    private static final String PROVIDER = "updateservice";

    private XLogger logger;    
    
    @Resource( lookup = JNDI_JDBC_RAW_REPO_NAME )
    private DataSource dataSource;
}
