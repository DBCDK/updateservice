/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import dk.dbc.common.records.MarcField;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.MarcSubField;
import dk.dbc.common.records.utils.RecordContentTransformer;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.util.List;
import java.util.Set;

public class MatVurdR01R02CheckRecordsAction extends AbstractRawRepoAction {

    private static final XLogger logger = XLoggerFactory.getXLogger(MatVurdR01R02CheckRecordsAction.class);

    public MatVurdR01R02CheckRecordsAction(GlobalActionState globalActionState, MarcRecord record) {
        super(MatVurdR01R02CheckRecordsAction.class.getSimpleName(), globalActionState, record);
    }

    @Override
    public ServiceResult performAction() throws UpdateException {
        logger.entry();
        ServiceResult result = null;
        try {
            List<String> matvurdRecords = new ArrayList<>();
            int hasSchool = 0;
            boolean hasLED = false;
            String thisId = "";
            for (MarcField field : record.getFields()) {
                if (RawRepo.MATVURD_FIELDS.contains(field.getName())) {
                    for (MarcSubField subField : field.getSubfields()) {
                        if ("a".equals(subField.getName())) {
                            matvurdRecords.add(subField.getValue());
                        }
                    }
                }
                else if ("001".contains(field.getName())) {
                    for (MarcSubField subField : field.getSubfields()) {
                        if ("a".equals(subField.getName())) {
                            thisId = subField.getValue();
                        }
                    }
                }
                else if ("032".contains(field.getName())) {
                    for (MarcSubField subField : field.getSubfields()) {
                        if ("x".equals(subField.getName()) && subField.getValue().startsWith("LED")) {
                            hasLED = true;
                            break;
                        }
                    }
                }
                else if ("700".contains(field.getName())) {
                    for (MarcSubField subField : field.getSubfields()) {
                        if ("f".equals(subField.getName()) && "skole".equals(subField.getValue())) {
                            hasSchool = 1;
                            break;
                        }
                    }
                }
            }
            if (!matvurdRecords.isEmpty()) {
                result = checkR01R02Content(matvurdRecords, thisId, hasSchool, hasLED);
                if (result != null) {
                    return result;
                }
            }

            return result = ServiceResult.newOkResult();
        } finally {
            logger.exit(result);
        }
    }

    /**
     * We have an update request for a matvurd record that point at same records as another matvurd record does.
     * If current record has a LED catalogue code or if a record pointed to has a such, then check is relaxed - may point to
     * four records, and if four then one of them must be a 700*f skole.
     * If there isn't a LED code then only two records is allowed and precisely one of them must contain 700*f skole
     * Please note that the ids list may be long and that the list of matvurd records containing these numbers may be large.
     */
    private ServiceResult checkR01R02Content(List<String> ids, String thisId, int hasSchool, boolean hasLED) throws UpdateException {
        try {
            for (String id : ids) {
                // for each id we get relations and look at content in those
                int count = 1;
                final RecordId recordId = new RecordId(id, RawRepo.COMMON_AGENCY);
                final Set<RecordId> childrenIds = state.getRawRepo().children(recordId);
                for (RecordId recordId1 : childrenIds) {
                    final MarcRecord curRecord = RecordContentTransformer.decodeRecord(rawRepo.fetchRecord(recordId1.getBibliographicRecordId(), recordId1.getAgencyId()).getContent());
                    MarcRecordReader reader = new MarcRecordReader(curRecord);
                    if (RawRepo.MATVURD_AGENCY == reader.getAgencyIdAsInt() && !thisId.equals(reader.getRecordId())) {
                        for (String content : reader.getValues("032", "x")) {
                            if (content.startsWith("LED")) {
                                hasLED = true;
                                break;
                            }
                        }
                        for (String content : reader.getValues("700", "f")) {
                            if (content.equals("skole")) {
                                hasSchool++;
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
                if (hasLED) {
                    // Up to 4 records are allowed (excluding the current) but only one school
                    if (count > 4) {
                        final String message = String.format(state.getMessages().getString("more.than.four.matvurd.hits"), id);
                        return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
                    }
                    if (count == 4 && hasSchool != 1) {
                        final String message = String.format(state.getMessages().getString("wrong.count.of.school.record"), id);
                        return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
                    }
                    if (hasSchool > 1) {
                        final String message = String.format(state.getMessages().getString("wrong.count.of.school.record"), id);
                        return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
                    }
                } else {
                    if (count > 2) {
                        final String message = String.format(state.getMessages().getString("more.than.two.matvurd.hits"), id);
                        return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
                    }
                    if (count == 2 && hasSchool != 1) {
                        final String message = String.format(state.getMessages().getString("wrong.count.of.school.record"), id);
                        return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
                    }
                }

            }
        } catch (UnsupportedEncodingException ex) {
            logger.error(ex.getMessage(), ex);
            throw new UpdateException(ex.getMessage(), ex);
        }

        return null;
    }
}
