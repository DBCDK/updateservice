/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.common.records.MarcSubField;
import dk.dbc.updateservice.update.UpdateException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.util.Properties;

/**
 * Updates the classifications in a enrichment record from the classifications
 * from a common record.
 */
public class UpdateClassificationsInEnrichmentRecordAction extends CreateEnrichmentRecordWithClassificationsAction {
    private static final XLogger logger = XLoggerFactory.getXLogger(UpdateClassificationsInEnrichmentRecordAction.class);
    private final static String RECATEGORIZATION_STRING = "UPDATE posttypeskift";
    private final static String RECLASSIFICATION_STRING = "UPDATE opstillingsændring";

    GlobalActionState state;
    private MarcRecord enrichmentRecord;

    public UpdateClassificationsInEnrichmentRecordAction(GlobalActionState globalActionState, Properties properties, String agencyIdInput) {
        super(globalActionState, properties, agencyIdInput);
        state = globalActionState;
    }

    public MarcRecord getEnrichmentRecord() {
        return enrichmentRecord;
    }

    public void setEnrichmentRecord(MarcRecord enrichmentRecord) {
        this.enrichmentRecord = enrichmentRecord;
    }

    /**
     * updates the classifications in the enrichment record.
     *
     * @return The enrichment record after its classifications has been updated.
     * @throws UpdateException In case of en JavaScript error.
     */
    @Override
    public MarcRecord createRecord() throws UpdateException {
        logger.entry();
        try {
            if (updatingCommonRecord == null) {
                throw new IllegalStateException("updatingCommonRecord is not assigned a value");
            }

            if (enrichmentRecord == null) {
                throw new IllegalStateException("enrichmentRecord is not assigned a value");
            }

            if (state.getLibraryRecordsHandler() == null) {
                throw new IllegalStateException("recordsHandler is not assigned a value");
            }
            MarcRecord record = state.getLibraryRecordsHandler().updateLibraryExtendedRecord(currentCommonRecord, updatingCommonRecord, enrichmentRecord);
            MarcRecordReader reader = new MarcRecordReader(record);
            MarcRecordWriter writer = new MarcRecordWriter(record);

            // When categorization has changed in the common record an y08 *a note must be added
            if (!reader.hasValue("y08", "a", RECATEGORIZATION_STRING)) {
                // If there already is an y08 *a subfield but it contains a different kind of note then keep that note
                if (reader.hasSubfield("y08", "a")) {
                    reader.getField("y08").getSubfields().add(new MarcSubField("a", RECLASSIFICATION_STRING));
                } else {
                    writer.addOrReplaceSubfield("y08", "a", RECLASSIFICATION_STRING);
                }
                writer.setChangedTimestamp();
            }
            writer.sort();
            return record;
        } finally {
            logger.exit();
        }
    }
}
