package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;

/**
 * Created by stp on 14/12/15.
 */
public class UpdateSchoolEnrichmentRecordAction extends UpdateEnrichmentRecordAction {
    private Integer commonRecordAgencyId;

    public UpdateSchoolEnrichmentRecordAction(RawRepo rawRepo, MarcRecord record) throws UpdateException {
        super(rawRepo, record);
        setName("UpdateSchoolEnrichmentRecordAction");

        this.commonRecordAgencyId = RawRepo.RAWREPO_COMMON_LIBRARY;
        if (rawRepo.recordExists(new MarcRecordReader(record).recordId(), RawRepo.SCHOOL_COMMON_AGENCY)) {
            this.commonRecordAgencyId = RawRepo.SCHOOL_COMMON_AGENCY;
        }
    }

    @Override
    protected Integer commonRecordAgencyId() {
        return this.commonRecordAgencyId;
    }
}
