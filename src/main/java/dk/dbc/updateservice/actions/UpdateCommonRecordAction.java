package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.marc.binding.DataField;
import dk.dbc.marc.binding.MarcRecord;
import dk.dbc.marc.binding.SubField;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.SolrServiceIndexer;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.update.UpdateRecordContentTransformer;
import dk.dbc.updateservice.utils.DeferredLogger;
import dk.dbc.vipcore.exception.VipCoreException;
import dk.dbc.vipcore.libraryrules.VipCoreLibraryRulesConnector.Rule;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import static dk.dbc.marc.binding.DataField.hasSubFieldCode;
import static dk.dbc.updateservice.update.RawRepo.COMMON_AGENCY;

/**
 * This action is used to update a common record.
 * <p>
 * This action does not actual update the enrichment record, but creates child
 * actions to do the actual update. The record is checked for integrity so
 * the data model is not violated.
 * </p>
 */
public class UpdateCommonRecordAction extends AbstractRawRepoAction {
    private static final DeferredLogger LOGGER = new DeferredLogger(UpdateCommonRecordAction.class);

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
    public ServiceResult performAction() throws UpdateException {
        return LOGGER.callChecked(log -> {
            try {
                final MarcRecordReader reader = new MarcRecordReader(marcRecord);
                if (!reader.markedForDeletion()) {
                    log.info("Update single");
                    if (COMMON_AGENCY == reader.getAgencyIdAsInt() && state.getSolrFBS().hasDocuments(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", reader.getRecordId()))) {
                        final String message = state.getMessages().getString("update.record.with.002.links");
                        log.error("Unable to create sub actions due to an error: {}", message);
                        return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
                    }
                }

                checkOveCodes();

                MarcRecord recordToStore;

                // At this point we know the following:
                // - The record is a common record
                // - The record has been authenticated and validated
                // - Common records can contain authority fields
                // We also know that:
                // - Cicero client doesn't understand authority fields
                //
                // Therefore we need to collapse the incoming expanded record and pass that record to the later actions
                final String groupId = state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId();

                if ("DBC".equals(reader.getValue("996", 'a')) && state.getLibraryGroup().isFBS() && state.getRawRepo().recordExists(reader.getRecordId(), reader.getAgencyIdAsInt())) {
                    final MarcRecord currentRecord = UpdateRecordContentTransformer.decodeRecord(state.getRawRepo().fetchRecord(reader.getRecordId(), reader.getAgencyIdAsInt()).getContent());
                    final MarcRecord collapsedRecord = state.getNoteAndSubjectExtensionsHandler().collapse(marcRecord, currentRecord,
                            groupId, state.getNoteAndSubjectExtensionsHandler().isPublishedDBCRecord(currentRecord));
                    recordToStore = state.getRecordSorter().sortRecord(collapsedRecord);
                } else {
                    recordToStore = marcRecord;
                }

                // At this point we have the collapsed record with authority fields, so perform validation on those now
                for (DataField field : recordToStore.getFields(DataField.class)) {
                    if (RawRepo.AUTHORITY_FIELDS.contains(field.getTag()) && field.hasSubField(hasSubFieldCode('5')) && field.hasSubField(hasSubFieldCode('6'))) {
                        final String authRecordId = field.getSubField(hasSubFieldCode('6')).orElseThrow().getData();
                        final int authAgencyId = Integer.parseInt(field.getSubField(hasSubFieldCode('5')).orElseThrow().getData());
                        if (!state.getRawRepo().recordExists(authRecordId, authAgencyId)) {
                            String message = String.format(state.getMessages().getString("ref.record.doesnt.exist"), authRecordId, authAgencyId);
                            log.error(message);
                            return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
                        }
                    }
                }

                // It is here we decide whether it's a single record or a volume/section record
                // If there is a field 014 either without a subfield x or if the content of subfield x is ANM
                // then the record is part of a volume/section/head structure.
                final String parentId = reader.getParentRecordId();
                if (parentId != null && !parentId.isEmpty()) {
                    log.info("Update vol: {}", parentId);
                    children.add(new UpdateVolumeRecord(state, settings, recordToStore));
                } else {
                    log.info("Update single");
                    children.add(new UpdateSingleRecord(state, settings, recordToStore));
                }
                return ServiceResult.newOkResult();
            } catch (VipCoreException | UpdateException e) {
                return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, e.getMessage());
            }
        });
    }

    /*
        This function checks if the content of 032 violates any rules. If the records does not live up to the rules an
        exception is thrown.
     */
    private void checkOveCodes() throws UpdateException, VipCoreException {
        final MarcRecordReader reader = new MarcRecordReader(marcRecord);
        final String groupId = state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId();
        final String recordId = reader.getRecordId();
        final int agencyIdAsInt = reader.getAgencyIdAsInt();

        // The rules for field 032 only apply to 870970 records and non-root libraries
        if (COMMON_AGENCY != agencyIdAsInt || state.getVipCoreService().hasFeature(groupId, Rule.AUTH_ROOT)) {
            return;
        }

        final boolean recordExists = state.getRawRepo().recordExistsMaybeDeleted(recordId, agencyIdAsInt);
        if (recordExists) {
            final MarcRecord curRecord = UpdateRecordContentTransformer.decodeRecord(state.getRawRepo().fetchRecord(recordId, COMMON_AGENCY).getContent());

            checkExisingOveCodes(curRecord);
        } else {
            checkNewOveCodes();
        }
    }

    private List<SubField> getSubfieldsOrEmptyList(MarcRecord marcRecord, String fieldName) {
        final MarcRecordReader reader = new MarcRecordReader(marcRecord);

        if (reader.hasField(fieldName)) {
            return reader.getField(fieldName).getSubFields();
        } else {
            return new ArrayList<>();
        }
    }

    /*
       This function checks that the content of field 032 (if present) is allowed when updating a record
       -    If the library has regional obligations rule then 032 *x OVE subfields can be changed
       -    No non-root library is allowed to change any other subfield or value in field 032

       This function assumes:
        - The record is a common record
        - The agency does not have auth_root rule
        - It is an existing record
     */
    void checkExisingOveCodes(MarcRecord curRecord) throws UpdateException, VipCoreException {
        final List<SubField> new032Subfields = getSubfieldsOrEmptyList(marcRecord, "032");
        final List<SubField> cur032Subfields = getSubfieldsOrEmptyList(curRecord, "032");

        if (new032Subfields.equals(cur032Subfields)) {
            // No changes in 032 at all, so stop checking
            return;
        }

        final String groupId = state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId();
        final List<SubField> new032NonOveSubfields = new032Subfields.stream().filter(subfield -> 'a' == subfield.getCode()
                || 'x' == subfield.getCode() && !subfield.getData().startsWith("OVE")).collect(Collectors.toList());
        final List<SubField> cur032NonOveSubfields = cur032Subfields.stream().filter(subfield -> 'a' == subfield.getCode()
                || 'x' == subfield.getCode() && !subfield.getData().startsWith("OVE")).collect(Collectors.toList());

        // FBS libraries are not allowed to change existing non-OVE 032 subfields
        if (!new032NonOveSubfields.equals(cur032NonOveSubfields)) {
            throw new UpdateException(state.getMessages().getString("update.library.record.catalog.codes.changed"));
        }

        final List<SubField> new032OveSubfields = new032Subfields.stream()
                .filter(subfield -> 'x' == subfield.getCode() && subfield.getData().startsWith("OVE")).collect(Collectors.toList());
        final List<SubField> cur032OveSubfields = cur032Subfields.stream()
                .filter(subfield -> 'x' == subfield.getCode() && subfield.getData().startsWith("OVE")).collect(Collectors.toList());

        // Only CB libraries are allowed to change OVE subfields
        if (!new032OveSubfields.equals(cur032OveSubfields) && !state.getVipCoreService().hasFeature(groupId, Rule.REGIONAL_OBLIGATIONS)) {
            throw new UpdateException(state.getMessages().getString("update.library.record.catalog.codes.not.cb"));
        }
    }

    /*
       This function checks that the content of field 032 (if present) in a new record is allowed
       -    If the library has regional obligations rule then 032 *x OVE is allowed
       -    No non-root library is allowed to add any other subfields or values to 032

       This function assumes:
        - The record is a common record
        - The agency does not have auth_root rule
        - It is a new record
     */
    private void checkNewOveCodes() throws UpdateException, VipCoreException {
        final String groupId = state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId();
        final List<SubField> new032Subfields = getSubfieldsOrEmptyList(marcRecord, "032");
        final List<SubField> new032NonOveSubfields = new032Subfields.stream().filter(subfield -> 'a' == subfield.getCode()
                || 'x' == subfield.getCode() && !subfield.getData().startsWith("OVE")).collect(Collectors.toList());
        final List<SubField> new032OveSubfields = new032Subfields.stream()
                .filter(subfield -> 'x' == subfield.getCode() && subfield.getData().startsWith("OVE")).collect(Collectors.toList());

        if (!new032NonOveSubfields.isEmpty()) {
            throw new UpdateException(state.getMessages().getString("update.library.record.catalog.codes.changed"));
        }

        if (!new032OveSubfields.isEmpty() && !state.getVipCoreService().hasFeature(groupId, Rule.REGIONAL_OBLIGATIONS)) {
            throw new UpdateException(state.getMessages().getString("update.library.record.catalog.codes.not.cb"));
        }
    }

}
