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
    public UpdateVolumeRecord(GlobalActionState globalActionState, Properties properties, MarcRecord record) {
        super(globalActionState, properties, record);
        setName("UpdateVolumeRecord");
    }

    protected ServiceAction createCreateRecordAction() {
        return new CreateVolumeRecordAction(state, settings, record);
    }

    protected ServiceAction createOverwriteRecordAction() {
        return new OverwriteVolumeRecordAction(state, settings, record);
    }
}
