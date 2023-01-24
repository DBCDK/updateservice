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
import dk.dbc.updateservice.utils.DeferredLogger;
import dk.dbc.vipcore.exception.VipCoreException;

import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * This action is used to create a new common record.
 */
public class CreateSingleRecordAction extends AbstractRawRepoAction {
    private static final DeferredLogger LOGGER = new DeferredLogger(CreateSingleRecordAction.class);

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
        return LOGGER.<ServiceResult, UpdateException, SolrException>callChecked2(log -> {
            if (log.isInfoEnabled()) {
                log.info("Handling record: {}", LogUtils.base64Encode(marcRecord));
            }
            final MarcRecordReader reader = new MarcRecordReader(marcRecord);

            if (!checkIfRecordCanBeRestored(state, marcRecord)) {
                final String message = state.getMessages().getString("create.record.with.locals");
                log.error("Unable to create sub actions due to an error: {}", message);
                return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
            }

            if (RawRepo.COMMON_AGENCY == reader.getAgencyIdAsInt() && state.getSolrFBS().hasDocuments(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", reader.getRecordId()))) {
                final String message = state.getMessages().getString("update.record.with.002.links");
                log.error("Unable to create sub actions due to an error: {}", message);
                return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
            }

            children.add(StoreRecordAction.newStoreMarcXChangeAction(state, settings, marcRecord));
            children.add(EnqueueRecordAction.newEnqueueAction(state, marcRecord, settings));
            if (RawRepo.MATVURD_AGENCY == reader.getAgencyIdAsInt()) {
                // Information that needs check is in the enrichment part, so we have to look at the full request record
                children.add(new LinkMatVurdRecordsAction(state, state.readRecord()));
            }
            children.add(new LinkAuthorityRecordsAction(state, marcRecord));

            return ServiceResult.newOkResult();
        });
    }

    static boolean checkIfRecordCanBeRestored(GlobalActionState state, MarcRecord record) throws UpdateException {
        final MarcRecordReader reader = new MarcRecordReader(record);

        // The only records we are interested in are MarcXchange and Articles with different recordId
        final Set<Integer> agenciesForRecord = state.getRawRepo().agenciesForRecordAll(reader.getRecordId());
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
            return LOGGER.callChecked(log -> {
                log.info("The agencies {} was found for {}. Checking if all agencies are FFU or lokbib - otherwise this action will fail", listToCheck, reader.getRecordId());
                final Set<String> allowedOverlappingAgencies = state.getFFULibraries();
                allowedOverlappingAgencies.addAll(state.getLokbibLibraries());
                boolean allAgenciesAreFFU = true;
                for (Integer agencyForRecord : listToCheck) {
                    if (!allowedOverlappingAgencies.contains(agencyForRecord.toString())) {
                        // If the agency is marked as delete in vipcore it will not appear on the list of ffu agencies.
                        // To make sure that an agency not in the list is not a deleted ffu agency we have to perform one
                        // more lookup.
                        final String templateGroup;
                        try {
                            templateGroup = state.getVipCoreService().getTemplateGroup(Integer.toString(agencyForRecord));
                        } catch (VipCoreException e) {
                            throw new UpdateException("Could not get template group for " + agencyForRecord, e);
                        }
                        if ("ffu".equals(templateGroup)) {
                            state.getFFULibraries().add(Integer.toString(agencyForRecord));
                        } else if ("lokbib".equals(templateGroup)) {
                            state.getLokbibLibraries().add(Integer.toString(agencyForRecord));
                        } else {
                            log.info("The library {} is not a FFU or lokbib library.", agencyForRecord);
                            allAgenciesAreFFU = false;
                            break;
                        }
                    }
                }

                return allAgenciesAreFFU;
            });
        }

        return true;
    }
}
