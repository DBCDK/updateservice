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

            checkRecordForUpdatability( record );

            bizLogger.info( "Split record into records to be created or updated" );
            List<MarcRecord> records = recordsHandler.recordDataForRawRepo( record, userId, groupId );
            Collections.sort( records, new ProcessOrder() );

            for( MarcRecord rec : records ) {
                String recId = MarcReader.getRecordValue( rec, "001", "a" );
                int libraryId = Integer.parseInt( MarcReader.getRecordValue( rec, "001", "b" ), 10 );

                bizLogger.info( "Begin to handle record [{}:{}]\n{}", recId, libraryId, rec );

                if( libraryId == rawRepo.RAWREPO_COMMON_LIBRARY ) {
                    if( hasRecordDeletionMark( rec ) ) {
                        deleteCommonRecord( rec );
                    }
                    else {
                        bizLogger.info( "Creates/updates common record: {}", MarcXChangeMimeType.MARCXCHANGE );
                        updateCommonRecord( rec );
                    }
                }
                else {
                    if( rawRepo.recordExists( recId, rawRepo.RAWREPO_COMMON_LIBRARY ) ) {
                        if( hasRecordDeletionMark( rec ) ) {
                            deleteEnrichmentRecord( rec );
                        }
                        else {
                            bizLogger.info( "Common record exist [{}:{}]", recId, rawRepo.RAWREPO_COMMON_LIBRARY );
                            bizLogger.info( "Creates/updates enrichment record: {}", MarcXChangeMimeType.ENRICHMENT );
                            Record commonRecord = rawRepo.fetchRecord( recId, rawRepo.RAWREPO_COMMON_LIBRARY );
                            saveLibraryExtendedRecord( commonRecord, rec );
                        }
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

    void checkRecordForUpdatability( MarcRecord record ) throws UpdateException {
        logger.entry();

        try {
            if( !hasRecordDeletionMark( record ) ) {
                return;
            }

            String recId = MarcReader.getRecordValue(record, "001", "a");
            int libraryId = Integer.parseInt( MarcReader.getRecordValue(record, "001", "b"), 10 );
            int rawRepoAgencyId = libraryId;

            if( libraryId == RawRepo.COMMON_LIBRARY ) {
                rawRepoAgencyId = RawRepo.RAWREPO_COMMON_LIBRARY;
            }

            if( !rawRepo.children( new RecordId( recId, rawRepoAgencyId ) ).isEmpty() ) {
                throw new UpdateException( String.format( messages.getString( "delete.record.children.error" ), recId ) );
            }
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
            localLibraries.remove(rawRepo.COMMON_LIBRARY );
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
            saveRecord( record );

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

    public void deleteCommonRecord( MarcRecord record ) throws UnsupportedEncodingException, JAXBException, UpdateException {
        logger.entry();

        try {
            String recId = MarcReader.getRecordValue( record, "001", "a" );
            String agencyId = MarcReader.getRecordValue( record, "001", "b" );

            bizLogger.info( "Deleting common record [{}:{}]", recId, agencyId );
            deleteRecord( record );
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

            if( hasRecordDeletionMark( record ) ) {
                if( !holdingsItems.getAgenciesThatHasHoldingsFor( record ).isEmpty() ) {
                    throw new UpdateException( messages.getString( "delete.local.with.holdings.error" ) );
                }

                deleteRecord( record );
            }
            else {
                saveRecord( record );
            }
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
                saveRecord( extRecord );
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
                saveRecord( extRecord );
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
                saveRecord( extRecord );
            }
            enqueueRecord(new RecordId(recId, libraryId));
        }
        finally {
            logger.exit();
        }
    }

    public void deleteEnrichmentRecord( MarcRecord record ) throws UnsupportedEncodingException, JAXBException, UpdateException {
        logger.entry( record );

        try {
            if( !holdingsItems.getAgenciesThatHasHoldingsFor( record ).isEmpty() ) {
                throw new UpdateException( messages.getString( "delete.enrichment.with.holdings.error" ) );
            }

            String recId = MarcReader.getRecordValue( record, "001", "a" );
            String agencyId = MarcReader.getRecordValue( record, "001", "b" );

            bizLogger.info( "Deleting enrichment record [{}:{}]", recId, agencyId );

            deleteRecord( record );
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

            Set<RecordId> relations = rawRepo.relationsToRecord( record );
            if( !relations.isEmpty() ) {
                bizLogger.info( "Records that relates to [{}:{}]", recordId, agencyId );
                for( RecordId recId : relations ) {
                    bizLogger.info( "  --> Record [{}:{}]", recId.getBibliographicRecordId(), recId.getAgencyId() );
                }
                throw new UpdateException( String.format( messages.getString( "delete.record.reference.error" ), recordId ) );
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

            rawRepo.changedRecord( PROVIDER, rawRepoRecord.getId(), rawRepoRecord.getMimeType() );
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
    private void saveRecord( MarcRecord record ) throws SQLException, UpdateException, RawRepoException, JAXBException, UnsupportedEncodingException {
        try {
            logger.entry( record );

            if( record == null ) {
                String err = messages.getString( "save.empty.record" );
                logger.warn( err );
                throw new UpdateException(err);
            }

            String recId = MarcReader.getRecordValue( record, "001", "a" );
            Integer agencyId = Integer.valueOf( MarcReader.getRecordValue( record, "001", "b" ), 10 );
            String parentId = MarcReader.getRecordValue( record, "014", "a" );

            if (!parentId.isEmpty() && !rawRepo.recordExists(parentId, agencyId)) {
                String err = String.format( messages.getString( "reference.record.not.exist" ), recId, agencyId, parentId, agencyId);
                bizLogger.warn( err );
                throw new UpdateException(err);
            }

            final Record rawRepoRecord = rawRepo.fetchRecord(recId, agencyId);
            rawRepoRecord.setContent( encodeRecord( record ) );
            rawRepoRecord.setMimeType( mimeTypeForRecord( record ) );
            rawRepoRecord.setDeleted( false );
            rawRepo.saveRecord( rawRepoRecord, parentId );
            bizLogger.info( "Save record [{}:{}]", rawRepoRecord.getId().getBibliographicRecordId(), rawRepoRecord.getId().getAgencyId() );

            rawRepo.changedRecord( PROVIDER, rawRepoRecord.getId(), rawRepoRecord.getMimeType() );
        }
        finally {
            logger.exit();
        }
    }

    private boolean hasRecordDeletionMark( MarcRecord record ) {
        return MarcReader.getRecordValue( record, "004", "r" ).equals( "d" );
    }

    private String mimeTypeForRecord( final MarcRecord record ) throws UpdateException {
        logger.entry( record );

        String result = "";
        try {
            String recId = MarcReader.getRecordValue( record, "001", "a" );
            Integer agencyId = Integer.valueOf( MarcReader.getRecordValue( record, "001", "b" ), 10 );

            if( agencyId.equals( RawRepo.RAWREPO_COMMON_LIBRARY ) ) {
                String owner = MarcReader.getRecordValue( record, "996", "a" );

                if( owner.equals( "DBC" ) ) {
                    result = MarcXChangeMimeType.MARCXCHANGE;
                }
                else {
                    result = MarcXChangeMimeType.DECENTRAL;
                }
            }
            else {
                if( rawRepo.recordExists( recId, rawRepo.RAWREPO_COMMON_LIBRARY ) ) {
                    logger.debug( "Common record exist [{}:{}]", recId, rawRepo.RAWREPO_COMMON_LIBRARY );
                    result = MarcXChangeMimeType.ENRICHMENT;
                }
                else {
                    result = MarcXChangeMimeType.MARCXCHANGE;
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

    /**
     * Class to sort the records returned from JavaScript in the order they should be
     * processed.
     * <p/>
     * The records are sorted in this order:
     * <ol>
     *     <li>Common records are processed before local and enrichment records.</li>
     *     <li>
     *         If one of the records has the deletion mark in 004r then the process order
     *         is reversed.
     *     </li>
     * </ol>
     */
    private class ProcessOrder implements Comparator<MarcRecord> {

        /**
         * Compares its two arguments for order.  Returns a negative integer, zero, or a positive integer as the first
         * argument is less than, equal to, or greater than the second.<p>
         * <p>
         * In the foregoing description, the notation <tt>sgn(</tt><i>expression</i><tt>)</tt> designates the mathematical
         * <i>signum</i> function, which is defined to return one of <tt>-1</tt>, <tt>0</tt>, or <tt>1</tt> according to
         * whether the value of <i>expression</i> is negative, zero or positive.<p>
         * <p>
         * The implementor must ensure that <tt>sgn(compare(x, y)) == -sgn(compare(y, x))</tt> for all <tt>x</tt> and
         * <tt>y</tt>.  (This implies that <tt>compare(x, y)</tt> must throw an exception if and only if <tt>compare(y,
         * x)</tt> throws an exception.)<p>
         * <p>
         * The implementor must also ensure that the relation is transitive: <tt>((compare(x, y)&gt;0) &amp;&amp;
         * (compare(y, z)&gt;0))</tt> implies <tt>compare(x, z)&gt;0</tt>.<p>
         * <p>
         * Finally, the implementor must ensure that <tt>compare(x, y)==0</tt> implies that <tt>sgn(compare(x,
         * z))==sgn(compare(y, z))</tt> for all <tt>z</tt>.<p>
         * <p>
         * It is generally the case, but <i>not</i> strictly required that <tt>(compare(x, y)==0) == (x.equals(y))</tt>.
         * Generally speaking, any comparator that violates this condition should clearly indicate this fact.  The
         * recommended language is "Note: this comparator imposes orderings that are inconsistent with equals."
         *
         * @param o1 the first object to be compared.
         * @param o2 the second object to be compared.
         *
         * @return a negative integer, zero, or a positive integer as the first argument is less than, equal to, or greater
         * than the second.
         *
         * @throws NullPointerException if an argument is null and this comparator does not permit null arguments
         * @throws ClassCastException   if the arguments' types prevent them from being compared by this comparator.
         */
        @Override
        public int compare( MarcRecord o1, MarcRecord o2 ) {
            Integer agency1 = Integer.valueOf( MarcReader.getRecordValue( o1, "001", "b" ), 10 );
            Integer agency2 = Integer.valueOf( MarcReader.getRecordValue( o2, "001", "b" ), 10 );

            int result;
            if( agency1.equals( agency2 ) ) {
                result = 0;
            }
            else if( agency1.equals( RawRepo.RAWREPO_COMMON_LIBRARY ) ) {
                result = -1;
            }
            else {
                result = 1;
            }

            if( hasRecordDeletionMark( o1 ) || hasRecordDeletionMark( o2 ) ) {
                return result * -1;
            }

            return result;
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
