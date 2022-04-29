/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcField;
import dk.dbc.common.records.MarcFieldReader;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.utils.LogUtils;
import dk.dbc.common.records.utils.RecordContentTransformer;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.SolrException;
import dk.dbc.updateservice.update.SolrServiceIndexer;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.vipcore.exception.VipCoreException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.UnsupportedEncodingException;
import java.util.Properties;

/**
 * This action is used to update a common record.
 * <p>
 * This action does not actual update the enrichment record, but creates child
 * actions to do the actual update. The record is checked for integrity so
 * the data model is not violated.
 * </p>
 */
public class UpdateCommonRecordAction extends AbstractRawRepoAction {
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(UpdateCommonRecordAction.class);

    private final Properties settings;

    public UpdateCommonRecordAction(GlobalActionState globalActionState, Properties properties, MarcRecord marcRecord) {
        super(UpdateCommonRecordAction.class.getSimpleName(), globalActionState, marcRecord);
        settings = properties;
    }

    /**
     * Performs this actions and may create any child actions.
     *
     * @return A ServiceResult to be reported in the web service response.
     * @throws UpdateException In case of an error.
     */
    @Override
    public ServiceResult performAction() throws UpdateException, SolrException {
        try {
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Handling record: {}", LogUtils.base64Encode(marcRecord));
            }

            final MarcRecordReader reader = new MarcRecordReader(marcRecord);
            if (!reader.markedForDeletion()) {
                LOGGER.info("Update single");
                if (RawRepo.COMMON_AGENCY == reader.getAgencyIdAsInt() && state.getSolrFBS().hasDocuments(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", reader.getRecordId()))) {
                    final String message = state.getMessages().getString("update.record.with.002.links");
                    LOGGER.error("Unable to create sub actions due to an error: {}", message);
                    return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
                }
            }

            MarcRecord recordToStore;

            // At this point we know the following:
            // - The record is a common record
            // - The record has been authenticated and validated
            // - Common records can contain authority fields
            // We also know that:
            // - Cicero client doesn't understand authority fields
            //
            // Therefor we need to collapse the incoming expanded record and pass that record to the later actions
            final String groupId = state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId();

            if ("DBC".equals(reader.getValue("996", "a")) && state.getLibraryGroup().isFBS() && state.getRawRepo().recordExists(reader.getRecordId(), reader.getAgencyIdAsInt())) {
                final MarcRecord currentRecord = RecordContentTransformer.decodeRecord(state.getRawRepo().fetchRecord(reader.getRecordId(), reader.getAgencyIdAsInt()).getContent());
                final MarcRecord collapsedRecord = state.getNoteAndSubjectExtensionsHandler().collapse(marcRecord, currentRecord, groupId, state.getNoteAndSubjectExtensionsHandler().isPublishedDBCRecord(marcRecord));
                recordToStore = state.getRecordSorter().sortRecord(collapsedRecord);
            } else {
                recordToStore = marcRecord;
            }

            // At this point we have the collapsed record with authority fields, so perform validation on those now
            for (MarcField field : recordToStore.getFields()) {
                final MarcFieldReader fieldReader = new MarcFieldReader(field);
                if (RawRepo.AUTHORITY_FIELDS.contains(field.getName()) && fieldReader.hasSubfield("5") && fieldReader.hasSubfield("6")) {
                    final String authRecordId = fieldReader.getValue("6");
                    final int authAgencyId = Integer.parseInt(fieldReader.getValue("5"));
                    if (!state.getRawRepo().recordExists(authRecordId, authAgencyId)) {
                        String message = String.format(state.getMessages().getString("ref.record.doesnt.exist"), authRecordId, authAgencyId);
                        LOGGER.error(message);
                        return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
                    }
                }
            }

            // It is here we decide whether it's a single record or a volume/section record
            // If there is a field 014 either without a subfield x or if the content of subfield x is ANM
            // then the record is part of a volume/section/head structure.
            final String parentId = reader.getParentRecordId();
            if (parentId != null && !parentId.isEmpty()) {
                LOGGER.info("Update vol: {}", parentId);
                children.add(new UpdateVolumeRecord(state, settings, recordToStore));
            } else {
                LOGGER.info("Update single");
                children.add(new UpdateSingleRecord(state, settings, recordToStore));
            }
            return ServiceResult.newOkResult();
        } catch (VipCoreException | UnsupportedEncodingException e) {
            LOGGER.catching(e);
            throw new UpdateException("Exception while collapsing record", e);
        }
    }

}
