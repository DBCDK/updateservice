package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.marc.binding.MarcRecord;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.utils.MDCUtil;


/**
 * Abstract class to contain common members for actions that works with records
 * in the rawrepo.
 */
public abstract class AbstractRawRepoAction extends AbstractAction {
    protected RawRepo rawRepo;
    protected MarcRecord marcRecord;
    protected RecordId recordId;

    protected AbstractRawRepoAction(String name, GlobalActionState globalActionState) {
        this(name, globalActionState, globalActionState.readRecord());
    }

    protected AbstractRawRepoAction(String name, GlobalActionState globalActionState, MarcRecord marcRecord) {
        super(name, globalActionState);
        this.rawRepo = globalActionState.getRawRepo();
        this.marcRecord = marcRecord;

        final MarcRecordReader reader = new MarcRecordReader(marcRecord);
        final String bibliographicRecordId = reader.getRecordId();
        int agencyId = reader.getAgencyIdAsInt();
        if (bibliographicRecordId != null) {
            this.recordId = new RecordId(bibliographicRecordId, agencyId);
        }
    }

    protected AbstractRawRepoAction(String name, GlobalActionState globalActionState, RecordId recordId) {
        super(name, globalActionState);
        this.rawRepo = globalActionState.getRawRepo();
        this.recordId = recordId;
    }

    public RawRepo getRawRepo() {
        return rawRepo;
    }

    public void setRawRepo(RawRepo rawRepo) {
        this.rawRepo = rawRepo;
    }

    public MarcRecord getRecord() {
        return marcRecord;
    }

    public void setRecord(MarcRecord marcRecord) {
        this.marcRecord = marcRecord;
    }

    @Override
    public void setupMDCContext() {
        if (marcRecord != null) {
            MDCUtil.setupContextForRecord(marcRecord);
        } else {
            MDCUtil.setupContextForRecord(recordId);
        }
    }
}
