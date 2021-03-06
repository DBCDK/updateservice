/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.utils.LogUtils;
import dk.dbc.common.records.utils.RecordContentTransformer;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.UnsupportedEncodingException;
import java.util.Properties;
import java.util.Set;

/**
 * Action to update a common school record.
 */
public class UpdateSchoolCommonRecord extends AbstractRawRepoAction {
    private static final XLogger logger = XLoggerFactory.getXLogger(UpdateSchoolCommonRecord.class);

    Properties settings;

    public UpdateSchoolCommonRecord(GlobalActionState globalActionState, Properties properties, MarcRecord record) {
        super(UpdateSchoolCommonRecord.class.getSimpleName(), globalActionState, record);
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
        logger.entry();
        try {
            logger.info("Handling record: {}", LogUtils.base64Encode(record));
            MarcRecordReader reader = new MarcRecordReader(record);
            if (reader.markedForDeletion()) {
                moveSchoolEnrichmentsActions(RawRepo.COMMON_AGENCY);
                children.add(new UpdateEnrichmentRecordAction(state, settings, record));
            } else {
                children.add(new UpdateEnrichmentRecordAction(state, settings, record));
                moveSchoolEnrichmentsActions(RawRepo.SCHOOL_COMMON_AGENCY);
            }
            return ServiceResult.newOkResult();
        } catch (UnsupportedEncodingException ex) {
            logger.error(ex.getMessage(), ex);
            throw new UpdateException(ex.getMessage(), ex);
        } finally {
            logger.exit();
        }
    }

    private void moveSchoolEnrichmentsActions(int target) throws UpdateException, UnsupportedEncodingException {
        logger.entry();
        try {
            Set<Integer> agencies = rawRepo.agenciesForRecord(record);
            if (agencies == null) {
                return;
            }
            MarcRecordReader reader = new MarcRecordReader(record);
            String recordId = reader.getRecordId();
            for (Integer agencyId : agencies) {
                if (!RawRepo.isSchoolEnrichment(agencyId)) {
                    continue;
                }
                Record rawRepoRecord = rawRepo.fetchRecord(recordId, agencyId);
                MarcRecord enrichmentRecord = RecordContentTransformer.decodeRecord(rawRepoRecord.getContent());

                LinkRecordAction linkRecordAction = new LinkRecordAction(state, enrichmentRecord);
                linkRecordAction.setLinkToRecordId(new RecordId(recordId, target));
                children.add(linkRecordAction);
                children.add(EnqueueRecordAction.newEnqueueAction(state, enrichmentRecord, settings));
            }
        } finally {
            logger.exit();
        }
    }
}
