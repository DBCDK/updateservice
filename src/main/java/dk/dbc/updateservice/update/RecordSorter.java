package dk.dbc.updateservice.update;


import dk.dbc.common.records.MarcRecord;
import dk.dbc.updateservice.javascript.Scripter;
import dk.dbc.updateservice.javascript.ScripterException;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.IOException;
import java.util.Properties;

public class RecordSorter {
    private static final XLogger logger = XLoggerFactory.getXLogger(RecordSorter.class);
    private static final String ENTRY_POINT = "sortRecord";

    private Scripter scripter;
    private String schemaName;

    public RecordSorter(Scripter scripter, String schemaName) {
        this.scripter = scripter;
        this.schemaName = schemaName;
    }

    public MarcRecord sortRecord(MarcRecord record, Properties properties) throws UpdateException {
        logger.entry();
        MarcRecord result = record;
        ObjectMapper mapper = new ObjectMapper();
        String jsonRecord;
        try {
            jsonRecord = mapper.writeValueAsString(record);
            Object jsResult = scripter.callMethod(ENTRY_POINT, schemaName, jsonRecord, properties);
            if (jsResult instanceof String) {
                result = mapper.readValue(jsResult.toString(), MarcRecord.class);
            }
            return result;
        } catch (IOException | ScripterException ex) {
            logger.error("Error when trying to sort the record. ", ex);
            return record;
        } finally {
            logger.exit();
        }
    }
}
