/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import java.util.ArrayList;
import dk.dbc.common.records.MarcField;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcSubField;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.util.List;

public class MatVurdR01R02CheckRecordsAction extends AbstractLinkRelationRecordsAction {

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
                        if ("x".equals(subField.getName())) {
                            if (subField.getValue().startsWith("LED")) {
                                hasLED = true;
                            }
                        }
                    }
                }
                else if ("700".contains(field.getName())) {
                    for (MarcSubField subField : field.getSubfields()) {
                        if ("f".equals(subField.getName())) {
                            if ("skole".equals(subField.getValue())) {
                                hasSchool = 1;
                            }
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

}
