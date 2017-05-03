/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;

import java.util.Properties;

/**
 * Created by stp on 14/12/15.
 */
public class UpdateSchoolEnrichmentRecordAction extends UpdateEnrichmentRecordAction {
    private Integer commonRecordAgencyId;

    public UpdateSchoolEnrichmentRecordAction(GlobalActionState globalActionState, Properties properties, MarcRecord record) throws UpdateException {
        super(globalActionState, properties, record);
        setName("UpdateSchoolEnrichmentRecordAction");
        commonRecordAgencyId = RawRepo.RAWREPO_COMMON_LIBRARY;
        if (rawRepo.recordExists(new MarcRecordReader(record).recordId(), RawRepo.SCHOOL_COMMON_AGENCY)) {
            this.commonRecordAgencyId = RawRepo.SCHOOL_COMMON_AGENCY;
        }
    }

    @Override
    protected Integer commonRecordAgencyId() {
        return this.commonRecordAgencyId;
    }
}
