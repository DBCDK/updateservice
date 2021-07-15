/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;

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
