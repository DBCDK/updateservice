package dk.dbc.updateservice.json;

import dk.dbc.marc.binding.DataField;
import dk.dbc.marc.binding.MarcRecord;
import dk.dbc.marc.binding.SubField;

import java.util.HashMap;
import java.util.Map;

/**
 * Provides access to all JSON mixins
 */
public class MixIns {
    private static final Map<Class<?>, Class<?>> MIX_INS = new HashMap<>();

    static {
        MIX_INS.put(MarcRecord.class, MarcRecordMixIn.class);
        MIX_INS.put(DataField.class, DataFieldMixIn.class);
        MIX_INS.put(SubField.class, MarcSubFieldMixIn.class);
    }

    private MixIns() {
    }

    public static Map<Class<?>, Class<?>> getMixIns() {
        return MIX_INS;
    }
}
