/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.ws;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import org.slf4j.MDC;

public class MDCUtil {
    public static void setupContextForRecord(MarcRecord record) {
        MarcRecordReader reader = new MarcRecordReader(record);
        String recordId = reader.getValue("001", "a");
        String agencyId = reader.getValue("001", "b");

        setupContextForRecord(recordId, agencyId);
    }

    public static void setupContextForRecord(String recordId, String agencyId) {
        final String trackingIdPattern = "%s-{%s:%s}";
        String newTrackingId = String.format(trackingIdPattern, MDC.get(UpdateService.MDC_TRACKING_ID_LOG_CONTEXT), recordId, agencyId);
        MDC.put(UpdateService.MDC_TRACKING_ID_LOG_CONTEXT, newTrackingId);
    }

    public static void setupContextForEnrichmentRecord(MarcRecord commonRecord, String agencyId) {
        MarcRecordReader reader = new MarcRecordReader(commonRecord);
        String recordId = reader.getValue("001", "a");

        setupContextForRecord(recordId, agencyId);
    }
}
