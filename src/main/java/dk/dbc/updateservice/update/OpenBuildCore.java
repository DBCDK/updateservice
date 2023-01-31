/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;

import dk.dbc.common.records.MarcConverter;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcXchangeFactory;
import dk.dbc.common.records.marcxchange.ObjectFactory;
import dk.dbc.common.records.marcxchange.RecordType;
import dk.dbc.jsonb.JSONBException;
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
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.dom.DOMSource;
import java.io.UnsupportedEncodingException;
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


    @EJB
    private DocumentFactory documentFactory;

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
                if (o instanceof Node) {
                    res = MarcConverter.createFromMarcXChange(new DOMSource((Node) o));
                    break;
                } else if (o instanceof String && o.toString().startsWith("<")) {
                    res = MarcConverter.convertFromMarcXChange((String) o);
                    break;
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
        } catch (JSONBException | OpencatBusinessConnectorException | JAXBException | UnsupportedEncodingException ex) {
            throw new EJBException("Error calling OpencatBusinessConnector", ex);
        } finally {
            watch.stop();
        }
    }

    private BuildResponseDTO buildResult(MarcRecord marcRecord) throws JAXBException, ParserConfigurationException {
        final RecordDataDTO recordDataDTO = new RecordDataDTO();
        final BuildResponseDTO buildResponseDTO = new BuildResponseDTO();
        final Document document = convertMarcRecordToDomDocument(marcRecord);
        recordDataDTO.setContent(Collections.singletonList(document.getDocumentElement()));
        final BibliographicRecordDTO bibliographicRecordDTO = new BibliographicRecordDTO();
        bibliographicRecordDTO.setRecordDataDTO(recordDataDTO);
        bibliographicRecordDTO.setRecordPacking(JNDIResources.RECORD_PACKING_XML);
        bibliographicRecordDTO.setRecordSchema(JNDIResources.RECORD_SCHEMA_MARCXCHANGE_1_1);
        buildResponseDTO.setBibliographicRecordDTO(bibliographicRecordDTO);
        buildResponseDTO.setBuildStatusEnumDTO(BuildStatusEnumDTO.OK);

        return buildResponseDTO;
    }

    private Document convertMarcRecordToDomDocument(MarcRecord marcRecord) throws JAXBException, ParserConfigurationException {
        final RecordType marcXhangeType = MarcXchangeFactory.createMarcXchangeFromMarc(marcRecord);
        final ObjectFactory objectFactory = new ObjectFactory();
        final JAXBElement<RecordType> jAXBElement = objectFactory.createRecord(marcXhangeType);

        final JAXBContext jc = JAXBContext.newInstance(RecordType.class);
        final Marshaller marshaller = jc.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_SCHEMA_LOCATION, JNDIResources.MARCXCHANGE_1_1_SCHEMA_LOCATION);

        final Document document = documentFactory.getNewDocument();
        marshaller.marshal(jAXBElement, document);
        return document;
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
