/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;

import com.fasterxml.jackson.databind.ObjectMapper;
import dk.dbc.common.records.MarcConverter;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcXchangeFactory;
import dk.dbc.common.records.marcxchange.ObjectFactory;
import dk.dbc.common.records.marcxchange.RecordType;
import dk.dbc.updateservice.dto.BibliographicRecordDTO;
import dk.dbc.updateservice.dto.BuildRequestDTO;
import dk.dbc.updateservice.dto.BuildResponseDTO;
import dk.dbc.updateservice.dto.BuildStatusEnumDTO;
import dk.dbc.updateservice.dto.RecordDataDTO;
import dk.dbc.updateservice.javascript.Scripter;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.json.MixIns;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.Stateless;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.dom.DOMSource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

@Stateless
public class OpenBuildCore {
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(OpenBuildCore.class);
    private static final Properties buildProperties = JNDIResources.getProperties();
    private static final ObjectMapper jacksonObjectMapper = new ObjectMapper();

    @EJB
    private Scripter scripter;

    @EJB
    private DocumentFactory documentFactory;

    @PostConstruct
    public void init() {
        addJacksonMixInAnnotations();
        validateProperties();
    }

    public BuildResponseDTO build(BuildRequestDTO parameters) {
        LOGGER.entry();
        StopWatch watch = new Log4JStopWatch("OpenBuildCore.build");
        BuildResponseDTO buildResponseDTO = null;
        try {
            if (!checkValidateSchema(parameters.getSchemaName())) {
                LOGGER.warn("Wrong validate schema: {}", parameters.getSchemaName());
                buildResponseDTO = new BuildResponseDTO();
                buildResponseDTO.setBuildStatusEnumDTO(BuildStatusEnumDTO.FAILED_INVALID_SCHEMA);
                return buildResponseDTO;
            }

            BibliographicRecordDTO srcRecord = parameters.getBibliographicRecordDTO();
            // Validate source record schema.
            if (srcRecord != null && !srcRecord.getRecordSchema().equals(JNDIResources.RECORD_SCHEMA_MARCXCHANGE_1_1)) {
                LOGGER.warn("Wrong record schema: {}", srcRecord.getRecordSchema());
                buildResponseDTO = new BuildResponseDTO();
                buildResponseDTO.setBuildStatusEnumDTO(BuildStatusEnumDTO.FAILED_INVALID_RECORD_SCHEMA);
                return buildResponseDTO;
            }

            // Validate source record packing.
            if (srcRecord != null && !srcRecord.getRecordPacking().equals(JNDIResources.RECORD_PACKING_XML)) {
                LOGGER.warn("Wrong record packing: {}", srcRecord.getRecordPacking());
                buildResponseDTO = new BuildResponseDTO();
                buildResponseDTO.setBuildStatusEnumDTO(BuildStatusEnumDTO.FAILED_INVALID_RECORD_PACKING);
                return buildResponseDTO;
            }

            MarcRecord record = null;
            if (srcRecord != null) {
                record = getMarcRecord(srcRecord.getRecordDataDTO());
                LOGGER.info("Building using record: {}", record);
            }

            MarcRecord marcRecord;
            StopWatch watchBuildRecord = new Log4JStopWatch("OpenBuildCore.buildRecord");
            if (record != null) {
                marcRecord = buildRecord(parameters.getSchemaName(), record);
            } else {
                marcRecord = buildRecord(parameters.getSchemaName(), null);
            }
            watchBuildRecord.stop();

            StopWatch watchBuildResult = new Log4JStopWatch("OpenBuildCore.buildResult");
            buildResponseDTO = buildResult(marcRecord);
            watchBuildResult.stop();
            return buildResponseDTO;
        } catch (Exception ex) {
            LOGGER.error("Caught exception", ex);
            buildResponseDTO = new BuildResponseDTO();
            buildResponseDTO.setBuildStatusEnumDTO(BuildStatusEnumDTO.FAILED_INTERNAL_ERROR);
            return buildResponseDTO;
        } finally {
            LOGGER.info("BuildResponseDTO: {}", buildResponseDTO);
            watch.stop();
            LOGGER.exit();
        }
    }

    public static String createTrackingId() {
        LOGGER.entry();
        String uuid = null;
        try {
            return uuid = UUID.randomUUID().toString();
        } finally {
            LOGGER.exit(uuid);
        }
    }

    private boolean checkValidateSchema(String name) throws ScripterException {
        LOGGER.entry(name);
        Boolean result = null;
        try {
            Object jsResult = scripter.callMethod("checkTemplateBuild", name, buildProperties);

            LOGGER.trace("Result from JS ({}): {}", jsResult.getClass().getName(), jsResult);

            if (jsResult instanceof Boolean) {
                result = (Boolean) jsResult;
                return result;
            }
            throw new ScripterException(String.format("The JavaScript function %s must return a boolean value.", "checkTemplate"));
        } finally {
            LOGGER.exit(result);
        }
    }

    private MarcRecord getMarcRecord(RecordDataDTO recordData) {
        LOGGER.entry(recordData);
        MarcRecord res = null;
        try {
            if (recordData != null) {
                List<Object> list = recordData.getContent();
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
        } finally {
            LOGGER.exit(res);
        }
    }

    private MarcRecord buildRecord(String buildSchema, MarcRecord record) {
        LOGGER.entry(buildSchema, record);
        MarcRecord result = null;
        try {
            Object jsResult;

            try {
                if (record != null) {
                    jsResult = scripter.callMethod("buildRecord", buildSchema, jacksonObjectMapper.writeValueAsString(record), buildProperties);
                } else {
                    jsResult = scripter.callMethod("buildRecord", buildSchema, null, buildProperties);
                }
            } catch (Exception ex) {
                LOGGER.error(ex.getLocalizedMessage());
                throw new EJBException("Error calling JavaScript environment", ex);
            }
            if (jsResult != null) {
                LOGGER.trace("Result from JS ({}): {}", jsResult.getClass().getName(), jsResult);
            }

            try {
                result = jacksonObjectMapper.readValue((String) jsResult, MarcRecord.class);
            } catch (IOException e) {
                throw new EJBException("Error while creating MarcRecord from JSON", e);
            }

            return result;
        } finally {
            LOGGER.exit(result);
        }
    }

    private BuildResponseDTO buildResult(MarcRecord marcRecord) throws JAXBException, ParserConfigurationException {
        LOGGER.entry(marcRecord);
        BuildResponseDTO buildResponseDTO = null;
        try {
            RecordDataDTO recordDataDTO = new RecordDataDTO();
            buildResponseDTO = new BuildResponseDTO();
            Document document = convertMarcRecordToDomDocument(marcRecord);
            recordDataDTO.setContent(Collections.singletonList(document.getDocumentElement()));
            BibliographicRecordDTO bibliographicRecordDTO = new BibliographicRecordDTO();
            bibliographicRecordDTO.setRecordDataDTO(recordDataDTO);
            bibliographicRecordDTO.setRecordPacking(JNDIResources.RECORD_PACKING_XML);
            bibliographicRecordDTO.setRecordSchema(JNDIResources.RECORD_SCHEMA_MARCXCHANGE_1_1);
            buildResponseDTO.setBibliographicRecordDTO(bibliographicRecordDTO);
            buildResponseDTO.setBuildStatusEnumDTO(BuildStatusEnumDTO.OK);
            return buildResponseDTO;
        } finally {
            LOGGER.exit(buildResponseDTO);
        }
    }

    private Document convertMarcRecordToDomDocument(MarcRecord marcRecord) throws JAXBException, ParserConfigurationException {
        LOGGER.entry(marcRecord);
        Document document = null;
        try {
            RecordType marcXhangeType = MarcXchangeFactory.createMarcXchangeFromMarc(marcRecord);
            ObjectFactory objectFactory = new ObjectFactory();
            JAXBElement<RecordType> jAXBElement = objectFactory.createRecord(marcXhangeType);

            JAXBContext jc = JAXBContext.newInstance(RecordType.class);
            Marshaller marshaller = jc.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_SCHEMA_LOCATION, JNDIResources.MARCXCHANGE_1_1_SCHEMA_LOCATION);

            document = documentFactory.getNewDocument();
            marshaller.marshal(jAXBElement, document);
            return document;
        } finally {
            LOGGER.exit(document);
        }
    }

    /**
     * PostConstruct method to initialize javascript environment, setup jackson mixins and initialize
     * forsrights web-service.
     */
    private void addJacksonMixInAnnotations() {
        LOGGER.entry();
        // Initialize jackson with annotation classes
        try {
            for (Map.Entry<Class<?>, Class<?>> e : MixIns.getMixIns().entrySet()) {
                jacksonObjectMapper.addMixIn(e.getKey(), e.getValue());
            }
        } finally {
            LOGGER.exit();
        }
    }

    private void validateProperties() {
        try {
            List<String> requiredProperties = new ArrayList<>();
            requiredProperties.add(JNDIResources.OPENNUMBERROLL_URL);
            requiredProperties.add(JNDIResources.OPENNUMBERROLL_NAME_FAUST_8);
            requiredProperties.add(JNDIResources.OPENNUMBERROLL_NAME_FAUST);
            requiredProperties.add(JNDIResources.JAVASCRIPT_BASEDIR);
            for (String s : requiredProperties) {
                if (!buildProperties.containsKey(s)) {
                    throw new IllegalArgumentException("Required Build property " + s + " not set");
                }
            }
        } finally {
            LOGGER.exit();
        }
    }

}
