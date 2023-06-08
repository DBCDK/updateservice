package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.marc.binding.MarcRecord;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.update.UpdateRecordContentTransformer;
import dk.dbc.updateservice.utils.DeferredLogger;

import java.io.UnsupportedEncodingException;
import java.util.Properties;
import java.util.Set;

/**
 * Action to update a common school record.
 */
public class UpdateSchoolCommonRecord extends AbstractRawRepoAction {
    private static final DeferredLogger LOGGER = new DeferredLogger(UpdateSchoolCommonRecord.class);

    Properties settings;

    public UpdateSchoolCommonRecord(GlobalActionState globalActionState, Properties properties, MarcRecord marcRecord) {
        super(UpdateSchoolCommonRecord.class.getSimpleName(), globalActionState, marcRecord);
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
                final MarcRecordReader reader = new MarcRecordReader(marcRecord);
                if (reader.markedForDeletion()) {
                    moveSchoolEnrichmentsActions(RawRepo.COMMON_AGENCY);
                    children.add(new UpdateEnrichmentRecordAction(state, settings, marcRecord));
                } else {
                    children.add(new UpdateEnrichmentRecordAction(state, settings, marcRecord));
                    moveSchoolEnrichmentsActions(RawRepo.SCHOOL_COMMON_AGENCY);
                }
                return ServiceResult.newOkResult();
            } catch (UnsupportedEncodingException ex) {
                log.error(ex.getMessage(), ex);
                throw new UpdateException(ex.getMessage(), ex);
            }
        });
    }

    private void moveSchoolEnrichmentsActions(int target) throws UpdateException, UnsupportedEncodingException {
        final Set<Integer> agencies = rawRepo.agenciesForRecord(recordId.getBibliographicRecordId());
        if (agencies == null) {
            return;
        }
        final MarcRecordReader reader = new MarcRecordReader(marcRecord);
        final String recordId = reader.getRecordId();
        for (Integer agencyId : agencies) {
            if (!RawRepo.isSchoolEnrichment(agencyId)) {
                continue;
            }
            final Record rawRepoRecord = rawRepo.fetchRecord(recordId, agencyId);
            final MarcRecord enrichmentRecord = UpdateRecordContentTransformer.decodeRecord(rawRepoRecord.getContent());

            final LinkRecordAction linkRecordAction = new LinkRecordAction(state, enrichmentRecord);
            linkRecordAction.setLinkToRecordId(new RecordId(recordId, target));
            children.add(linkRecordAction);
            children.add(EnqueueRecordAction.newEnqueueAction(state, enrichmentRecord, settings));
        }
    }
}
