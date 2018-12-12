/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.CatalogExtractionCode;
import dk.dbc.common.records.MarcField;
import dk.dbc.common.records.MarcFieldReader;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.common.records.MarcSubField;
import dk.dbc.common.records.utils.LogUtils;
import dk.dbc.common.records.utils.RecordContentTransformer;
import dk.dbc.openagency.client.OpenAgencyException;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.SolrException;
import dk.dbc.updateservice.update.SolrServiceIndexer;
import dk.dbc.updateservice.update.UpdateException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * This action is used to update a common record.
 * <p>
 * This action does not actual update the enrichment record, but creates child
 * actions to do the actual update. The record is checked for integrity so
 * the data model is not violated.
 * </p>
 */
public class UpdateCommonRecordAction extends AbstractRawRepoAction {
    private static final XLogger logger = XLoggerFactory.getXLogger(UpdateCommonRecordAction.class);

    private Properties settings;

    private static final List<String> metacompassSubFieldsToCopy = Arrays.asList("e", "i", "g");

    public UpdateCommonRecordAction(GlobalActionState globalActionState, Properties properties, MarcRecord record) {
        super(UpdateCommonRecordAction.class.getSimpleName(), globalActionState, record);
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
        logger.entry();
        try {
            logger.info("Handling record: {}", LogUtils.base64Encode(record));

            MarcRecordReader reader = new MarcRecordReader(record);
            if (!reader.markedForDeletion()) {
                logger.info("Update single");
                if (RawRepo.COMMON_AGENCY == reader.getAgencyIdAsInt() && state.getSolrService().hasDocuments(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", reader.getRecordId()))) {
                    String message = state.getMessages().getString("update.record.with.002.links");
                    logger.error("Unable to create sub actions due to an error: {}", message);
                    return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state);
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
            String groupId = state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId();

            if ("DBC".equals(reader.getValue("996", "a")) && state.getLibraryGroup().isFBS() && state.getRawRepo().recordExists(reader.getRecordId(), reader.getAgencyIdAsInt())) {
                MarcRecord currentRecord = RecordContentTransformer.decodeRecord(state.getRawRepo().fetchRecord(reader.getRecordId(), reader.getAgencyIdAsInt()).getContent());
                MarcRecord collapsedRecord = state.getNoteAndSubjectExtensionsHandler().collapse(record, currentRecord, groupId, state.getNoteAndSubjectExtensionsHandler().isNationalCommonRecord(record));
                recordToStore = state.getRecordSorter().sortRecord(collapsedRecord, settings);
            } else {
                recordToStore = record;
            }

            // At this point we have the collapsed record with authority fields, so perform validation on those now
            for (MarcField field : recordToStore.getFields()) {
                MarcFieldReader fieldReader = new MarcFieldReader(field);
                if (RawRepo.AUTHORITY_FIELDS.contains(field.getName()) && fieldReader.hasSubfield("5") && fieldReader.hasSubfield("6")) {
                    String authRecordId = fieldReader.getValue("6");
                    int authAgencyId = Integer.parseInt(fieldReader.getValue("5"));
                    if (!state.getRawRepo().recordExists(authRecordId, authAgencyId)) {
                        String message = String.format(state.getMessages().getString("auth.record.doesnt.exist"), authRecordId, authAgencyId);
                        logger.error(message);
                        return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state);
                    }
                }
            }

            if (RawRepo.COMMON_AGENCY == reader.getAgencyIdAsInt()) {
                logger.info("Rewriting indicators");
                rewriteIndicators();
            }

            if (!CatalogExtractionCode.isPublished(record)) {
                logger.info("Record is under production - checking if there are any metacompass fields to copy to 666");
                copyMetaCompassFields();
            }

            // It is here we decide whether it's a single record or a volume/section record
            // If there is a field 014 either without a subfield x or if the content of subfield x is ANM
            // then the record is part of a volume/section/head structure.
            String parentId = reader.getParentRecordId();
            if (parentId != null && !parentId.isEmpty()) {
                logger.info("Update vol: {}", parentId);
                children.add(new UpdateVolumeRecord(state, settings, recordToStore));
            } else {
                logger.info("Update single");
                children.add(new UpdateSingleRecord(state, settings, recordToStore));
            }
            return ServiceResult.newOkResult();
        } catch (OpenAgencyException | UnsupportedEncodingException e) {
            logger.catching(e);
            throw new UpdateException("Exception while collapsing record", e);
        } finally {
            logger.exit();
        }
    }

    private void rewriteIndicators() {
        logger.entry();
        try {
            MarcRecordWriter writer = new MarcRecordWriter(record);
            for (MarcField field : writer.getRecord().getFields()) {
                if (field.getName().equals("700")) {
                    boolean write02 = false;
                    for (MarcSubField sf : field.getSubfields()) {
                        if (sf.getName().equals("g") && sf.getValue().equals("1")) {
                            field.setIndicator("01");
                            write02 = false;
                            break;
                        } else if (sf.getName().equals("4") && sf.getValue().equals("led")) {
                            write02 = true;
                        }
                    }
                    if (write02) {
                        field.setIndicator("02");
                    }
                }
            }
        } finally {
            logger.exit();
        }
    }

    /**
     * If the record is still under production then all 665 *q, *e, *i and *g subfields must be copied to 666
     */
    void copyMetaCompassFields() {
        final List<MarcSubField> subfieldsToCopy = new ArrayList<>();
        final List<MarcField> fields665 = record.getFields().stream().
                filter(field -> "665".equals(field.getName())).
                collect(Collectors.toList());

        for (MarcField field : fields665) {
            if (field.getSubfields().stream().
                    anyMatch(subfield -> "&".equals(subfield.getName()) && "LEKTOR".equals(subfield.getValue()))) {
                for (MarcSubField subfield : field.getSubfields()) {
                    // 665 *q -> 666 *q
                    if ("q".equals(subfield.getName())) {
                        subfieldsToCopy.add(new MarcSubField("q", subfield.getValue()));
                    }

                    // 665 *e/*i/*g -> 666 *s
                    if (metacompassSubFieldsToCopy.contains(subfield.getName())) {
                        subfieldsToCopy.add(new MarcSubField("s", subfield.getValue()));
                    }
                }
            }
        }

        if (subfieldsToCopy.size() > 0) {
            // Fields added by automation should always have an empty *0
            final MarcSubField subfield0 = new MarcSubField("0", "");
            final List<MarcField> fields666 = record.getFields().stream().
                    filter(field -> "666".equals(field.getName())).
                    collect(Collectors.toList());

            for (MarcSubField subfieldToCopy : subfieldsToCopy) {
                boolean hasSubfield = false;
                for (MarcField field666 : fields666) {
                    if (field666.getSubfields().contains(subfieldToCopy)) {
                        // If the field has the subfield to copy but doesn't have *0 subfield then *0 must be added
                        if (!field666.getSubfields().contains(subfield0)) {
                            field666.getSubfields().add(0, subfield0);
                        }

                        hasSubfield = true;
                        break;
                    }
                }

                if (!hasSubfield) {
                    record.getFields().add(new MarcField("666", "00", Arrays.asList(subfield0, subfieldToCopy)));
                }
            }
        }
    }
}
