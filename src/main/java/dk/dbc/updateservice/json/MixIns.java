/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.json;

import dk.dbc.common.records.MarcField;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcSubField;

import java.util.HashMap;
import java.util.Map;

/**
 * Provides access to all JSON mixins
 */
public class MixIns {
    private static final Map<Class<?>, Class<?>> MIX_INS = new HashMap<>();

    static {
        MIX_INS.put(MarcRecord.class, MarcRecordMixIn.class);
        MIX_INS.put(MarcField.class, MarcFieldMixIn.class);
        MIX_INS.put(MarcSubField.class, MarcSubFieldMixIn.class);
    }

    private MixIns() {
    }

    public static Map<Class<?>, Class<?>> getMixIns() {
        return MIX_INS;
    }
}
