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

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

        for (MarcField field : marcRecord.getFields()) {
            final MarcFieldReader fieldReader = new MarcFieldReader(field);
            if (AUTHORITY_ALL_FIELDS.contains(field.getName()) && fieldReader.hasSubfield("5") && fieldReader.hasSubfield("6")) {
                final String authRecordId = fieldReader.getValue("6");
                final int authAgencyId = Integer.parseInt(fieldReader.getValue("5"));

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
