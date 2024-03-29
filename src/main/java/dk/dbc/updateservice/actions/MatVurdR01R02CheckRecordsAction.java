package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.marc.binding.DataField;
import dk.dbc.marc.binding.MarcRecord;
import dk.dbc.marc.binding.SubField;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.update.UpdateRecordContentTransformer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MatVurdR01R02CheckRecordsAction extends AbstractRawRepoAction {

    public MatVurdR01R02CheckRecordsAction(GlobalActionState globalActionState, MarcRecord marcRecord) {
        super(MatVurdR01R02CheckRecordsAction.class.getSimpleName(), globalActionState, marcRecord);
    }

    @Override
    public ServiceResult performAction() throws UpdateException {
        final List<String> matvurdRecords = new ArrayList<>();
        int hasSchool = 0;
        boolean hasLED = false;
        String thisId = "";
        for (DataField field : marcRecord.getFields(DataField.class)) {
            if (RawRepo.MATVURD_FIELDS.contains(field.getTag())) {
                for (SubField subField : field.getSubFields()) {
                    if ('a' == subField.getCode()) {
                        matvurdRecords.add(subField.getData());
                    }
                }
            } else if ("001".contains(field.getTag())) {
                for (SubField subField : field.getSubFields()) {
                    if ('a' == subField.getCode()) {
                        thisId = subField.getData();
                    }
                }
            } else if ("004".contains(field.getTag())) {
                for (SubField subField : field.getSubFields()) {
                    if ('r' == subField.getCode() && "d".equals(subField.getData())) {
                        return ServiceResult.newOkResult();
                    }
                }
            } else if ("032".contains(field.getTag())) {
                for (SubField subField : field.getSubFields()) {
                    if ('x' == subField.getCode() && subField.getData().startsWith("LED")) {
                        hasLED = true;
                        break;
                    }
                }
            } else if ("700".contains(field.getTag())) {
                for (SubField subField : field.getSubFields()) {
                    if ('f' == subField.getCode() && "skole".equals(subField.getData())) {
                        hasSchool = 1;
                        break;
                    }
                }
            }
        }
        if (!matvurdRecords.isEmpty()) {
            final ServiceResult result = checkR01R02Content(matvurdRecords, thisId, hasSchool, hasLED);
            if (result != null) {
                return result;
            }
        }

        return ServiceResult.newOkResult();
    }

    /**
     * We have an update request for a matvurd record that point at same records as another matvurd record does.
     * If current record has a LED catalogue code or if a record pointed to has a such, then check is relaxed - may point to
     * four records, and if four then one of them must be a 700*f skole.
     * If there isn't a LED code then only two records is allowed and precisely one of them must contain 700*f skole
     * Please note that the ids list may be long and that the list of matvurd records containing these numbers may be large.
     */
    private ServiceResult checkR01R02Content(List<String> ids, String thisId, int hasSchool, boolean hasLED) throws UpdateException {
        for (String id : ids) {
            // for each id we get relations and look at content in those
            int count = 1;
            boolean idHasLED = hasLED;
            int idHasSchool = hasSchool;
            final RecordId recordId = new RecordId(id, RawRepo.COMMON_AGENCY);
            final Set<RecordId> childrenIds = state.getRawRepo().children(recordId);
            for (RecordId recordId1 : childrenIds) {
                final MarcRecord curRecord = UpdateRecordContentTransformer.decodeRecord(rawRepo.fetchRecord(recordId1.getBibliographicRecordId(), recordId1.getAgencyId()).getContent());
                final MarcRecordReader reader = new MarcRecordReader(curRecord);
                if (RawRepo.MATVURD_AGENCY == reader.getAgencyIdAsInt() && !thisId.equals(reader.getRecordId())) {
                    for (String content : reader.getValues("032", 'x')) {
                        if (content.startsWith("LED")) {
                            idHasLED = true;
                            break;
                        }
                    }
                    for (String content : reader.getValues("700", 'f')) {
                        if (content.equals("skole")) {
                            idHasSchool++;
                            break;
                        }
                    }
                    count++;
                }
            }
            /*
            Two situations - with or without LED code
            With LED :
                Up to four records and if four then there must be one but only one skole record
                If less than four max one may be a skole record
            Without LED :
                Max two records and only one with skole
             */
            if (idHasLED) {
                // Up to 4 records are allowed (excluding the current) but only one school
                if (count > 4) {
                    final String message = String.format(state.getMessages().getString("more.than.four.matvurd.hits"), id);
                    return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
                }
            } else {
                if (count > 2) {
                    final String message = String.format(state.getMessages().getString("more.than.two.matvurd.hits"), id);
                    return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
                }
                if (count == 2 && idHasSchool == 0) {
                    final String message = String.format(state.getMessages().getString("zero.count.of.school.record"), id);
                    return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
                }
                if (count == 2 && idHasSchool == 2) {
                    final String message = String.format(state.getMessages().getString("two.count.of.school.record"), id);
                    return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
                }
            }

        }
        return null;
    }
}
