package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.marc.binding.MarcRecord;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.update.UpdateRecordContentTransformer;
import dk.dbc.updateservice.utils.DeferredLogger;

import java.io.UnsupportedEncodingException;
import java.util.Properties;
import java.util.Set;

/**
 * Action to delete a common record.
 * <p>
 * It supports single and volume records.
 * </p>
 */
public class DeleteCommonRecordAction extends AbstractRawRepoAction {
    private static final DeferredLogger LOGGER = new DeferredLogger(DeleteCommonRecordAction.class);

    Properties settings;

    public DeleteCommonRecordAction(GlobalActionState globalActionState, Properties properties, MarcRecord marcRecord) {
        super(DeleteCommonRecordAction.class.getSimpleName(), globalActionState, marcRecord);
        settings = properties;
    }

    /**
     * Performs this actions and may create any child actions.
     *
     * @return A ServiceResult to be reported in the web service response.
     * @throws UpdateException In case of an error.
     */
    @Override
    public ServiceResult performAction() throws UpdateException {
        return LOGGER.callChecked(log -> {
            try {
                final Set<RecordId> recordChildren = rawRepo.children(recordId);
                if (!recordChildren.isEmpty()) {
                    if (checkForNotDeletableLittolkChildren(recordChildren)) {
                        deleteLittolkChildren(recordChildren);
                    } else {
                        final MarcRecordReader reader = new MarcRecordReader(marcRecord);
                        final String recordId = reader.getRecordId();

                        final String message = state.getMessages().getString("delete.record.children.error");
                        final String errorMessage = String.format(message, recordId);

                        log.error("Unable to create sub actions due to an error: {}", errorMessage);
                        return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, errorMessage);
                    }
                }

                for (RecordId enrichmentId : rawRepo.enrichments(recordId)) {
                    final Record rawRepoEnrichmentRecord = rawRepo.fetchRecord(enrichmentId.getBibliographicRecordId(), enrichmentId.getAgencyId());
                    final MarcRecord enrichmentRecord = UpdateRecordContentTransformer.decodeRecord(rawRepoEnrichmentRecord.getContent());

                    final MarcRecordWriter writer = new MarcRecordWriter(enrichmentRecord);
                    writer.markForDeletion();

                    final UpdateEnrichmentRecordAction updateEnrichmentRecordAction = new UpdateEnrichmentRecordAction(state, settings, enrichmentRecord);

                    children.add(updateEnrichmentRecordAction);
                }

                children.add(EnqueueRecordAction.newEnqueueAction(state, marcRecord, settings));
                children.add(new RemoveLinksAction(state, marcRecord));
                children.add(DeleteRecordAction.newDeleteRecordAction(state, settings, marcRecord));

                return ServiceResult.newOkResult();
            } catch (UnsupportedEncodingException ex) {
                log.error(ex.getMessage(), ex);
                return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, ex.getMessage());
            }
        });
    }

    /**
     * Return true if all children are 870974 littolk records
     * <p>
     * Otherwise false
     *
     * @param recordChildren The list of RecordIds to check
     * @return true if all children are 870974 littolk records which can be deleted
     */
    private boolean checkForNotDeletableLittolkChildren(Set<RecordId> recordChildren) {
        for (RecordId recordId : recordChildren) {
            if (recordId.getAgencyId() != RawRepo.LITTOLK_AGENCY) {
                return false;
            }
        }

        return true;
    }

    /**
     * This function deletes every record plus its enrichment in recordChildren input
     *
     * @param recordChildren The list of records to delete
     * @throws UpdateException              In case of an error.
     * @throws UnsupportedEncodingException If the record can't be decoded
     */
    private void deleteLittolkChildren(Set<RecordId> recordChildren) throws UpdateException, UnsupportedEncodingException {
        LOGGER.<Void, UpdateException, UnsupportedEncodingException>callChecked2(log -> {
            for (RecordId recordId : recordChildren) {
                final MarcRecord littolkEnrichment = UpdateRecordContentTransformer.decodeRecord(rawRepo.fetchRecord(recordId.getBibliographicRecordId(), RawRepo.DBC_ENRICHMENT).getContent());
                log.info("Creating DeleteRecordAction for {}:{}", recordId.getBibliographicRecordId(), RawRepo.DBC_ENRICHMENT);
                new MarcRecordWriter(littolkEnrichment).markForDeletion();
                children.add(new UpdateEnrichmentRecordAction(state, settings, littolkEnrichment));

                final MarcRecord littolkRecord = UpdateRecordContentTransformer.decodeRecord(rawRepo.fetchRecord(recordId.getBibliographicRecordId(), RawRepo.LITTOLK_AGENCY).getContent());
                log.info("Creating DeleteRecordAction for {}:{}", recordId.getBibliographicRecordId(), RawRepo.LITTOLK_AGENCY);
                children.add(new DeleteCommonRecordAction(state, settings, littolkRecord));
            }
            return null;
        });
    }

}
