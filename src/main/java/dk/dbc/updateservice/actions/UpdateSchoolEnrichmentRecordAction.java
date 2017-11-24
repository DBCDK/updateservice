/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;

import java.util.Properties;

public class UpdateSchoolEnrichmentRecordAction extends UpdateEnrichmentRecordAction {
    private int commonRecordAgencyId;

    public UpdateSchoolEnrichmentRecordAction(GlobalActionState globalActionState, Properties properties, MarcRecord record) throws UpdateException {
        super(globalActionState, properties, record);
        setName("UpdateSchoolEnrichmentRecordAction");
        commonRecordAgencyId = RawRepo.COMMON_AGENCY;
        if (rawRepo.recordExists(new MarcRecordReader(record).getRecordId(), RawRepo.SCHOOL_COMMON_AGENCY)) {
            this.commonRecordAgencyId = RawRepo.SCHOOL_COMMON_AGENCY;
        }
    }

    @Override
    protected int getParentAgencyId() {
        return this.commonRecordAgencyId;
    }
}
