/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import dk.dbc.common.records.MarcConverter;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcXchangeFactory;
import dk.dbc.common.records.marcxchange.ObjectFactory;
import dk.dbc.common.records.marcxchange.RecordType;
import dk.dbc.log.DBCTrackedLogContext;
import dk.dbc.oss.ns.catalogingbuild.BibliographicRecord;
import dk.dbc.oss.ns.catalogingbuild.Build;
import dk.dbc.oss.ns.catalogingbuild.BuildPortType;
import dk.dbc.oss.ns.catalogingbuild.BuildRequest;
import dk.dbc.oss.ns.catalogingbuild.BuildResponse;
import dk.dbc.oss.ns.catalogingbuild.BuildResult;
import dk.dbc.oss.ns.catalogingbuild.BuildStatusEnum;
import dk.dbc.oss.ns.catalogingbuild.RecordData;
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
import javax.jws.WebService;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.dom.DOMSource;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/**
 * Web service entry point for Open Build SOAP service.
 * <p/>
 * This class implements the SOAP operations for our web service.
 */
@WebService(
        serviceName = "CatalogingBuildServices",
        portName = "BuildPort",
        endpointInterface = "dk.dbc.oss.ns.catalogingbuild.BuildPortType",
        targetNamespace = "http://oss.dbc.dk/ns/catalogingBuild",
        wsdlLocation = "WEB-INF/classes/META-INF/wsdl/build/catalogingBuild.wsdl")
@Stateless
public class OpenBuild implements BuildPortType {
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(OpenBuild.class);

    @SuppressWarnings("EjbEnvironmentInspection")
    @EJB
    private Scripter scripter;

    @SuppressWarnings("EjbEnvironmentInspection")
    @EJB
    private DocumentFactory documentFactory;

    private Properties buildProperties = JNDIResources.getProperties();
    private ObjectMapper jacksonObjectMapper = new ObjectMapper();

    /**
     * PostConstruct method to initialize javascript environment, setup jackson mixins and initialize
     * forsrights web-service.
     */
    @PostConstruct
    public void init() {
        addJacksonMixInAnnotations();
        validateProperties(buildProperties);
    }

    /**
     * Build service web-service entrypoint.
     *
     * @param parameters The parameters to use for build.
     * @return BuildResult containing the new or converted template
     */
    @Override
    public BuildResult build(BuildRequest parameters) {
        new DBCTrackedLogContext(createTrackingId());
        LOGGER.entry();
        StopWatch watch = new Log4JStopWatch("OpenBuild.build");
        LOGGER.info("Build request: " + buildRequestToString(parameters));
        BuildResult result = null;
        try {
            if (!checkValidateSchema(parameters.getSchemaName())) {
                LOGGER.warn("Wrong validate schema: {}", parameters.getSchemaName());
                BuildResult buildResult = new BuildResult();
                buildResult.setBuildStatus(BuildStatusEnum.FAILED_INVALID_SCHEMA);
                return buildResult;
            }

            BibliographicRecord srcRecord = parameters.getBibliographicRecord();
            // Validate source record schema.
            if (srcRecord != null && !srcRecord.getRecordSchema().equals(JNDIResources.RECORD_SCHEMA_MARCXCHANGE_1_1)) {
                LOGGER.warn("Wrong record schema: {}", srcRecord.getRecordSchema());
                BuildResult buildResult = new BuildResult();
                buildResult.setBuildStatus(BuildStatusEnum.FAILED_INVALID_RECORD_SCHEMA);
                return buildResult;
            }

            // Validate source record packing.
            if (srcRecord != null && !srcRecord.getRecordPacking().equals(JNDIResources.RECORD_PACKING_XML)) {
                LOGGER.warn("Wrong record packing: {}", srcRecord.getRecordPacking());
                BuildResult buildResult = new BuildResult();
                buildResult.setBuildStatus(BuildStatusEnum.FAILED_INVALID_RECORD_PACKING);
                return buildResult;
            }

            MarcRecord record = null;
            if (srcRecord != null) {
                record = getMarcRecord(srcRecord.getRecordData());
            }
            MarcRecord marcRecord;
            StopWatch watchBuildRecord = new Log4JStopWatch("OpenBuild.buildRecord");
            if (record != null) {
                marcRecord = buildRecord(parameters.getSchemaName(), record);
            } else {
                marcRecord = buildRecord(parameters.getSchemaName(), null);
            }
            watchBuildRecord.stop();

            StopWatch watchBuildResult = new Log4JStopWatch("OpenBuild.buildResult");
            result = buildResult(marcRecord);
            watchBuildResult.stop();
            return result;
        } catch (Exception ex) {
            LOGGER.error("Caught exception", ex);
            BuildResult buildResult = new BuildResult();
            buildResult.setBuildStatus(BuildStatusEnum.FAILED_INTERNAL_ERROR);
            return buildResult;
        } finally {
            String resultOutput = buildResultToString(result);
            LOGGER.info("Build response: " + resultOutput);
            watch.stop();
            LOGGER.exit();
            DBCTrackedLogContext.remove();
        }
    }

    private MarcRecord getMarcRecord(RecordData recordData) {
        LOGGER.entry(recordData);
        MarcRecord res = null;
        try {
            if (recordData != null) {
                List<Object> list = recordData.getContent();
                for (Object o : list) {
                    if (o instanceof Node) {
                        res = MarcConverter.createFromMarcXChange(new DOMSource((Node) o));
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

    private String createTrackingId() {
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

    private BuildResult buildResult(MarcRecord marcRecord) throws JAXBException, ParserConfigurationException {
        LOGGER.entry(marcRecord);
        BuildResult buildResult = null;
        try {
            RecordData recordData = new RecordData();
            Document document = convertMarcRecordToDomDocument(marcRecord);
            recordData.getContent().add(document.getDocumentElement());
            BibliographicRecord bibliographicRecord = new BibliographicRecord();
            bibliographicRecord.setRecordData(recordData);
            bibliographicRecord.setRecordPacking(JNDIResources.RECORD_PACKING_XML);
            bibliographicRecord.setRecordSchema(JNDIResources.RECORD_SCHEMA_MARCXCHANGE_1_1);
            buildResult = new BuildResult();
            buildResult.setBibliographicRecord(bibliographicRecord);
            buildResult.setBuildStatus(BuildStatusEnum.OK);
            return buildResult;
        } finally {
            LOGGER.exit(buildResult);
        }
    }

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

    private void validateProperties(Properties properties) {
        LOGGER.entry(properties);
        try {
            List<String> requiredProperties = new ArrayList<>();
            requiredProperties.add(JNDIResources.OPENNUMBERROLL_URL);
            requiredProperties.add(JNDIResources.OPENNUMBERROLL_NAME_FAUST_8);
            requiredProperties.add(JNDIResources.OPENNUMBERROLL_NAME_FAUST);
            requiredProperties.add(JNDIResources.JAVASCRIPT_BASEDIR);
            for (String s : requiredProperties) {
                if (!properties.containsKey(s)) {
                    throw new IllegalArgumentException("Required Build property " + s + " not set");
                }
            }
        } finally {
            LOGGER.exit();
        }
    }

    private String buildRequestToString(BuildRequest br) {
        String res;
        try {
            Build build = new Build();
            build.setBuildRequest(br);
            res = marshal(build, Build.class);
        } catch (JAXBException e) {
            LOGGER.catching(e);
            res = "<could not read input>";
        }
        return res;
    }

    private String buildResultToString(BuildResult br) {
        String res;
        try {
            BuildResponse buildResponse = new BuildResponse();
            buildResponse.setBuildResult(br);
            res = marshal(buildResponse, BuildResponse.class);
        } catch (JAXBException e) {
            LOGGER.catching(e);
            res = "<could not read output>";
        }
        return res;
    }

    private <T> String marshal(T data, Class<T> clazz) throws JAXBException {
        StringWriter stringWriter = new StringWriter();
        JAXBContext jaxbContext = JAXBContext.newInstance(clazz);
        Marshaller marshaller = jaxbContext.createMarshaller();
        marshaller.marshal(data, stringWriter);
        return stringWriter.toString();
    }

}
