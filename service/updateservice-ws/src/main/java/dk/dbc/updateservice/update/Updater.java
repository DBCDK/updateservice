//-----------------------------------------------------------------------------
package dk.dbc.updateservice.update;

//-----------------------------------------------------------------------------
import dk.dbc.iscrum.records.MarcReader;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcXchangeFactory;
import dk.dbc.iscrum.records.marcxchange.CollectionType;
import dk.dbc.rawrepo.RawRepoDAO;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.Stateless;
import javax.ejb.LocalBean;
import javax.sql.DataSource;
import javax.xml.bind.JAXBContext;
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

    public void updateRecord( MarcRecord record ) throws SQLException, ClassNotFoundException, JAXBException {
        logger.entry( record );
        
        try( Connection conn = dataSource.getConnection() ) {
            final RawRepoDAO rawRepoDAO = RawRepoDAO.newInstance( conn );
           
            byte[] content = encodeRecord( record );
            
            String recId = MarcReader.getRecordValue( record, "001", "a" );
            int libraryId = Integer.parseInt( MarcReader.getRecordValue( record, "001", "b" ) );
            String parentId = MarcReader.getRecordValue( record, "014", "a" );
             
            saveRecord( rawRepoDAO, content, recId, libraryId, parentId );            
        }
        
        logger.exit();
    }
    
    private byte[] encodeRecord( MarcRecord record ) throws JAXBException {
        MarcXchangeFactory marcXchangeFactory = new MarcXchangeFactory();
        CollectionType marcXhangeCollectionType = marcXchangeFactory.createMarcXchangeFromMarc( record );

        JAXBContext jc = JAXBContext.newInstance( CollectionType.class );
        Marshaller marshaller = jc.createMarshaller();
        marshaller.setProperty( Marshaller.JAXB_SCHEMA_LOCATION, "http://www.loc.gov/standards/iso25577/marcxchange-2-0.xsd" );
        
        ByteArrayOutputStream recData = new ByteArrayOutputStream();
        marshaller.marshal( marcXhangeCollectionType, recData );
        
        return recData.toByteArray();
    }
    
    private void saveRecord( RawRepoDAO rawRepo, byte[] content, String recId, int libraryId, String parentId ) throws SQLException {
        final Record rawRepoRecord = rawRepo.fetchRecord( recId, libraryId );
        rawRepoRecord.setContent( content );
        rawRepo.saveRecord( rawRepoRecord );

        final HashSet<RecordId> references = new HashSet<>();
        if( !parentId.isEmpty() ) {
            references.add( new RecordId( parentId, libraryId ) );
        }
        rawRepo.setRelationsFrom( rawRepoRecord.getId(), references );
        rawRepo.changedRecord( PROVIDER, rawRepoRecord.getId() );        
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
    
    @Resource( name = JNDI_JDBC_RAW_REPO_NAME )
    private DataSource dataSource;
}
