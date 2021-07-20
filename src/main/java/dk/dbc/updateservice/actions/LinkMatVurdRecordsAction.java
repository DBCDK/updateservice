/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcField;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.MarcSubField;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;

public class LinkMatVurdRecordsAction extends AbstractLinkRelationRecordsAction {

    public LinkMatVurdRecordsAction(GlobalActionState globalActionState, MarcRecord marcRecord) {
        super(LinkMatVurdRecordsAction.class.getSimpleName(), globalActionState, marcRecord);
    }

    @Override
    public ServiceResult performAction() throws UpdateException {
        final MarcRecordReader reader = new MarcRecordReader(marcRecord);
        final String recordId = reader.getRecordId();
        final int agencyId = reader.getAgencyIdAsInt();
        final RecordId recordIdObj = new RecordId(recordId, agencyId);

        for (MarcField field : marcRecord.getFields()) {
            if (RawRepo.MATVURD_FIELDS.contains(field.getName())) {
                for (MarcSubField subField : field.getSubfields()) {
                    if ("a".equals(subField.getName())) {
                        final String refRecordId = subField.getValue();
                        final int refAgencyId = RawRepo.COMMON_AGENCY;

                        final ServiceResult result = checkIfReferenceExists(refRecordId, refAgencyId);
                        if (result != null) {
                            return result;
                        }

                        appendLinkReference(recordIdObj, new RecordId(refRecordId, refAgencyId));
                    }
                }
            }
        }

        return ServiceResult.newOkResult();
    }

}
