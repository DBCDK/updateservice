/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcField;
import dk.dbc.common.records.MarcFieldReader;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

public class LinkAuthorityRecordsAction extends AbstractLinkRelationRecordsAction {

    private static final XLogger LOGGER = XLoggerFactory.getXLogger(LinkAuthorityRecordsAction.class);

    public LinkAuthorityRecordsAction(GlobalActionState globalActionState, MarcRecord marcRecord) {
        super(LinkAuthorityRecordsAction.class.getSimpleName(), globalActionState, marcRecord);
    }

    @Override
    public ServiceResult performAction() throws UpdateException {
        LOGGER.entry();
        ServiceResult result = null;
        try {
            final MarcRecordReader reader = new MarcRecordReader(marcRecord);
            final String recordId = reader.getRecordId();
            final int agencyId = reader.getAgencyIdAsInt();
            final RecordId recordIdObj = new RecordId(recordId, agencyId);

            for (MarcField field : marcRecord.getFields()) {
                final MarcFieldReader fieldReader = new MarcFieldReader(field);
                if (RawRepo.AUTHORITY_FIELDS.contains(field.getName()) && fieldReader.hasSubfield("5") && fieldReader.hasSubfield("6")) {
                    final String authRecordId = fieldReader.getValue("6");
                    final int authAgencyId = Integer.parseInt(fieldReader.getValue("5"));

                    result = checkIfReferenceExists(authRecordId, authAgencyId);
                    if (result != null) {
                        return result;
                    }

                    appendLinkReference(recordIdObj, new RecordId(authRecordId, authAgencyId));
                }
            }

            return result = ServiceResult.newOkResult();
        } finally {
            LOGGER.exit(result);
        }
    }

}
