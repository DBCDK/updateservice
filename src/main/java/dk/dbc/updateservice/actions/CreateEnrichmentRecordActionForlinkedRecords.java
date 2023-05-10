package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.marc.binding.DataField;
import dk.dbc.marc.binding.Leader;
import dk.dbc.marc.binding.MarcRecord;
import dk.dbc.marc.binding.SubField;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.update.UpdateRecordContentTransformer;
import dk.dbc.updateservice.utils.DeferredLogger;
import dk.dbc.updateservice.utils.MDCUtil;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import static dk.dbc.marc.reader.DanMarc2LineFormatReader.DEFAULT_LEADER;

/**
 * Action to create or update an enrichment record from a common record.
 * <p>
 * This action handles to case where we need to create or update an enrichment
 * triggered when the classification data is updated in a common record and
 * there is an agency that has holdings for the common record.
 * </p>
 * <p>
 * The creation of the enrichment record is done by calling the JavaScript
 * engine (thought LibraryRecordsHandler) to produce the actual enrichment
 * record.
 * </p>
 */
public class CreateEnrichmentRecordActionForlinkedRecords extends AbstractRawRepoAction {
    private static final DeferredLogger LOGGER = new DeferredLogger(CreateEnrichmentRecordActionForlinkedRecords.class);
    private static final String RECATEGORIZATION_STRING = "Sammenlagt med post med faustnummer %s";
    private static final String ERRONEOUS_RECATEGORIZATION_STRING = "Manglende data i posten til at skabe korrekt y08 for faustnummer %s";
    private static final String RECATEGORIZATION_STRING_OBSOLETE = " Postens opstilling ændret på grund af omkatalogisering";
    private static final String MIMETYPE = MarcXChangeMimeType.ENRICHMENT;

    private MarcRecord recordWithHoldings;
    private int agencyId;
    private MarcRecord record;
    private final Properties settings;

    public CreateEnrichmentRecordActionForlinkedRecords(GlobalActionState globalActionState, Properties properties) {
        super(CreateEnrichmentRecordWithClassificationsAction.class.getSimpleName(), globalActionState);
        settings = properties;
    }

    void setRecordWithHoldings(MarcRecord recordWithHoldings) {
        this.recordWithHoldings = recordWithHoldings;
    }

    public int getAgencyId() {
        return agencyId;
    }

    public void setAgencyId(int agencyId) {
        this.agencyId = agencyId;
    }

    public void setMarcRecord(MarcRecord marcRecord) {
        this.record = marcRecord;
    }

    /**
     * Performs this actions and may create any child actions.
     *
     * @return A list of ValidationError to be reported in the web service response.
     * @throws UpdateException In case of an error.
     */
    @Override
    public ServiceResult performAction() throws UpdateException {
        return LOGGER.callChecked(log -> {
            final MarcRecord enrichmentRecord = createEnrichmentRecord();
            if (enrichmentRecord.getFields().isEmpty()) {
                log.info("No sub actions to create for an empty enrichment record.");
                return ServiceResult.newOkResult();
            }
            log.info("Creating sub actions to store new enrichment record.");
            log.debug("Enrichment record:\n{}", enrichmentRecord);

            final String recordId = new MarcRecordReader(enrichmentRecord).getRecordId();
            final StoreRecordAction storeRecordAction = new StoreRecordAction(state, settings, enrichmentRecord);
            storeRecordAction.setMimetype(MIMETYPE);
            children.add(storeRecordAction);

            final LinkRecordAction linkRecordAction = new LinkRecordAction(state, enrichmentRecord);
            linkRecordAction.setLinkToRecordId(new RecordId(recordId, RawRepo.COMMON_AGENCY));
            children.add(linkRecordAction);

            final EnqueueRecordAction enqueueRecordAction = new EnqueueRecordAction(state, settings, enrichmentRecord);
            children.add(enqueueRecordAction);

            return ServiceResult.newOkResult();
        });
    }

    @Override
    public void setupMDCContext() {
        MDCUtil.setupContextForEnrichmentRecord(record, Integer.toString(agencyId));
    }

    protected MarcRecord loadRecord(String recordId, Integer agencyId) throws UpdateException {
        final Record record = rawRepo.fetchRecord(recordId, agencyId);
        return UpdateRecordContentTransformer.decodeRecord(record.getContent());
    }

    private MarcRecord createEnrichmentRecord() throws UpdateException {
        MarcRecord enrichmentRecord = new MarcRecord().setLeader(new Leader().setData(DEFAULT_LEADER));
        final MarcRecordReader recordReader = new MarcRecordReader(record);
        final String recordId = recordReader.getRecordId();
        if (rawRepo.recordExists(recordId, agencyId)) {
            enrichmentRecord = loadRecord(recordId, agencyId);
        } else {
            final MarcRecordWriter enrichmentRecordWriter = new MarcRecordWriter(enrichmentRecord);
            enrichmentRecordWriter.copyFieldsFromRecord(Arrays.asList("001", "004"), record);
            enrichmentRecordWriter.addOrReplaceSubField("001", 'b', Integer.toString(agencyId));
        }
        enrichmentRecord.getFields().add(getFormattedY08Field(recordWithHoldings));

        return enrichmentRecord;
    }

    private DataField getFormattedY08Field(MarcRecord rec) {
        final DataField yNoteField = new DataField("y08", "00");
        final MarcRecordReader reader = new MarcRecordReader(rec);
        final String faust = reader.getValue("001", 'a');
        String faustWithIntro;
        try {
            final DataField noteField = state.getLibraryRecordsHandler().fetchNoteField(rec);
            final String yNoteFieldString = getNoteFieldString(noteField);
            if (yNoteFieldString == null) {
                faustWithIntro = String.format(ERRONEOUS_RECATEGORIZATION_STRING, faust);
            } else {
                faustWithIntro = String.format(RECATEGORIZATION_STRING, faust).concat(" " + yNoteFieldString);
                faustWithIntro = faustWithIntro.replace(RECATEGORIZATION_STRING_OBSOLETE, "");
            }
            yNoteField.getSubFields().add(new SubField('a', faustWithIntro));
            return yNoteField;
        } catch (UpdateException e) {
            LOGGER.use(log -> log.error("Error : UpdateException, probably due to malformed record \n", e));
            yNoteField.getSubFields().add(new SubField('a', String.format(ERRONEOUS_RECATEGORIZATION_STRING, faust)));
            return yNoteField;
        }
    }

    private String getNoteFieldString(DataField noteField) {
        if (noteField.getSubFields() == null || noteField.getSubFields().isEmpty()) {
            return null;
        }

        final List<SubField> subFields = noteField.getSubFields();
        return LOGGER.call(log -> {
            String res = "";
            for (Iterator<SubField> mfIter = subFields.listIterator(); mfIter.hasNext(); ) {
                SubField sf = mfIter.next();
                log.trace("working on subfield: {}", sf);
                if (mfIter.hasNext()) {
                    if ('d' == sf.getCode()) {
                        res = res.concat(sf.getData() + ": ");
                    } else {
                        res = res.concat(sf.getData() + " ");
                    }
                } else {
                    res = res.concat(sf.getData());
                }
            }
            return res;
        });
    }
}
