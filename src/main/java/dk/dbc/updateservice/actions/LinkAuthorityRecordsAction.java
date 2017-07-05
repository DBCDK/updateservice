package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcField;
import dk.dbc.iscrum.records.MarcFieldReader;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

public class LinkAuthorityRecordsAction extends AbstractRawRepoAction {

    private static final XLogger logger = XLoggerFactory.getXLogger(LinkAuthorityRecordsAction.class);

    public LinkAuthorityRecordsAction(GlobalActionState globalActionState, MarcRecord record) {
        super(LinkAuthorityRecordsAction.class.getSimpleName(), globalActionState, record);
    }

    @Override
    public ServiceResult performAction() throws UpdateException {
        logger.entry();
        try {
            MarcRecordReader reader = new MarcRecordReader(record);
            RecordId recordIdObj = new RecordId(reader.recordId(), reader.agencyIdAsInteger());

            for (MarcField field : record.getFields()) {
                MarcFieldReader fieldReader = new MarcFieldReader(field);
                if (RawRepo.AUTHORITY_FIELDS.contains(field.getName()) && fieldReader.hasSubfield("5") && fieldReader.hasSubfield("6")) {
                    String authRecordId = fieldReader.getValue("6");
                    Integer authAgencyId = Integer.parseInt(fieldReader.getValue("5"));
                    RecordId authRecordIdObj = new RecordId(authRecordId, authAgencyId);
                    logger.info("Linking {} to {}", recordIdObj, authRecordIdObj);
                    state.getRawRepo().linkRecordAppend(recordIdObj, authRecordIdObj);
                }
            }

            return ServiceResult.newOkResult();
        } finally {
            logger.exit();
        }
    }

}
