/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.utils.LogUtils;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.rawrepo.Record;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.SolrException;
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
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(CreateSingleRecordAction.class);

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
    public ServiceResult performAction() throws UpdateException, SolrException {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Handling record: {}", LogUtils.base64Encode(marcRecord));
        }
        final MarcRecordReader reader = new MarcRecordReader(marcRecord);

        if (!checkIfRecordCanBeRestored(state, marcRecord)) {
            final String message = state.getMessages().getString("create.record.with.locals");
            LOGGER.error("Unable to create sub actions due to an error: {}", message);
            return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
        }

        if (RawRepo.COMMON_AGENCY == reader.getAgencyIdAsInt() && state.getSolrFBS().hasDocuments(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", reader.getRecordId()))) {
            final String message = state.getMessages().getString("update.record.with.002.links");
            LOGGER.error("Unable to create sub actions due to an error: {}", message);
            return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
        }

        children.add(StoreRecordAction.newStoreMarcXChangeAction(state, settings, marcRecord));
        children.add(EnqueueRecordAction.newEnqueueAction(state, marcRecord, settings));
        if (RawRepo.MATVURD_AGENCY == reader.getAgencyIdAsInt()) {
            // Information that needs check is in the enrichment part so we have to look at the full request record
            children.add(new LinkMatVurdRecordsAction(state, state.readRecord()));
        }
        children.add(new LinkAuthorityRecordsAction(state, marcRecord));

        return ServiceResult.newOkResult();
    }

    static boolean checkIfRecordCanBeRestored(GlobalActionState state, MarcRecord record) throws UpdateException {
        final MarcRecordReader reader = new MarcRecordReader(record);

        // The only records we are interested in are MarcXchange and Articles with different recordId
        final Set<Integer> agenciesForRecord = state.getRawRepo().agenciesForRecordAll(record);
        agenciesForRecord.remove(reader.getAgencyIdAsInt());

        final Set<Integer> listToCheck = new HashSet<>();
        for (Integer agencyId : agenciesForRecord) {
            final Record r = state.getRawRepo().fetchRecord(reader.getRecordId(), agencyId);
            if (!MarcXChangeMimeType.ENRICHMENT.equals(r.getMimeType())) {
                listToCheck.add(agencyId);
            }
        }

        // The rule is: FBS and DBC libraries cannot have overlapping records.
        // However, FFU and LokBib libraries are allowed to have overlapping posts as they never use enrichment posts
        if (!listToCheck.isEmpty()) {
            LOGGER.info("The agencies {} was found for {}. Checking if all agencies are FFU or lokbib - otherwise this action will fail", listToCheck, reader.getRecordId());
            final Set<String> allowedOverlappingAgencies = state.getFFULibraries();
            allowedOverlappingAgencies.addAll(state.getLokbibLibraries());
            boolean allAgenciesAreFFU = true;
            for (Integer agencyForRecord : listToCheck) {
                if (!allowedOverlappingAgencies.contains(agencyForRecord.toString())) {
                    LOGGER.info("The library {} is not a FFU or lokbib library.", agencyForRecord);
                    allAgenciesAreFFU = false;
                    break;
                }
            }

            return allAgenciesAreFFU;
        }

        return true;
    }
}
