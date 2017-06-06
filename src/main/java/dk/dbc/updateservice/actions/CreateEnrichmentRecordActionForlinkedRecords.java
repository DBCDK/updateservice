/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.*;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.ws.MDCUtil;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

/**
 * Action to create a new enrichment record from a common record.
 * <p>
 * This action handles to case where we need to create a new enrichment
 * triggered when the classification data is updated in a common record and
 * there is no enrichment record for a agency that has holdings for the
 * common record.
 * </p>
 * <p>
 * The creation of the enrichment record is done by calling the JavaScript
 * engine (thought LibraryRecordsHandler) to produce the actual enrichment
 * record.
 * </p>
 */
public class CreateEnrichmentRecordActionForlinkedRecords extends AbstractAction {
    private static final XLogger logger = XLoggerFactory.getXLogger(CreateEnrichmentRecordActionForlinkedRecords.class);
    private final static String RECATEGORIZATION_STRING = "Sammenlagt med post med faustnummer %s";
    private final static String ERRORNOUS_RECATEGORIZATION_STRING = "Manglende data i posten til at skabe korrekt y08 for faustnummer %s";
    private final static String RECATEGORIZATION_STRING_OBSOLETE = " Postens opstilling ændret på grund af omkatalogisering";
    static final String MIMETYPE = MarcXChangeMimeType.ENRICHMENT;

    private List<MarcRecord> listOfRecordsToFetchClassificationDataFrom;
    private Integer agencyId;
    protected MarcRecord currentCommonRecord;
    protected MarcRecord updatingCommonRecord;
    private Properties settings;

    public CreateEnrichmentRecordActionForlinkedRecords(GlobalActionState globalActionState, Properties properties) {
        super(CreateEnrichmentRecordWithClassificationsAction.class.getSimpleName(), globalActionState);
        settings = properties;
    }

    public void setListOfRecordsToFetchClassificationDataFrom(List<MarcRecord> listOfRecordsToFetchClassificationDataFrom) {
        this.listOfRecordsToFetchClassificationDataFrom = listOfRecordsToFetchClassificationDataFrom;
    }

    public Integer getAgencyId() {
        return agencyId;
    }

    public void setAgencyId(Integer agencyId) {
        this.agencyId = agencyId;
    }

    public void setCurrentCommonRecord(MarcRecord currentCommonRecord) {
        this.currentCommonRecord = currentCommonRecord;
    }

    public void setUpdatingCommonRecord(MarcRecord updatingCommonRecord) {
        this.updatingCommonRecord = updatingCommonRecord;
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
            logger.info("Current common record:\n{}", currentCommonRecord);
            logger.info("Updating common record:\n{}", updatingCommonRecord);

            MarcRecord enrichmentRecord = createEnrichmentRecord();
            if (enrichmentRecord.getFields().isEmpty()) {
                logger.info("No sub actions to create for an empty enrichment record.");
                return ServiceResult.newOkResult();
            }
            logger.info("Creating sub actions to store new enrichment record.");
            logger.info("Enrichment record:\n{}", enrichmentRecord);

            String recordId = new MarcRecordReader(enrichmentRecord).recordId();
            StoreRecordAction storeRecordAction = new StoreRecordAction(state, settings, enrichmentRecord);
            storeRecordAction.setMimetype(MIMETYPE);

            children.add(storeRecordAction);

            LinkRecordAction linkRecordAction = new LinkRecordAction(state, enrichmentRecord);
            linkRecordAction.setLinkToRecordId(new RecordId(recordId, RawRepo.COMMON_AGENCY));
            children.add(linkRecordAction);

            EnqueueRecordAction enqueueRecordAction = new EnqueueRecordAction(state, settings, enrichmentRecord);
            children.add(enqueueRecordAction);
            return ServiceResult.newOkResult();
        } catch (ScripterException ex) {
            logger.error("Update error: " + ex.getMessage(), ex);
            throw new UpdateException(ex.getMessage(), ex);
        } finally {
            logger.exit();
        }
    }

    @Override
    public void setupMDCContext() {
        MDCUtil.setupContextForEnrichmentRecord(updatingCommonRecord, agencyId.toString());
    }

    private MarcRecord createEnrichmentRecord() throws ScripterException {
        logger.entry();
        MarcRecord enrichmentRecord = new MarcRecord();
        try {
            MarcRecordWriter enrichmentRecordWriter = new MarcRecordWriter(enrichmentRecord);
            enrichmentRecordWriter.copyFieldsFromRecord(Arrays.asList("001", "004"), updatingCommonRecord);
            enrichmentRecordWriter.addOrReplaceSubfield("001", "b", agencyId.toString());
            listOfRecordsToFetchClassificationDataFrom.forEach((rec) -> {
                enrichmentRecord.getFields().add(getFormatted004Field(rec));
            });
            return enrichmentRecord;
        } finally {
            logger.exit(enrichmentRecord);
        }
    }

    private MarcField getFormatted004Field(MarcRecord rec) {
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
                    faustWithIntro = String.format(ERRORNOUS_RECATEGORIZATION_STRING, faust);
                } else {
                    faustWithIntro = (String.format(RECATEGORIZATION_STRING, faust).concat(" " + yNoteFieldString));
                    faustWithIntro = faustWithIntro.replace(RECATEGORIZATION_STRING_OBSOLETE, "");
                }
                yNoteField.getSubfields().add(new MarcSubField("a", faustWithIntro));
                return yNoteField;
            } catch (ScripterException e) {
                logger.error("Error : Scripter exception , probally due to malformed record \n", e);
                yNoteField.getSubfields().add(new MarcSubField("a", String.format(ERRORNOUS_RECATEGORIZATION_STRING, faust)));
                return yNoteField;
            }
        } finally {
            logger.exit(yNoteField);
        }
    }

    private String getNoteFieldString(MarcField noteField) throws ScripterException {
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
