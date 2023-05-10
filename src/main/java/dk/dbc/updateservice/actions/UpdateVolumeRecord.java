package dk.dbc.updateservice.actions;

import dk.dbc.marc.binding.MarcRecord;

import java.util.Properties;

/**
 * Action to update a volume record.
 */
public class UpdateVolumeRecord extends UpdateSingleRecord {
    public UpdateVolumeRecord(GlobalActionState globalActionState, Properties properties, MarcRecord marcRecord) {
        super(globalActionState, properties, marcRecord);
        setName("UpdateVolumeRecord");
    }

    @Override
    protected ServiceAction createCreateRecordAction() {
        return new CreateVolumeRecordAction(state, settings, marcRecord);
    }

    @Override
    protected ServiceAction createOverwriteRecordAction() {
        return new OverwriteVolumeRecordAction(state, settings, marcRecord);
    }
}
