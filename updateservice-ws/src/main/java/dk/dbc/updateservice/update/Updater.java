//-----------------------------------------------------------------------------
package dk.dbc.updateservice.update;

//-----------------------------------------------------------------------------

import dk.dbc.iscrum.records.*;
import dk.dbc.iscrum.records.marcxchange.CollectionType;
import dk.dbc.iscrum.records.marcxchange.ObjectFactory;
import dk.dbc.iscrum.records.marcxchange.RecordType;
import dk.dbc.iscrum.utils.ResourceBundles;
import dk.dbc.iscrum.utils.logback.filters.BusinessLoggerFilter;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.rawrepo.RawRepoException;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.javascript.Scripter;
import dk.dbc.updateservice.javascript.ScripterException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.util.*;

//-----------------------------------------------------------------------------
/**
 * EJB to update a record in the rawrepo database.
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
     * Constructs a new instance with a null rawRepo.
     */
    public Updater() {
        this( null, null, null );
    }

    /**
     * Constructs with a rawRepo.
     *
     * <dl>
     *      <dt>Note</dt>
     *      <dd>
     *          This constructor is added to "inject" a rawRepo from
     *          unit tests.
     *      </dd>
     * </dl>
     *
     * @param rawRepo      The rawRepo to "inject".
     * @param holdingsItems The holdingsItems to "inject".
     * @param recordsHandler  The records handler to use.
     *
     */
    public Updater( RawRepo rawRepo, HoldingsItems holdingsItems, LibraryRecordsHandler recordsHandler ) {
        this.rawRepo = rawRepo;
        this.holdingsItems = holdingsItems;
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

        logger.entry();
        try {
            if( recordsHandler == null ) {
                this.recordsHandler = new LibraryRecordsHandler( scripter, "updater.js" );
            }

            this.messages = ResourceBundles.getBundle( this, "messages" );
        }
        catch( MissingResourceException ex ) {
            logger.error( "Unable to load resource", ex );
        }
        finally {
            logger.exit();
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
    public void updateRecord( MarcRecord record, String userId, String groupId ) throws UpdateException {
        logger.entry( record );

        try {
            if( record == null ) {
                throw new NullPointerException( messages.getString( "record.is.null" ) );
            }

            bizLogger.info( "Split record into records to be created or updated" );

            for( MarcRecord rec : recordsHandler.recordDataForRawRepo( record, userId, groupId ) ) {
                String recId = MarcReader.getRecordValue( rec, "001", "a" );
                int libraryId = Integer.parseInt( MarcReader.getRecordValue( rec, "001", "b" ), 10 );

                bizLogger.info( "Begin to handle record [{}:{}]\n{}", recId, libraryId, rec );

                if( libraryId == rawRepo.RAWREPO_COMMON_LIBRARY ) {
                    bizLogger.info( "Creates/updates common record: {}", MarcXChangeMimeType.MARCXCHANGE );
                    updateCommonRecord( rec );
                }
                else {
                    if( rawRepo.recordExists( recId, rawRepo.RAWREPO_COMMON_LIBRARY ) ) {
                        bizLogger.info( "Common record exist [{}:{}]", recId, rawRepo.RAWREPO_COMMON_LIBRARY );
                        bizLogger.info( "Creates/updates enrichment record: {}", MarcXChangeMimeType.ENRICHMENT );
                        Record commonRecord = rawRepo.fetchRecord( recId, rawRepo.RAWREPO_COMMON_LIBRARY );
                        saveLibraryExtendedRecord( commonRecord, rec );
                    }
                    else {
                        bizLogger.info( "Common record does not exist [{}:{}]", recId, rawRepo.RAWREPO_COMMON_LIBRARY );
                        bizLogger.info( "Creates/updates local record: {}", MarcXChangeMimeType.DECENTRAL );
                        updateLibraryLocalRecord( rec );
                    }
                }
            }
        }
        catch( RawRepoException | SQLException | JAXBException | UnsupportedEncodingException | ScripterException ex ) {
            logger.error( "Update error: " + ex.getMessage(), ex );
            throw new UpdateException( ex.getMessage(), ex );
        }
        catch( NumberFormatException ex ) {
            throw new UpdateException( messages.getString( "invalid.agencyid" ), ex );
        }
        finally {
            logger.exit();
        }
    }
    
    void updateCommonRecord( MarcRecord record ) throws SQLException, UpdateException, JAXBException, UnsupportedEncodingException, ScripterException, RawRepoException {
        logger.entry( record );

        try {
            String recId = MarcReader.getRecordValue(record, "001", "a");
            int libraryId = Integer.parseInt( MarcReader.getRecordValue(record, "001", "b"), 10 );
            String parentId = MarcReader.getRecordValue(record, "014", "a");

            logger.info("Record id: [{}:{}]", recId, libraryId);
            logger.info("Parent Record id: {}", parentId);

            // Fetch ids of local libraries for recId.
            // Note: We remove rawRepo.RAWREPO_COMMON_LIBRARY from the set because the interface
            // 		 returns *all* libraries and not only local libraries.
            Set<Integer> localLibraries = rawRepo.agenciesForRecord( recId );
            localLibraries.remove(rawRepo.RAWREPO_COMMON_LIBRARY );
            logger.info("Local libraries: {}", localLibraries);

            if (!rawRepo.recordExists(recId, libraryId) && !localLibraries.isEmpty()) {
                logger.error("Try to update common record [{}:{}], but local records exists: {}",
                        recId, libraryId, recId, localLibraries);
                throw new UpdateException(String.format( messages.getString( "common.record.with.locals" ), localLibraries ) );
            }

            MarcRecord oldRecord = null;
            if (rawRepo.recordExists(recId, libraryId)) {
                Record rawRecord = rawRepo.fetchRecord(recId, libraryId);
                if (rawRecord.getContent() != null) {
                    oldRecord = decodeRecord(rawRecord.getContent());
                }
            }

            if ( oldRecord == null ) {
                oldRecord = new MarcRecord();
                oldRecord.setFields(new ArrayList<MarcField>());
            }

            if( oldRecord.getFields().isEmpty() ) {
                bizLogger.info( "Save new common record:\n{}", record.toString() );
            }
            else {
                bizLogger.info( "Overwriting common existing record. Old record:\n{}\nNew record:\n{}",
                                oldRecord.toString(), record.toString() );
            }
            saveRecord(encodeRecord(record), recId, libraryId, parentId);

            if (recordsHandler.hasClassificationData(oldRecord) && recordsHandler.hasClassificationData(record)) {
                if (recordsHandler.hasClassificationsChanged(oldRecord, record)) {
                    logger.info("Classifications was changed for common record [{}:{}]", recId, libraryId);
                    Set<Integer> holdingsLibraries = holdingsItems.getAgenciesThatHasHoldingsFor( recId );
                    for (Integer id : holdingsLibraries) {
                        logger.info("Local library for record: {}", id);
                        if (rawRepo.recordExists(recId, id)) {
                            Record extRecord = rawRepo.fetchRecord(recId, id);
                            MarcRecord extRecordData = decodeRecord(extRecord.getContent());
                            if (!recordsHandler.hasClassificationData(extRecordData)) {
                                logger.info("Update classifications for extended library record: [{}:{}]", recId, id);
                                updateLibraryExtendedRecord(rawRepo.fetchRecord(recId, libraryId), oldRecord, decodeRecord(extRecord.getContent()));
                            } else {
                                logger.info("Extended library record, [{}:{}], has its own classifications. Record not updated.", recId, id);
                                enqueueRecord(extRecord.getId());
                            }
                        } else {
                            logger.info("Create new extended library record: [{}:{}].", recId, id);
                            createLibraryExtendedRecord(rawRepo.fetchRecord(recId, libraryId), oldRecord, id);
                        }
                    }
                }
            }
            enqueueExtendedRecords(new RecordId(recId, libraryId));
        }
        catch( NumberFormatException ex ) {
            throw new UpdateException( messages.getString( "invalid.agencyid" ), ex );
        }
        finally {
            logger.exit();
        }
    }

    void updateLibraryLocalRecord( MarcRecord record ) throws SQLException, UpdateException, JAXBException, UnsupportedEncodingException, RawRepoException {
        try {
            logger.entry( record );

            String recId = MarcReader.getRecordValue(record, "001", "a");
            int libraryId = Integer.parseInt( MarcReader.getRecordValue(record, "001", "b"), 10 );
            String parentId = MarcReader.getRecordValue(record, "014", "a");

            if( !rawRepo.recordExists( recId, libraryId ) ) {
                bizLogger.info( "Creating new local record:\n{}", record.toString() );
            }
            else {
                Record rawRecord = rawRepo.fetchRecord( recId, libraryId );
                MarcRecord oldRecord = decodeRecord( rawRecord.getContent() );
                bizLogger.info( "Overwriting existing local record. Old record:\n{}\nNew record:\n{}",
                                oldRecord.toString(), record.toString() );
            }

            saveRecord(encodeRecord(record), recId, libraryId, parentId);
        }
        finally {
            logger.exit();
        }
    }
    
    void createLibraryExtendedRecord( Record commonRecord, MarcRecord oldCommonRecordData, int libraryId ) throws SQLException, UpdateException, JAXBException, UnsupportedEncodingException, ScripterException, RawRepoException {
        try {
            logger.entry(commonRecord, oldCommonRecordData, libraryId);
            MarcRecord extRecord = recordsHandler.createLibraryExtendedRecord(oldCommonRecordData, libraryId);

            if( !extRecord.getFields().isEmpty() ) {
                String recId = MarcReader.getRecordValue(extRecord, "001", "a");

                bizLogger.info( "Creating new enrichment record for library {}:\n{}", libraryId, extRecord.toString() );
                saveRecord( encodeRecord( extRecord ), recId, libraryId, "" );
                enqueueRecord( new RecordId( recId, libraryId ) );
            }
        }
        finally {
            logger.exit();
        }
    }

    void updateLibraryExtendedRecord( Record commonRecord, MarcRecord oldCommonRecordData, MarcRecord record ) throws SQLException, UpdateException, JAXBException, UnsupportedEncodingException, ScripterException, RawRepoException {
        try {
            logger.entry(commonRecord, oldCommonRecordData, record);
            MarcRecord extRecord = recordsHandler.updateLibraryExtendedRecord(oldCommonRecordData, record);

            String recId = MarcReader.getRecordValue(record, "001", "a");
            int libraryId = Integer.parseInt( MarcReader.getRecordValue(record, "001", "b"), 10 );
            String parentId = MarcReader.getRecordValue(record, "014", "a");

            Record rawRecord = rawRepo.fetchRecord( recId, libraryId );
            MarcRecord oldRecord = decodeRecord( rawRecord.getContent() );
            bizLogger.info( "Overwriting existing enrichment record. Old record:\n{}\nNew record:\n{}",
                            oldRecord.toString(), extRecord.toString() );

            if (extRecord.getFields().isEmpty()) {
                if (rawRepo.recordExists(recId, libraryId)) {
                    bizLogger.info( "Deleting existing library extended record because its empty");
                    deleteRecord( record );
                }
                else {
                    bizLogger.info( "Does not update enrichment record because it is empty: [{}:{}]", recId, libraryId );
                }
            }
            else {
                saveRecord( encodeRecord( extRecord ), recId, libraryId, parentId );
            }
            enqueueRecord(new RecordId(recId, libraryId));
        }
        finally {
            logger.exit();
        }
    }

    void saveLibraryExtendedRecord( Record commonRecord, MarcRecord record ) throws UnsupportedEncodingException, ScripterException, SQLException, UpdateException, JAXBException, RawRepoException {
        try {
            logger.entry(commonRecord, record);
            MarcRecord extRecord = record;

            if (commonRecord.getContent() != null) {
                MarcRecord commonRecData = decodeRecord(commonRecord.getContent());
                extRecord = recordsHandler.correctLibraryExtendedRecord(commonRecData, record);
            }

            String recId = MarcReader.getRecordValue(record, "001", "a");
            int libraryId = Integer.parseInt( MarcReader.getRecordValue(record, "001", "b"), 10 );
            String parentId = MarcReader.getRecordValue(record, "014", "a");

            logger.info("Record id: [{}:{}]", recId, libraryId);
            logger.info("Parent Record id: {}", parentId);
            logger.info("Original library extended record:\n{}", record);

            if (extRecord.getFields().isEmpty()) {
                if (rawRepo.recordExists(recId, libraryId)) {
                    bizLogger.info( "Deleting existing library extended record because its empty");
                    deleteRecord( record );
                }
                else {
                    bizLogger.info( "Does not create enrichment record bacause it is empty: [{}:{}]", recId, libraryId );
                }
            }
            else {
                if( rawRepo.recordExists( recId, libraryId ) ) {
                    Record rawRecord = rawRepo.fetchRecord( recId, libraryId );
                    MarcRecord oldRecord = decodeRecord( rawRecord.getContent() );
                    bizLogger.info( "Overwriting existing enrichment record. Old record:\n{}\nNew record:\n{}",
                            oldRecord.toString(), extRecord.toString() );
                }
                else {
                    bizLogger.info( "Creating new enrichment record:\n{}", extRecord.toString() );
                }
                saveRecord( encodeRecord( extRecord ), recId, libraryId, parentId );
            }
            enqueueRecord(new RecordId(recId, libraryId));
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
     * @throws JAXBException if the record can not be encoded in marcxchange.
     * @throws UnsupportedEncodingException if the record can not be encoded in UTF-8
     */
    byte[] encodeRecord( MarcRecord record ) throws JAXBException, UnsupportedEncodingException {
        byte[] result = null;

        try {
            logger.entry(record);

            if (record.getFields().isEmpty()) {
                return null;
            }

            RecordType marcXchangeType = MarcXchangeFactory.createMarcXchangeFromMarc(record);

            ObjectFactory objectFactory = new ObjectFactory();
            JAXBElement<RecordType> jAXBElement = objectFactory.createRecord(marcXchangeType);

            JAXBContext jc = JAXBContext.newInstance(CollectionType.class);
            Marshaller marshaller = jc.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_SCHEMA_LOCATION, "http://www.loc.gov/standards/iso25577/marcxchange-1-1.xsd");

            StringWriter recData = new StringWriter();
            marshaller.marshal(jAXBElement, recData);

            logger.info("Marshelled record: {}", recData.toString());
            result = recData.toString().getBytes("UTF-8");

            return result;
        }
        finally {
            logger.exit( result );
        }
    }

    /**
     * Deletes a record.
     *
     * @param record
     *
     * @throws UpdateException
     * @throws JAXBException
     * @throws UnsupportedEncodingException
     */
    private void deleteRecord( MarcRecord record ) throws UpdateException, JAXBException, UnsupportedEncodingException {
        logger.entry( record );

        try {
            String recordId = MarcReader.getRecordValue( record, "001", "a");
            int agencyId = Integer.parseInt( MarcReader.getRecordValue(record, "001", "b"), 10 );

            if( !rawRepo.recordExistsMaybeDeleted( recordId, agencyId ) ) {
                throw new UpdateException( String.format( messages.getString( "delete.record.not.exist" ), recordId, agencyId) );
            }

            bizLogger.info( "Deleting record [{}:{}]", recordId, agencyId );

            MarcRecord deletedRecordData = new MarcRecord();
            for( MarcField field : record.getFields() ) {
                if( field.getName().equals( "001" ) ) {
                    deletedRecordData.getFields().add( field );
                }
                if( field.getName().equals( "004" ) ) {
                    deletedRecordData.getFields().add( field );
                }
            }
            MarcWriter.addOrReplaceSubfield( deletedRecordData, "004", "r", "d" );

            logger.debug( "Current record data:\n{}", record.toString() );
            logger.debug( "Current record deleted data:\n{}", deletedRecordData.toString() );

            final Record rawRepoRecord = rawRepo.fetchRecord( recordId, agencyId );
            rawRepoRecord.setContent( encodeRecord( deletedRecordData ) );
            rawRepoRecord.setDeleted( true );
            rawRepo.saveRecord( rawRepoRecord, "" );
        }
        finally {
            logger.exit();
        }
    }

    /**
     * Saves the record in a rawrepo.
     *
     * @param content   The encoded content of the record.
     * @param recId     The id from the record.
     * @param libraryId The library number from the record.
     * @param parentId  The parent id to any parent record, from the record.
     *
     * @throws SQLException JDBC errors.
     * @throws UpdateException if a parent record does not exist.
     * @throws RawRepoException RawRepoException
     */
    private void saveRecord( byte[] content, String recId, int libraryId, String parentId ) throws SQLException, UpdateException, RawRepoException {
        try {
            logger.entry(content, recId, libraryId, parentId);

            if (content == null) {
                String err = String.format( messages.getString( "save.empty.record" ), recId, libraryId);
                logger.warn( err );
                throw new UpdateException(err);
            }

            if (!parentId.isEmpty() && !rawRepo.recordExists(parentId, libraryId)) {
                String err = String.format( messages.getString( "reference.record.not.exist" ), recId, libraryId, parentId, libraryId);
                bizLogger.warn( err );
                throw new UpdateException(err);
            }

            final Record rawRepoRecord = rawRepo.fetchRecord(recId, libraryId);
            rawRepoRecord.setContent( content );
            rawRepoRecord.setMimeType( mimeTypeForRecord( rawRepoRecord ) );
            rawRepoRecord.setDeleted( false );
            rawRepo.saveRecord( rawRepoRecord, parentId );
            bizLogger.info( "Save record [{}:{}]", rawRepoRecord.getId().getBibliographicRecordId(), rawRepoRecord.getId().getAgencyId() );

            rawRepo.changedRecord( PROVIDER, rawRepoRecord.getId(), rawRepoRecord.getMimeType() );
        }
        finally {
            logger.exit();
        }
    }

    private String mimeTypeForRecord( final Record rawRepoRecord ) throws UpdateException {
        logger.entry( rawRepoRecord );

        String result = "";
        try {
            RecordId recId = rawRepoRecord.getId();
            if( recId.getAgencyId() == RawRepo.RAWREPO_COMMON_LIBRARY ) {
                result = MarcXChangeMimeType.MARCXCHANGE;
            }
            else {
                if( rawRepo.recordExists( recId.getBibliographicRecordId(), rawRepo.RAWREPO_COMMON_LIBRARY ) ) {
                    logger.debug( "Common record exist [{}:{}]", recId.getBibliographicRecordId(), rawRepo.RAWREPO_COMMON_LIBRARY );
                    result = MarcXChangeMimeType.ENRICHMENT;
                }
                else {
                    result = MarcXChangeMimeType.DECENTRAL;
                }
            }

            return result;
        }
        finally {
            logger.exit( result );
        }
    }

    /**
     * Links a record to another record in the rawrepo.
     *
     * @param id       The id of the record to link from.
     * @param refer_id The id of the record to link to.
     *
     * @throws SQLException JDBC errors.
     * @throws RawRepoException RawRepoException
     */
    private void linkToRecord( RecordId id, RecordId refer_id ) throws SQLException, RawRepoException {
        try {
            logger.entry(id, refer_id);

            final HashSet<RecordId> references = new HashSet<>();
            references.add(refer_id);
        }
        finally {
            logger.exit();
        }
    }

    private void enqueueRecord( RecordId id ) throws SQLException {
        try {
            logger.entry(id);
            logger.info("Enqueue record: [{}:{}]", id.getBibliographicRecordId(), id.getAgencyId());
        }
        finally {
            logger.exit();
        }
    }

    private void enqueueExtendedRecords( RecordId commonRecId ) throws UpdateException, SQLException {
        try {
            logger.entry(commonRecId);

            Set<Integer> extLibraries = rawRepo.agenciesForRecord( commonRecId.getBibliographicRecordId() );
            for (Integer libId : extLibraries) {
                enqueueRecord(new RecordId(commonRecId.getBibliographicRecordId(), libId));
            }
        }
        finally {
            logger.exit();
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
    private final XLogger bizLogger = XLoggerFactory.getXLogger( BusinessLoggerFilter.LOGGER_NAME );

    /**
     * Rawrepo requires a provider name for the service that changes its
     * content. This constant defines the provider name for the update web
     * service.
     */
    static final String PROVIDER = "opencataloging-update";

    @EJB
    private Scripter scripter;
    
    @EJB
    private RawRepo rawRepo;

    @EJB
    private HoldingsItems holdingsItems;

    private LibraryRecordsHandler recordsHandler;
    private ResourceBundle messages;
}
