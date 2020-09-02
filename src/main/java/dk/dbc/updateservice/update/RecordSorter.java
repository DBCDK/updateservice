package dk.dbc.updateservice.update;


import dk.dbc.common.records.MarcRecord;
import dk.dbc.jsonb.JSONBException;
import dk.dbc.opencat.connector.OpencatBusinessConnector;
import dk.dbc.opencat.connector.OpencatBusinessConnectorException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.xml.bind.JAXBException;
import java.io.UnsupportedEncodingException;
import java.util.Properties;

public class RecordSorter {
    private static final XLogger logger = XLoggerFactory.getXLogger(RecordSorter.class);

    private OpencatBusinessConnector opencatBusinessConnector;
    private String schemaName;

    // Default constructor used for mocking
    protected RecordSorter() {
    }

    public RecordSorter(OpencatBusinessConnector opencatBusinessConnector, String schemaName) {
        this.opencatBusinessConnector = opencatBusinessConnector;
        this.schemaName = schemaName;
    }

    public MarcRecord sortRecord(MarcRecord record, Properties properties) throws UpdateException {
        logger.entry();
        try {
            return opencatBusinessConnector.sortRecord(schemaName, record);
        } catch (OpencatBusinessConnectorException | JSONBException | JAXBException | UnsupportedEncodingException ex) {
            logger.error("Error when trying to sort the record. ", ex);
            return record;
        } finally {
            logger.exit();
        }
    }
}
