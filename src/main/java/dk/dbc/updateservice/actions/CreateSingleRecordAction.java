/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.rawrepo.Record;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.SolrServiceIndexer;
import dk.dbc.updateservice.update.UpdateException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * This action is used to create a new common record.
 */
public class CreateSingleRecordAction extends AbstractRawRepoAction {
    private static final XLogger logger = XLoggerFactory.getXLogger(CreateSingleRecordAction.class);

    Properties settings;

    public CreateSingleRecordAction(GlobalActionState globalActionState, Properties properties, MarcRecord marcRecord) {
        super(CreateSingleRecordAction.class.getSimpleName(), globalActionState, marcRecord);
        settings = properties;
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
            logger.info("Handling record:\n{}", record);
            MarcRecordReader reader = new MarcRecordReader(record);

            if (!checkIfRecordCanBeRestored(state, record)) {
                String message = state.getMessages().getString("create.record.with.locals");
                logger.error("Unable to create sub actions due to an error: {}", message);
                return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state);
            }

            if (RawRepo.COMMON_AGENCY.equals(reader.agencyIdAsInteger()) && state.getSolrService().hasDocuments(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", reader.recordId()))) {
                String message = state.getMessages().getString("update.record.with.002.links");
                logger.error("Unable to create sub actions due to an error: {}", message);
                return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state);
            }
            logger.info("Creating sub actions successfully");

            children.add(StoreRecordAction.newStoreMarcXChangeAction(state, settings, record));
            children.add(EnqueueRecordAction.newEnqueueAction(state, record, settings));
            children.add(new LinkAuthorityRecordsAction(state, record));
            return ServiceResult.newOkResult();
        } finally {
            logger.exit();
        }
    }

    static boolean checkIfRecordCanBeRestored(GlobalActionState state, MarcRecord record) throws UpdateException{
        MarcRecordReader reader = new MarcRecordReader(record);

        // The only records we are interested in are MarcXchange and Articles with different recordId
        Set<Integer> agenciesForRecord = state.getRawRepo().agenciesForRecordAll(record);
        Set<Integer> listToCheck = new HashSet<>();
        for (Integer agencyId : agenciesForRecord) {
            if (!agencyId.equals(reader.agencyIdAsInteger())) {
                Record r = state.getRawRepo().fetchRecord(reader.recordId(), agencyId);
                if (!MarcXChangeMimeType.ENRICHMENT.equals(r.getMimeType())) {
                    listToCheck.add(agencyId);
                }
            }
        }

        // The rule is: FBS and DBC libraries cannot have overlapping records.
        // However, FFU libraries are allowed to have overlapping posts as they never use enrichment posts
        if (!listToCheck.isEmpty()) {
            logger.info("The agencies {} was found for {}. Checking if all agencies are FFU - otherwise this action will fail", listToCheck, reader.recordId());
            Set<String> ffuAgencyIds = state.getFFULibraries();
            boolean allAgenciesAreFFU = true;
            for (Integer agencyForRecord : listToCheck) {
                if (!ffuAgencyIds.contains(agencyForRecord.toString())) {
                    logger.info("The library {} is not a FFU library.", agencyForRecord);
                    allAgenciesAreFFU = false;
                    break;
                }
            }

            if (!allAgenciesAreFFU) {
                return false;
            }
        }

        return true;
    }
}
