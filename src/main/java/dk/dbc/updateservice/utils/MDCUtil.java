package dk.dbc.updateservice.utils;

import dk.dbc.marc.binding.MarcRecord;
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
        String recordId = marcRecord.getSubFieldValue("001", 'a').orElse(null);
        String agencyId = marcRecord.getSubFieldValue("001", 'b').orElse(null);

        setupContextForRecord(recordId, agencyId);
    }

    public static void setupContextForRecord(String recordId, String agencyId) {
        String trackingId = MDC.get(MDC_TRACKING_ID_LOG_CONTEXT);
        if(!trackingId.contains(recordId)) {
            final String trackingIdPattern = "%s-{%s:%s}";
            String newTrackingId = String.format(trackingIdPattern, trackingId, recordId, agencyId);
            MDC.put(MDC_TRACKING_ID_LOG_CONTEXT, newTrackingId);
        }
    }

    public static void setupContextForEnrichmentRecord(MarcRecord commonRecord, String agencyId) {
        String recordId = commonRecord.getSubFieldValue("001", 'a').orElse(null);

        setupContextForRecord(recordId, agencyId);
    }
}
