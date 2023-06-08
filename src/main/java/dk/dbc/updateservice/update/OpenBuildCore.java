package dk.dbc.updateservice.update;

import dk.dbc.jsonb.JSONBException;
import dk.dbc.marc.binding.MarcRecord;
import dk.dbc.marc.reader.MarcReaderException;
import dk.dbc.opencat.connector.OpencatBusinessConnector;
import dk.dbc.opencat.connector.OpencatBusinessConnectorException;
import dk.dbc.updateservice.dto.BibliographicRecordDTO;
import dk.dbc.updateservice.dto.BuildRequestDTO;
import dk.dbc.updateservice.dto.BuildResponseDTO;
import dk.dbc.updateservice.dto.BuildStatusEnumDTO;
import dk.dbc.updateservice.dto.RecordDataDTO;
import dk.dbc.updateservice.utils.DeferredLogger;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
import org.slf4j.MDC;

import javax.annotation.PostConstruct;
import javax.ejb.EJBException;
import javax.ejb.Stateless;
import javax.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static dk.dbc.updateservice.rest.ApplicationConfig.LOG_DURATION_THRESHOLD_MS;
import static dk.dbc.updateservice.utils.MDCUtil.MDC_TRACKING_ID_LOG_CONTEXT;

@Stateless
public class OpenBuildCore {
    private static final DeferredLogger LOGGER = new DeferredLogger(OpenBuildCore.class);
    private static final Properties buildProperties = JNDIResources.getProperties();

    @Inject
    private OpencatBusinessConnector opencatBusinessConnector;

    @PostConstruct
    public void init() {
        validateProperties();
    }

    public BuildResponseDTO build(BuildRequestDTO parameters) {
        return LOGGER.call(log -> {
            final StopWatch watch = new Log4JStopWatch("OpenBuildCore.build").setTimeThreshold(LOG_DURATION_THRESHOLD_MS);
            BuildResponseDTO buildResponseDTO = null;
            try {
                if (!checkValidateSchema(parameters.getSchemaName())) {
                    log.warn("Wrong validate schema: {}", parameters.getSchemaName());
                    buildResponseDTO = new BuildResponseDTO();
                    buildResponseDTO.setBuildStatusEnumDTO(BuildStatusEnumDTO.FAILED_INVALID_SCHEMA);
                    return buildResponseDTO;
                }

                final BibliographicRecordDTO srcRecord = parameters.getBibliographicRecordDTO();
                // Validate source record schema.
                if (srcRecord != null && !srcRecord.getRecordSchema().equals(JNDIResources.RECORD_SCHEMA_MARCXCHANGE_1_1)) {
                    log.warn("Wrong record schema: {}", srcRecord.getRecordSchema());
                    buildResponseDTO = new BuildResponseDTO();
                    buildResponseDTO.setBuildStatusEnumDTO(BuildStatusEnumDTO.FAILED_INVALID_RECORD_SCHEMA);
                    return buildResponseDTO;
                }

                // Validate source record packing.
                if (srcRecord != null && !srcRecord.getRecordPacking().equals(JNDIResources.RECORD_PACKING_XML)) {
                    log.warn("Wrong record packing: {}", srcRecord.getRecordPacking());
                    buildResponseDTO = new BuildResponseDTO();
                    buildResponseDTO.setBuildStatusEnumDTO(BuildStatusEnumDTO.FAILED_INVALID_RECORD_PACKING);
                    return buildResponseDTO;
                }

                MarcRecord record = null;
                if (srcRecord != null) {
                    record = getMarcRecord(srcRecord.getRecordDataDTO());
                    log.info("Building using record: {}", record);
                }

                MarcRecord marcRecord;
                final StopWatch watchBuildRecord = new Log4JStopWatch("OpenBuildCore.buildRecord").setTimeThreshold(LOG_DURATION_THRESHOLD_MS);
                if (record != null) {
                    marcRecord = buildRecord(parameters.getSchemaName(), record);
                } else {
                    marcRecord = buildRecord(parameters.getSchemaName(), null);
                }
                watchBuildRecord.stop();

                final StopWatch watchBuildResult = new Log4JStopWatch("OpenBuildCore.buildResult").setTimeThreshold(LOG_DURATION_THRESHOLD_MS);
                buildResponseDTO = buildResult(marcRecord);
                watchBuildResult.stop();
                return buildResponseDTO;
            } catch (Exception ex) {
                log.error("Caught exception", ex);
                buildResponseDTO = new BuildResponseDTO();
                buildResponseDTO.setBuildStatusEnumDTO(BuildStatusEnumDTO.FAILED_INTERNAL_ERROR);
                return buildResponseDTO;
            } finally {
                log.info("BuildResponseDTO: {}", buildResponseDTO);
                watch.stop();
            }
        });
    }

    public static String createTrackingId() {
        return UUID.randomUUID().toString();
    }

    private boolean checkValidateSchema(String name) throws JSONBException, OpencatBusinessConnectorException {
        final StopWatch watch = new Log4JStopWatch("opencatBusiness.checkTemplateBuild").setTimeThreshold(LOG_DURATION_THRESHOLD_MS);
        try {
            final String trackingId = MDC.get(MDC_TRACKING_ID_LOG_CONTEXT);

            return opencatBusinessConnector.checkTemplateBuild(name, trackingId);
        } finally {
            watch.stop();
        }
    }

    private MarcRecord getMarcRecord(RecordDataDTO recordData) {
        MarcRecord res = null;
        if (recordData != null) {
            final List<Object> list = recordData.getContent();
            for (Object o : list) {
                String marcString = (String) o;
                if (!"".equals(marcString.trim())) {
                    try {
                        res = UpdateRecordContentTransformer.decodeRecord(marcString.getBytes(StandardCharsets.UTF_8));
                        break;
                    } catch (UpdateException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        if (res != null && "".equals(res.toString().trim())) {
            res = null;
        }
        return res;
    }

    private MarcRecord buildRecord(String buildSchema, MarcRecord marcRecord) {
        final StopWatch watch = new Log4JStopWatch("opencatBusiness.buildRecord").setTimeThreshold(LOG_DURATION_THRESHOLD_MS);
        try {
            final String trackingId = MDC.get(MDC_TRACKING_ID_LOG_CONTEXT);

            return opencatBusinessConnector.buildRecord(buildSchema, marcRecord, trackingId);
        } catch (JSONBException | OpencatBusinessConnectorException | MarcReaderException ex) {
            throw new EJBException("Error calling OpencatBusinessConnector", ex);
        } finally {
            watch.stop();
        }
    }

    private BuildResponseDTO buildResult(MarcRecord marcRecord) {
        final RecordDataDTO recordDataDTO = new RecordDataDTO();
        final BuildResponseDTO buildResponseDTO = new BuildResponseDTO();
        final byte[] content = UpdateRecordContentTransformer.encodeRecord(marcRecord);
        recordDataDTO.setContent(Collections.singletonList(new String(content)));
        final BibliographicRecordDTO bibliographicRecordDTO = new BibliographicRecordDTO();
        bibliographicRecordDTO.setRecordDataDTO(recordDataDTO);
        bibliographicRecordDTO.setRecordPacking(JNDIResources.RECORD_PACKING_XML);
        bibliographicRecordDTO.setRecordSchema(JNDIResources.RECORD_SCHEMA_MARCXCHANGE_1_1);
        buildResponseDTO.setBibliographicRecordDTO(bibliographicRecordDTO);
        buildResponseDTO.setBuildStatusEnumDTO(BuildStatusEnumDTO.OK);

        return buildResponseDTO;
    }


    private void validateProperties() {
        final List<String> requiredProperties = new ArrayList<>();
        requiredProperties.add(JNDIResources.OPENNUMBERROLL_URL);
        requiredProperties.add(JNDIResources.OPENNUMBERROLL_NAME_FAUST_8);
        requiredProperties.add(JNDIResources.OPENNUMBERROLL_NAME_FAUST);
        for (String s : requiredProperties) {
            if (!buildProperties.containsKey(s)) {
                throw new IllegalArgumentException("Required Build property " + s + " not set");
            }
        }
    }

}
