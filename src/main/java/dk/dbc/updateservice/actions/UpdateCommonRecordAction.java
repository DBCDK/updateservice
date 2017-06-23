/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.*;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.SolrServiceIndexer;
import dk.dbc.updateservice.update.UpdateException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

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
    private static final XLogger logger = XLoggerFactory.getXLogger(UpdateCommonRecordAction.class);
    static final String MIMETYPE = MarcXChangeMimeType.MARCXCHANGE;

    private Properties settings;

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
    public ServiceResult performAction() throws UpdateException {
        logger.entry();
        try {
            logger.info("Handling record:\n{}", record);

            MarcRecordReader reader = new MarcRecordReader(record);
            if (!reader.markedForDeletion()) {
                logger.info("Update single");
                if (RawRepo.COMMON_AGENCY.equals(reader.agencyIdAsInteger()) && state.getSolrService().hasDocuments(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", reader.recordId()))) {
                    String message = state.getMessages().getString("update.record.with.002.links");
                    logger.error("Unable to create sub actions due to an error: {}", message);
                    return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state);
                }
            }

            if ((RawRepo.COMMON_AGENCY.equals(reader.agencyIdAsInteger()))) {
                logger.info("Rewriting indictators");
                rewriteIndicators();
            }
            String parentId = reader.parentRecordId();
            if (parentId != null && !parentId.isEmpty()) {
                logger.info("Update vol: {}", parentId);
                children.add(new UpdateVolumeRecord(state, settings, record));
            } else {
                logger.info("Update single");
                children.add(new UpdateSingleRecord(state, settings, record));
            }
            return ServiceResult.newOkResult();
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
}
