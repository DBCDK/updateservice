/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.utils.MDCUtil;


/**
 * Abstract class to contain common members for actions that works with records
 * in the rawrepo.
 */
public abstract class AbstractRawRepoAction extends AbstractAction {
    protected RawRepo rawRepo;
    protected MarcRecord record;

    protected AbstractRawRepoAction(String name, GlobalActionState globalActionState) {
        this(name, globalActionState, globalActionState.readRecord());
    }

    protected AbstractRawRepoAction(String name, GlobalActionState globalActionState, MarcRecord record) {
        super(name, globalActionState);
        rawRepo = globalActionState.getRawRepo();
        this.record = record;
    }

    public RawRepo getRawRepo() {
        return rawRepo;
    }

    public void setRawRepo(RawRepo rawRepo) {
        this.rawRepo = rawRepo;
    }

    public MarcRecord getRecord() {
        return record;
    }

    public void setRecord(MarcRecord record) {
        this.record = record;
    }

    @Override
    public void setupMDCContext() {
        MDCUtil.setupContextForRecord(record);
    }
}
