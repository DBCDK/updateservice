package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.marc.binding.MarcRecord;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;

import java.util.Properties;

public class UpdateSchoolEnrichmentRecordAction extends UpdateEnrichmentRecordAction {
    private int commonRecordAgencyId;

    UpdateSchoolEnrichmentRecordAction(GlobalActionState globalActionState, Properties properties, MarcRecord marcRecord) throws UpdateException {
        super(globalActionState, properties, marcRecord);
        setName("UpdateSchoolEnrichmentRecordAction");
        commonRecordAgencyId = RawRepo.COMMON_AGENCY;
        if (rawRepo.recordExists(new MarcRecordReader(marcRecord).getRecordId(), RawRepo.SCHOOL_COMMON_AGENCY)) {
            this.commonRecordAgencyId = RawRepo.SCHOOL_COMMON_AGENCY;
        }
    }

    @Override
    protected int getParentAgencyId() {
        return this.commonRecordAgencyId;
    }
}
