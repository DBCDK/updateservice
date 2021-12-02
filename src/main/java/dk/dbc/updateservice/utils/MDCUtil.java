/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.utils;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.rawrepo.RecordId;
import org.slf4j.MDC;

public class MDCUtil {
    public static final String MDC_REQUEST_ID_LOG_CONTEXT = "requestId";
    public static final String MDC_PREFIX_ID_LOG_CONTEXT = "prefixId";
    public static final String MDC_REQUEST_PRIORITY = "priority";
    public static final String MDC_TRACKING_ID_LOG_CONTEXT = "trackingId";

    private MDCUtil() {
        
    }

    public static void setupContextForRecord(RecordId recordId) {
        setupContextForRecord(recordId.getBibliographicRecordId(), Integer.toString(recordId.getAgencyId()));
    }

    public static void setupContextForRecord(MarcRecord marcRecord) {
        MarcRecordReader reader = new MarcRecordReader(marcRecord);
        String recordId = reader.getValue("001", "a");
        String agencyId = reader.getValue("001", "b");

        setupContextForRecord(recordId, agencyId);
    }

    public static void setupContextForRecord(String recordId, String agencyId) {
        final String trackingIdPattern = "%s-{%s:%s}";
        String newTrackingId = String.format(trackingIdPattern, MDC.get(MDC_TRACKING_ID_LOG_CONTEXT), recordId, agencyId);
        MDC.put(MDC_TRACKING_ID_LOG_CONTEXT, newTrackingId);
    }

    public static void setupContextForEnrichmentRecord(MarcRecord commonRecord, String agencyId) {
        MarcRecordReader reader = new MarcRecordReader(commonRecord);
        String recordId = reader.getValue("001", "a");

        setupContextForRecord(recordId, agencyId);
    }
}
