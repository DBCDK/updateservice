package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.marc.binding.DataField;
import dk.dbc.marc.binding.MarcRecord;
import dk.dbc.marc.binding.SubField;
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

        for (DataField field : marcRecord.getFields(DataField.class)) {
            if (RawRepo.MATVURD_FIELDS.contains(field.getTag())) {
                for (SubField subField : field.getSubFields()) {
                    if ('a' == subField.getCode()) {
                        final String refRecordId = subField.getData();
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
