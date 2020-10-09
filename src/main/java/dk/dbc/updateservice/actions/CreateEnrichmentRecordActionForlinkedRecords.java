/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcField;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.common.records.MarcSubField;
import dk.dbc.common.records.utils.LogUtils;
import dk.dbc.common.records.utils.RecordContentTransformer;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.utils.MDCUtil;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

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
    private static final XLogger logger = XLoggerFactory.getXLogger(CreateEnrichmentRecordActionForlinkedRecords.class);
    private final static String RECATEGORIZATION_STRING = "Sammenlagt med post med faustnummer %s";
    private final static String ERRONEOUS_RECATEGORIZATION_STRING = "Manglende data i posten til at skabe korrekt y08 for faustnummer %s";
    private final static String RECATEGORIZATION_STRING_OBSOLETE = " Postens opstilling ændret på grund af omkatalogisering";
    private static final String MIMETYPE = MarcXChangeMimeType.ENRICHMENT;

    private MarcRecord recordWithHoldings;
    private int agencyId;
    private MarcRecord record;
    private Properties settings;

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

    public void setRecord(MarcRecord record) {
        this.record = record;
    }

    /**
     * Performs this actions and may create any child actions.
     *
     * @return A list of ValidationError to be reported in the web service response.
     * @throws UpdateException In case of an error.
     */
    @Override
    public ServiceResult performAction() throws UpdateException {
        logger.entry();
        try {
            logger.info("Handling record: {}", LogUtils.base64Encode(record));

            MarcRecord enrichmentRecord = createEnrichmentRecord();
            if (enrichmentRecord.getFields().isEmpty()) {
                logger.info("No sub actions to create for an empty enrichment record.");
                return ServiceResult.newOkResult();
            }
            logger.info("Creating sub actions to store new enrichment record.");
            logger.info("Enrichment record:\n{}", enrichmentRecord);

            String recordId = new MarcRecordReader(enrichmentRecord).getRecordId();
            StoreRecordAction storeRecordAction = new StoreRecordAction(state, settings, enrichmentRecord);
            storeRecordAction.setMimetype(MIMETYPE);

            children.add(storeRecordAction);

            LinkRecordAction linkRecordAction = new LinkRecordAction(state, enrichmentRecord);
            linkRecordAction.setLinkToRecordId(new RecordId(recordId, RawRepo.COMMON_AGENCY));
            children.add(linkRecordAction);

            EnqueueRecordAction enqueueRecordAction = new EnqueueRecordAction(state, settings, enrichmentRecord);
            children.add(enqueueRecordAction);
            return ServiceResult.newOkResult();
        } finally {
            logger.exit();
        }
    }

    @Override
    public void setupMDCContext() {
        MDCUtil.setupContextForEnrichmentRecord(record, Integer.toString(agencyId));
    }

    protected MarcRecord loadRecord(String recordId, Integer agencyId) throws UpdateException {
        logger.entry(recordId, agencyId);
        MarcRecord result = null;
        try {
            Record record = rawRepo.fetchRecord(recordId, agencyId);
            return result = RecordContentTransformer.decodeRecord(record.getContent());
        } catch (UnsupportedEncodingException e) {
            throw new UpdateException(e.getMessage(), e);
        } finally {
            logger.exit(result);
        }
    }

    private MarcRecord createEnrichmentRecord() throws UpdateException {
        logger.entry();
        MarcRecord enrichmentRecord = new MarcRecord();
        try {
            MarcRecordReader recordReader = new MarcRecordReader(record);
            String recordId = recordReader.getRecordId();
            MarcRecordWriter enrichmentRecordWriter = new MarcRecordWriter(enrichmentRecord);
            if (rawRepo.recordExists(recordId, agencyId)) {
                enrichmentRecord = loadRecord(recordId, agencyId);
            } else {
                enrichmentRecordWriter.copyFieldsFromRecord(Arrays.asList("001", "004"), record);
                enrichmentRecordWriter.addOrReplaceSubfield("001", "b", Integer.toString(agencyId));
            }
            enrichmentRecord.getFields().add(getFormattedY08Field(recordWithHoldings));
            return enrichmentRecord;
        } finally {
            logger.exit(enrichmentRecord);
        }
    }

    private MarcField getFormattedY08Field(MarcRecord rec) {
        logger.entry(rec);
        MarcField yNoteField = new MarcField("y08", "00");
        try {
            MarcRecordReader reader = new MarcRecordReader(rec);
            String faust = reader.getValue("001", "a");
            String faustWithIntro;
            try {
                MarcField noteField = state.getLibraryRecordsHandler().fetchNoteField(rec);
                String yNoteFieldString = getNoteFieldString(noteField);
                if (yNoteFieldString == null) {
                    faustWithIntro = String.format(ERRONEOUS_RECATEGORIZATION_STRING, faust);
                } else {
                    faustWithIntro = String.format(RECATEGORIZATION_STRING, faust).concat(" " + yNoteFieldString);
                    faustWithIntro = faustWithIntro.replace(RECATEGORIZATION_STRING_OBSOLETE, "");
                }
                yNoteField.getSubfields().add(new MarcSubField("a", faustWithIntro));
                return yNoteField;
            } catch (UpdateException e) {
                logger.error("Error : UpdateException, probably due to malformed record \n", e);
                yNoteField.getSubfields().add(new MarcSubField("a", String.format(ERRONEOUS_RECATEGORIZATION_STRING, faust)));
                return yNoteField;
            }
        } finally {
            logger.exit(yNoteField);
        }
    }

    private String getNoteFieldString(MarcField noteField) {
        logger.entry(noteField);
        String res = "";
        try {
            if (noteField.getSubfields() == null || noteField.getSubfields().isEmpty()) {
                return null;
            }

            List<MarcSubField> subFields = noteField.getSubfields();
            for (Iterator<MarcSubField> mfIter = subFields.listIterator(); mfIter.hasNext(); ) {
                MarcSubField sf = mfIter.next();
                logger.trace("working on subfield : " + sf);
                if (mfIter.hasNext()) {
                    if (sf.getName().equals("d")) {
                        res = res.concat(sf.getValue() + ": ");
                    } else {
                        res = res.concat(sf.getValue() + " ");
                    }
                } else {
                    res = res.concat(sf.getValue());
                }
            }
            return res;
        } finally {
            logger.exit(res);
        }
    }
}
