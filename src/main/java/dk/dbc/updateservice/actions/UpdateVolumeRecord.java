package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.ws.JNDIResources;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

/**
 * Action to update a volume record.
 */
public class UpdateVolumeRecord extends UpdateSingleRecord {
    private static final XLogger logger = XLoggerFactory.getXLogger(UpdateVolumeRecord.class);

    public UpdateVolumeRecord(RawRepo rawRepo, MarcRecord record) {
        super(rawRepo, record);
        setName("UpdateVolumeRecord");
    }

    @Override
    protected ServiceAction createCreateRecordAction() {
        logger.entry();

        try {
            CreateVolumeRecordAction action = new CreateVolumeRecordAction(rawRepo, record);
            action.setHoldingsItems(getHoldingsItems());
            action.setSolrService(getSolrService());
            action.setProviderId(settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID));

            return action;
        } finally {
            logger.exit();
        }
    }

    @Override
    protected ServiceAction createOverwriteRecordAction() {
        logger.entry();

        try {
            OverwriteVolumeRecordAction action = new OverwriteVolumeRecordAction(rawRepo, record);
            action.setGroupId(getGroupId());
            action.setHoldingsItems(getHoldingsItems());
            action.setOpenAgencyService(getOpenAgencyService());
            action.setRecordsHandler(getRecordsHandler());
            action.setSolrService(getSolrService());
            action.setSettings(settings);

            return action;
        } finally {
            logger.exit();
        }
    }
}
