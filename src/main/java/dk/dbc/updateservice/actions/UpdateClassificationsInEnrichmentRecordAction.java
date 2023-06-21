package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.marc.binding.DataField;
import dk.dbc.marc.binding.MarcRecord;
import dk.dbc.marc.binding.SubField;
import dk.dbc.updateservice.update.LibraryRecordsHandler;
import dk.dbc.updateservice.update.UpdateException;

import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Updates the classifications in an enrichment record from the classifications
 * from a common record.
 */
public class UpdateClassificationsInEnrichmentRecordAction extends UpdateEnrichmentRecordAction {
    private static final String RECATEGORIZATION_STRING = "UPDATE posttypeskift";
    private static final String RECLASSIFICATION_STRING = "UPDATE opstillingsændring";

    private MarcRecord currentCommonRecord = null;
    private MarcRecord updatingCommonRecord = null;

    public void setCurrentCommonRecord(MarcRecord currentCommonRecord) {
        this.currentCommonRecord = currentCommonRecord;
    }

    public void setUpdatingCommonRecord(MarcRecord updatingCommonRecord) {
        this.updatingCommonRecord = updatingCommonRecord;
    }

    public UpdateClassificationsInEnrichmentRecordAction(GlobalActionState globalActionState, Properties properties, MarcRecord marcRecord, String agencyIdInput) {
        super(globalActionState, properties, marcRecord, Integer.parseInt(agencyIdInput));
        state = globalActionState;
    }

    /**
     * Updates the classifications in the enrichment record.
     *
     * @return The enrichment record after its classifications has been updated.
     * @throws UpdateException In case of en JavaScript error.
     */
    @Override
    public ServiceResult performAction() throws UpdateException {
        if (state.getLibraryRecordsHandler().hasClassificationData(marcRecord) &&
                !state.getLibraryRecordsHandler().hasClassificationsChanged(marcRecord, updatingCommonRecord)) {
            handleSameClassification();
        } else {
            handleDifferentClassification();
        }

        return ServiceResult.newOkResult();
    }

    private void handleSameClassification() throws UpdateException {
        final List<String> nonClassificationFieldsLeft = marcRecord.getFields(DataField.class)
                .stream()
                .map(DataField::getTag)
                .collect(Collectors.toList());
        nonClassificationFieldsLeft.remove("001");
        nonClassificationFieldsLeft.remove("004");
        nonClassificationFieldsLeft.removeAll(LibraryRecordsHandler.CLASSIFICATION_FIELDS);

        if (nonClassificationFieldsLeft.isEmpty()) {
            // The enrichment has no relevant fields left, so just delete it
            performDeletionAction();
        } else {
            // Remove the classification fields
            final MarcRecordWriter enrichmentWriter = new MarcRecordWriter(marcRecord);
            enrichmentWriter.removeFields(LibraryRecordsHandler.CLASSIFICATION_FIELDS);
            performSaveRecord(marcRecord);
        }
    }

    private void handleDifferentClassification() throws UpdateException {
        final MarcRecord extendedRecord = state.getLibraryRecordsHandler().updateLibraryExtendedRecord(currentCommonRecord, updatingCommonRecord, marcRecord);
        final MarcRecordReader reader = new MarcRecordReader(extendedRecord);
        final MarcRecordWriter writer = new MarcRecordWriter(extendedRecord);

        // When categorization has changed in the common record a y08 *a note must be added
        if (!reader.hasValue("y08", 'a', RECATEGORIZATION_STRING)) {
            // If there already is a y08 *a subfield, but it contains a different kind of note then keep that note
            if (reader.hasSubfield("y08", 'a')) {
                reader.getField("y08").getSubFields().add(new SubField('a', RECLASSIFICATION_STRING));
            } else {
                writer.addOrReplaceSubField("y08", 'a', RECLASSIFICATION_STRING);
            }
            writer.setChangedTimestamp();

            writer.sort();
        }

        performSaveRecord(extendedRecord);
    }

}
