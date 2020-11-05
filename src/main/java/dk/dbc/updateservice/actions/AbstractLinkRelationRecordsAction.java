/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.rawrepo.RawRepoException;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.naming.NamingException;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.util.List;
import java.lang.Object;

import static dk.dbc.updateservice.javascript.UpdaterRawRepo.getRelationsChildren;

public abstract class AbstractLinkRelationRecordsAction extends AbstractRawRepoAction {
    private static final XLogger logger = XLoggerFactory.getXLogger(AbstractLinkRelationRecordsAction.class);

    public AbstractLinkRelationRecordsAction(String name, GlobalActionState globalActionState, MarcRecord record) {
        super(name, globalActionState, record);
    }

    protected ServiceResult checkIfReferenceExists(String bibliographicRecordId, int agencyId) throws UpdateException {
        if (!state.getRawRepo().recordExists(bibliographicRecordId, agencyId)) {
            final String message = String.format(state.getMessages().getString("ref.record.doesnt.exist"), bibliographicRecordId, agencyId);
            return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
        } else {
            return null;
        }
    }

    protected void appendLinkReference(RecordId source, RecordId target) throws UpdateException {
        logger.info("Set relation from [{}:{}] -> [{}:{}]",
                source.getBibliographicRecordId(),
                source.getAgencyId(),
                target.getBibliographicRecordId(),
                target.getAgencyId());
        state.getRawRepo().linkRecordAppend(source, target);
    }

    /**
     * We have an update request for a matvurd record that point at same records as another matvurd record does.
     * If current record has a LED catalogue code or if a record pointed to has a such, then check is relaxed - may point to
     * four records, and if four then one of them must be a 700*f skole.
     * If there isn't a LED code then only two records is allowed and precisely one of them must contain 700*f skole
     * Please note that the ids list may be long and that the list of matvurd records containing these numbers may be large.
     */
    protected ServiceResult checkR01R02Content(List<String> ids, String thisId, int hasSchool, boolean hasLED) throws UpdateException {
        try {
            for (String id : ids) {
                // for each id we get relations and look at content in those
                int count = 1;
                List<MarcRecord> records = getRelationsChildren(id, rawRepo.COMMON_AGENCY);
                for (MarcRecord record : records) {
                    MarcRecordReader reader = new MarcRecordReader(record);
                    if (rawRepo.MATVURD_AGENCY == reader.getAgencyIdAsInt() && !thisId.equals(reader.getRecordId())) {
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
                    // Up to 3 records are allowed (excluding the current) but only one school
                    if (count > 3) {
                        final String message = String.format(state.getMessages().getString("more.than.three.matvurd.hits"), id);
                        return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
                    }
                    if (count == 3 && hasSchool != 1) {
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
        } catch (SQLException | UnsupportedEncodingException | RawRepoException | NamingException ex) {
            logger.error(ex.getMessage(), ex);
            throw new UpdateException(ex.getMessage(), ex);
        }

        return null;
    }
}
