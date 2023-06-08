package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.marc.binding.DataField;
import dk.dbc.marc.binding.MarcRecord;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static dk.dbc.marc.binding.DataField.hasSubFieldCode;

public class LinkAuthorityRecordsAction extends AbstractLinkRelationRecordsAction {
    private static final Stream<String> AUTHORITY_RELATION_FIELDS = Stream.of("200", "210", "230", "232", "233", "234");
    private static final Set<String> AUTHORITY_ALL_FIELDS = Stream.concat(RawRepo.AUTHORITY_FIELDS.stream(), AUTHORITY_RELATION_FIELDS)
            .collect(Collectors.toUnmodifiableSet());

    public LinkAuthorityRecordsAction(GlobalActionState globalActionState, MarcRecord marcRecord) {
        super(LinkAuthorityRecordsAction.class.getSimpleName(), globalActionState, marcRecord);
    }

    @Override
    public ServiceResult performAction() throws UpdateException {
        final MarcRecordReader reader = new MarcRecordReader(marcRecord);
        final String recordId = reader.getRecordId();
        final int agencyId = reader.getAgencyIdAsInt();
        final RecordId recordIdObj = new RecordId(recordId, agencyId);

        for (DataField field : marcRecord.getFields(DataField.class)) {
            if (AUTHORITY_ALL_FIELDS.contains(field.getTag()) && field.hasSubField(hasSubFieldCode('5')) && field.hasSubField(hasSubFieldCode('6'))) {
                final String authRecordId = field.getSubField(hasSubFieldCode('6')).orElseThrow().getData();
                final int authAgencyId = Integer.parseInt(field.getSubField(hasSubFieldCode('5')).orElseThrow().getData());

                final ServiceResult result = checkIfReferenceExists(authRecordId, authAgencyId);
                if (result != null) {
                    return result;
                }

                appendLinkReference(recordIdObj, new RecordId(authRecordId, authAgencyId));
            }
        }

        return ServiceResult.newOkResult();
    }

}
