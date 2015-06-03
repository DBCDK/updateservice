//-----------------------------------------------------------------------------
package dk.dbc.updateservice.update;

//-----------------------------------------------------------------------------

import dk.dbc.iscrum.records.MarcConverter;
import dk.dbc.iscrum.records.MarcReader;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcXchangeFactory;
import dk.dbc.iscrum.records.marcxchange.CollectionType;
import dk.dbc.iscrum.records.marcxchange.ObjectFactory;
import dk.dbc.iscrum.utils.ResourceBundles;
import dk.dbc.iscrum.utils.logback.filters.BusinessLoggerFilter;
import dk.dbc.rawrepo.RawRepoDAO;
import dk.dbc.rawrepo.RawRepoException;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.ws.JNDIResources;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.annotation.Resource;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.sql.DataSource;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

//-----------------------------------------------------------------------------

/**
 * EJB to provide access to the RawRepo database.
 */
@Stateless
@TransactionAttribute( TransactionAttributeType.NOT_SUPPORTED )
public class RawRepo {
    //-------------------------------------------------------------------------
    //              Types
    //-------------------------------------------------------------------------

    public enum RecordType {
        COMMON_TYPE,
        ENRICHMENT_TYPE,
        LOCAL_TYPE
    }

    //-------------------------------------------------------------------------
    //              Constructors
    //-------------------------------------------------------------------------

    public RawRepo() {
        this.dataSourceReader = null;
        this.dataSourceWriter = null;
    }

    public RawRepo( DataSource dataSourceReader, DataSource dataSourceWriter ) {
        this.dataSourceReader = dataSourceReader;
        this.dataSourceWriter = dataSourceWriter;
    }

    //-------------------------------------------------------------------------
    //              Business logic
    //-------------------------------------------------------------------------

    /**
     * Returns a Set of local agencies for a record.
     * <p>
     * The agency for common records is not returned in the set.
     *
     * @param record The record to lookup local agencies for.
     *
     * @return A Set of agency ids for the local agencies.
     *
     * @throws UpdateException In case of an error from RawRepo or an SQL exception.
     */
    public Set<Integer> agenciesForRecord( MarcRecord record ) throws UpdateException {
        logger.entry( record );

        Set<Integer> result = null;
        try {
            if( record == null ) {
                throw new IllegalArgumentException( "record can not be null" );
            }

            result = agenciesForRecord( getRecordId( record ) );
            return result;
        }
        finally {
            logger.exit( result );
        }
    }

    /**
     * Returns a Set of local agencies for an record id.
     * <p>
     * The agency for common records is not returned in the set.
     *
     * @param recordId The record id to lookup local agencies for.
     *
     * @return A Set of agency ids for the local agencies.
     *
     * @throws UpdateException In case of an error from RawRepo or an SQL exception.
     */
    public Set<Integer> agenciesForRecord( String recordId ) throws UpdateException {
        logger.entry( recordId );

        Set<Integer> result = null;
        try {
            if( recordId == null ) {
                throw new IllegalArgumentException( "recordId can not be null" );
            }

            if( recordId.isEmpty() ) {
                throw new IllegalArgumentException( "recordId can not be empty" );
            }

            try( Connection conn = dataSourceReader.getConnection() ) {
                RawRepoDAO dao = createDAO( conn );

                result = dao.allAgenciesForBibliographicRecordId( recordId );

                return result;
            }
            catch( RawRepoException | SQLException ex ) {
                logger.error( ex.getMessage(), ex );
                throw new UpdateException( ex.getMessage(), ex );
            }
        }
        finally {
            logger.exit( result );
        }
    }

    public Set<RecordId> children( MarcRecord record ) throws UpdateException {
        logger.entry();

        try {
            if( record == null ) {
                throw new IllegalArgumentException( "record can not be null" );
            }

            RecordId recordId = new RecordId( getRecordId( record ), convertAgencyId( getAgencyId( record ) ) );
            return children( recordId );
        }
        finally {
            logger.exit();
        }
    }

    public Set<RecordId> children( RecordId recordId ) throws UpdateException {
        logger.entry();

        try {
            if( recordId == null ) {
                throw new IllegalArgumentException( "recordId can not be null" );
            }

            try( Connection conn = dataSourceReader.getConnection() ) {
                RawRepoDAO dao = createDAO( conn );

                return dao.getRelationsChildren( recordId );
            }
            catch( RawRepoException | SQLException ex ) {
                logger.error( ex.getMessage(), ex );
                throw new UpdateException( ex.getMessage(), ex );
            }
        }
        finally {
            logger.exit();
        }
    }

    public Set<RecordId> relationsToRecord( MarcRecord record ) throws UpdateException {
        logger.entry();

        try {
            if( record == null ) {
                throw new IllegalArgumentException( "record can not be null" );
            }

            RecordId recordId = new RecordId( getRecordId( record ), convertAgencyId( getAgencyId( record ) ) );
            return relationsToRecord( recordId );
        }
        finally {
            logger.exit();
        }
    }

    public Set<RecordId> relationsToRecord( RecordId recordId ) throws UpdateException {
        logger.entry();

        try {
            if( recordId == null ) {
                throw new IllegalArgumentException( "recordId can not be null" );
            }

            Set<RecordId> relations = new HashSet<>();
            try( Connection conn = dataSourceReader.getConnection() ) {
                RawRepoDAO dao = createDAO( conn );

                relations.addAll( dao.getRelationsChildren( recordId ) );
                relations.addAll( dao.getRelationsSiblingsToMe( recordId ) );

                return relations;
            }
            catch( RawRepoException | SQLException ex ) {
                logger.error( ex.getMessage(), ex );
                throw new UpdateException( ex.getMessage(), ex );
            }
        }
        finally {
            logger.exit();
        }
    }

    /**
     * Fetches the RawRepo record for a MarcRecord.
     * <p>
     * If the record does not exist in the RawRepo then it will be created.
     *
     * @param recId String
     * @param agencyId Integer
     *
     * @return The RawRepo record.
     *
     * @throws UpdateException In case of an error from RawRepo or an SQL exception.
     */
    public Record fetchRecord( String recId, Integer agencyId ) throws UpdateException {
        logger.entry( recId, agencyId );

        Record result = null;
        try {
            if( recId == null ) {
                throw new IllegalArgumentException( "recId can not be null" );
            }
            if( agencyId == null ) {
                throw new IllegalArgumentException( "agencyId can not be null" );
            }

            try( Connection conn = dataSourceReader.getConnection() ) {
                RawRepoDAO dao = createDAO( conn );

                result = dao.fetchRecord( recId, agencyId );
                return result;
            }
            catch( RawRepoException | SQLException ex ) {
                logger.error( ex.getMessage(), ex );
                throw new UpdateException( ex.getMessage(), ex );
            }
        }
        finally {
            logger.exit( result );
        }
    }

    /**
     * Fetches the common RawRepo record for a MarcRecord.
     * <p>
     * If the record does not exist in the RawRepo then it will be created.
     *
     * @param record The MarcRecord to fetch the common RawRepo record for.
     *
     * @return The RawRepo record.
     *
     * @throws UnsupportedOperationException UnsupportedOperationException
     */
    public Record fetchCommonRecord( MarcRecord record ) {
        throw new UnsupportedOperationException( "Not implementated yet!" );
    }

    /**
     * Returns the record type of a record.
     *
     * @param record The MarcRecord to return the record type for.
     *
     * @return The record type.
     *
     * @throws UpdateException In case of an error from RawRepo or an SQL exception.
     */
    public RecordType recordType( MarcRecord record ) throws UpdateException {
        logger.entry( record );

        RecordType result = RecordType.COMMON_TYPE;
        try {
            Integer agencyId = convertAgencyId( getAgencyId( record ) );
            if( agencyId == RAWREPO_COMMON_LIBRARY ) {
                result = RecordType.COMMON_TYPE;
            }
            else {
                if( recordExists( getRecordId( record ), RAWREPO_COMMON_LIBRARY ) ) {
                    result = RecordType.ENRICHMENT_TYPE;
                }
                else {
                    result = RecordType.LOCAL_TYPE;
                }
            }

            return result;
        }
        finally {
            logger.exit( result );
        }
    }

    /**
     * Checks if a record exists in RawRepo.
     *
     * @param recordId The record id for the record to check for.
     * @param agencyId The agency id for the record to check for.
     *
     * @return <code>true</code> if the record exists, <code>false</code> otherwise.
     *
     * @throws UpdateException In case of an error from RawRepo or an SQL exception.
     */
    public boolean recordExists( String recordId, Integer agencyId ) throws UpdateException {
        logger.entry( recordId, agencyId );

        boolean result = false;
        try( Connection conn = dataSourceReader.getConnection() ) {
            RawRepoDAO dao = createDAO( conn );

            result = dao.recordExists( recordId, agencyId );
            return result;
        }
        catch( RawRepoException | SQLException ex ) {
            logger.error( ex.getMessage(), ex );
            throw new UpdateException( ex.getMessage(), ex );
        }
        finally {
            logger.exit( result );
        }
    }

    /**
     * Checks if a maybe deleted record exists in RawRepo.
     *
     * @param recordId The record id for the record to check for.
     * @param agencyId The agency id for the record to check for.
     *
     * @return <code>true</code> if the record exists, <code>false</code> otherwise.
     *
     * @throws UpdateException In case of an error from RawRepo or an SQL exception.
     */
    public boolean recordExistsMaybeDeleted( String recordId, Integer agencyId ) throws UpdateException {
        logger.entry( recordId, agencyId );

        boolean result = false;
        try( Connection conn = dataSourceReader.getConnection() ) {
            RawRepoDAO dao = createDAO( conn );

            result = dao.recordExistsMabyDeleted( recordId, agencyId );
            return result;
        }
        catch( RawRepoException | SQLException ex ) {
            logger.error( ex.getMessage(), ex );
            throw new UpdateException( ex.getMessage(), ex );
        }
        finally {
            logger.exit( result );
        }
    }

    @TransactionAttribute( TransactionAttributeType.REQUIRES_NEW )
    public void saveRecord( Record record, String parentId ) throws UpdateException {
        logger.entry( record );

        if( record.getId().getBibliographicRecordId().equals( parentId ) ) {
            int agencyId = record.getId().getAgencyId();
            if( agencyId == RAWREPO_COMMON_LIBRARY ) {
                agencyId = COMMON_LIBRARY;
            }
            throw new UpdateException( String.format( ResourceBundles.getBundle( this, "messages" ).getString( "parent.point.to.itself" ),
                    record.getId().getBibliographicRecordId(), agencyId ) );
        }

        if( record.getId().getAgencyId() != RAWREPO_COMMON_LIBRARY && !parentId.isEmpty() ) {
            if( recordExists( record.getId().getBibliographicRecordId(), RAWREPO_COMMON_LIBRARY ) ) {
                throw new UpdateException( String.format( ResourceBundles.getBundle( this, "messages" ).getString( "enrichment.has.parent" ),
                        record.getId().getBibliographicRecordId(), record.getId().getAgencyId() ) );
            }
        }

        try( Connection conn = dataSourceWriter.getConnection() ) {
            RawRepoDAO dao = createDAO( conn );

            if( record.isDeleted() ) {
                dao.setRelationsFrom( record.getId(), new HashSet<RecordId>() );
            }
            dao.saveRecord( record );

            if( !record.isDeleted() ) {
                if( RAWREPO_COMMON_LIBRARY == record.getId().getAgencyId() ) {
                    if (!parentId.isEmpty()) {
                        linkToRecord( dao, record.getId(), new RecordId( parentId, record.getId().getAgencyId() ) );
                    }
                }
                else {
                    if ( recordExists( record.getId().getBibliographicRecordId(), RAWREPO_COMMON_LIBRARY )) {
                        logger.info("Linker record [{}] -> [{}]", record.getId(), new RecordId( record.getId().getBibliographicRecordId(), RAWREPO_COMMON_LIBRARY ));
                        linkToRecord( dao, record.getId(), new RecordId( record.getId().getBibliographicRecordId(), RAWREPO_COMMON_LIBRARY ));
                    }
                }
            }
        }
        catch( RawRepoException | SQLException ex ) {
            logger.error( ex.getMessage(), ex );
            throw new UpdateException( ex.getMessage(), ex );
        }
        finally {
            logger.exit();
        }
    }

    @TransactionAttribute( TransactionAttributeType.REQUIRES_NEW )
    public void changedRecord( String provider, RecordId recId, String mimetype ) throws UpdateException {
        logger.entry( provider, recId, mimetype );

        try( Connection conn = dataSourceWriter.getConnection() ) {
            RawRepoDAO dao = createDAO( conn );

            dao.changedRecord( provider, recId, mimetype );
        }
        catch( RawRepoException | SQLException ex ) {
            logger.error( ex.getMessage(), ex );
            throw new UpdateException( ex.getMessage(), ex );
        }
        finally {
            logger.exit();
        }
    }

    @TransactionAttribute( TransactionAttributeType.REQUIRED )
    public void purgeRecord( RecordId recordId ) throws UpdateException {
        logger.entry( recordId );

        try( Connection conn = dataSourceWriter.getConnection() ) {
            RawRepoDAO dao = createDAO( conn );

            dao.purgeRecord( recordId );
        }
        catch( RawRepoException | SQLException ex ) {
            logger.error( ex.getMessage(), ex );
            throw new UpdateException( ex.getMessage(), ex );
        }
        finally {
            logger.exit();
        }
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
     * @throws javax.xml.bind.JAXBException if the record can not be encoded in marcxchange.
     * @throws UnsupportedEncodingException if the record can not be encoded in UTF-8
     */
    public byte[] encodeRecord( MarcRecord record ) throws JAXBException, UnsupportedEncodingException {
        logger.entry( record );
        byte[] result = null;

        try {

            if( record.getFields().isEmpty() ) {
                return null;
            }

            dk.dbc.iscrum.records.marcxchange.RecordType marcXchangeType = MarcXchangeFactory.createMarcXchangeFromMarc( record );

            ObjectFactory objectFactory = new ObjectFactory();
            JAXBElement<dk.dbc.iscrum.records.marcxchange.RecordType> jAXBElement = objectFactory.createRecord( marcXchangeType );

            JAXBContext jc = JAXBContext.newInstance( CollectionType.class );
            Marshaller marshaller = jc.createMarshaller();
            marshaller.setProperty( Marshaller.JAXB_SCHEMA_LOCATION, "http://www.loc.gov/standards/iso25577/marcxchange-1-1.xsd" );

            StringWriter recData = new StringWriter();
            marshaller.marshal( jAXBElement, recData );

            logger.info( "Marshelled record: {}", recData.toString() );
            result = recData.toString().getBytes( "UTF-8" );

            return result;
        }
        finally {
            logger.exit( result );
        }
    }

    //-------------------------------------------------------------------------
    //              Helpers
    //-------------------------------------------------------------------------

    protected RawRepoDAO createDAO( Connection conn ) throws RawRepoException {
        return RawRepoDAO.newInstance( conn );
    }

    private String getRecordId( MarcRecord record ) {
        return MarcReader.getRecordValue( record, "001", "a" );
    }

    private String getAgencyId( MarcRecord record ) {
        return MarcReader.getRecordValue( record, "001", "b" );
    }

    private int convertAgencyId( String agencyId ) throws UpdateException {
        try {
            return Integer.parseInt( agencyId, 10 );
        }
        catch( NumberFormatException ex ) {
            throw new UpdateException( String.format( "Biblioteksnummeret '%s' er ikke et tal", agencyId ), ex );
        }
    }

    private void linkToRecord( RawRepoDAO dao, RecordId id, RecordId refer_id ) throws SQLException, RawRepoException {
        logger.entry( dao, id, refer_id );

        try {
            final HashSet<RecordId> references = new HashSet<>();

            references.add( refer_id );
            dao.setRelationsFrom( id, references );

            bizLogger.info( "Set relation from [{}:{}] -> [{}:{}]",
                            id.getBibliographicRecordId(), id.getAgencyId(),
                            refer_id.getBibliographicRecordId(), refer_id.getAgencyId() );
        }
        finally {
            logger.exit();
        }
    }

    //-------------------------------------------------------------------------
    //              Constants
    //-------------------------------------------------------------------------

    public static final Integer RAWREPO_COMMON_LIBRARY = 191919;
    public static final Integer COMMON_LIBRARY = 870970;

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    /**
     * Logger instance to write entries to the log files.
     */
    private static XLogger logger = XLoggerFactory.getXLogger( RawRepo.class );
    private final XLogger bizLogger = XLoggerFactory.getXLogger( BusinessLoggerFilter.LOGGER_NAME );

    /**
     * Injected DataSource to read from the rawrepo database.
     */
    @Resource( lookup = JNDIResources.JDBC_RAW_REPO_READONLY_NAME )
    private DataSource dataSourceReader;

    /**
     * Injected DataSource to write to the rawrepo database.
     */
    @Resource( lookup = JNDIResources.JDBC_RAW_REPO_WRITABLE_NAME )
    private DataSource dataSourceWriter;
}
