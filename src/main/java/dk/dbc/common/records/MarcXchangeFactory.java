package dk.dbc.common.records;

import dk.dbc.common.records.marcxchange.DataFieldType;
import dk.dbc.common.records.marcxchange.LeaderFieldType;
import dk.dbc.common.records.marcxchange.RecordType;
import dk.dbc.common.records.marcxchange.SubfieldatafieldType;

/**
 * Factory class to construct MarcXchange objects.
 */
public class MarcXchangeFactory {

    private MarcXchangeFactory() {

    }

    public static RecordType createMarcXchangeFromMarc(MarcRecord marcRecord) {
        RecordType recordType = new RecordType();
        DataFieldType dataFieldType;
        if (marcRecord != null && marcRecord.getFields() != null) {
            LeaderFieldType leaderFieldType = new LeaderFieldType();
            leaderFieldType.setValue(MarcStatic.MARC_X_CHANGE_LEADER);
            recordType.setLeader(leaderFieldType);
            for (MarcField marcField : marcRecord.getFields()) {
                dataFieldType = createMarcXchangeFieldFromMarcField(marcField);
                recordType.getDatafield().add(dataFieldType);
            }
        }
        return recordType;
    }

    private static DataFieldType createMarcXchangeFieldFromMarcField(MarcField marcField) {
        DataFieldType dataFieldType = new DataFieldType();
        if (marcField != null) {
            fillIndicatorFieldsFromString(marcField.getIndicator(), dataFieldType);
            dataFieldType.setTag(marcField.getName());
            SubfieldatafieldType subfieldatafieldType;
            if (marcField.getSubfields() != null && !marcField.getSubfields().isEmpty()) {
                for (MarcSubField marcSubField : marcField.getSubfields()) {
                    subfieldatafieldType = createMarcXchangeSubfieldFromMarcSubfield(marcSubField);
                    dataFieldType.getSubfield().add(subfieldatafieldType);
                }
            }
        }
        return dataFieldType;
    }

    private static SubfieldatafieldType createMarcXchangeSubfieldFromMarcSubfield(MarcSubField marcSubField) {
        SubfieldatafieldType subfieldatafieldType = new SubfieldatafieldType();
        if (marcSubField != null) {
            subfieldatafieldType.setCode(marcSubField.getName());
            subfieldatafieldType.setValue(marcSubField.getValue());
        } else {
            subfieldatafieldType.setCode("");
        }
        return subfieldatafieldType;
    }

    private static void fillIndicatorFieldsFromString(String indicator, DataFieldType dataFieldType) {
        if (indicator != null && !indicator.isEmpty()) {
            String tmpIndicator = indicator;
            dataFieldType.setInd1(tmpIndicator.substring(0, 1));
            tmpIndicator = tmpIndicator.substring(1);
            if (!tmpIndicator.isEmpty()) {
                dataFieldType.setInd2(tmpIndicator.substring(0, 1));
                tmpIndicator = tmpIndicator.substring(1);
            }
            if (!tmpIndicator.isEmpty()) {
                dataFieldType.setInd3(tmpIndicator.substring(0, 1));
                tmpIndicator = tmpIndicator.substring(1);
            }
            if (!tmpIndicator.isEmpty()) {
                dataFieldType.setInd4(tmpIndicator.substring(0, 1));
                tmpIndicator = tmpIndicator.substring(1);
            }
            if (!tmpIndicator.isEmpty()) {
                dataFieldType.setInd5(tmpIndicator.substring(0, 1));
                tmpIndicator = tmpIndicator.substring(1);
            }
            if (!tmpIndicator.isEmpty()) {
                dataFieldType.setInd6(tmpIndicator.substring(0, 1));
                tmpIndicator = tmpIndicator.substring(1);
            }
            if (!tmpIndicator.isEmpty()) {
                dataFieldType.setInd7(tmpIndicator.substring(0, 1));
                tmpIndicator = tmpIndicator.substring(1);
            }
            if (!tmpIndicator.isEmpty()) {
                dataFieldType.setInd8(tmpIndicator.substring(0, 1));
                tmpIndicator = tmpIndicator.substring(1);
            }
            if (!tmpIndicator.isEmpty()) {
                dataFieldType.setInd9(tmpIndicator.substring(0, 1));
            }
        }
    }
}
