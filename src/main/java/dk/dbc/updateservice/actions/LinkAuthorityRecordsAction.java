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

import java.util.Properties;

public class LinkAuthorityRecordsAction extends AbstractRawRepoAction {

    private static final XLogger logger = XLoggerFactory.getXLogger(UpdateCommonRecordAction.class);

    private Properties settings;

    public LinkAuthorityRecordsAction(GlobalActionState globalActionState, Properties properties, MarcRecord record) {
        super(LinkAuthorityRecordsAction.class.getSimpleName(), globalActionState, record);
        settings = properties;
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
                    Integer authAgencyId = RawRepo.AUTHORITY_AGENCY;
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
