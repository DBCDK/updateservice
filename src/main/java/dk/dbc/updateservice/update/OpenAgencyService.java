/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;

import dk.dbc.updateservice.json.JsonMapper;
import dk.dbc.openagency.client.LibraryRuleHandler;
import dk.dbc.openagency.client.OpenAgencyException;
import dk.dbc.openagency.client.OpenAgencyServiceFromURL;
import dk.dbc.updateservice.ws.JNDIResources;
import java.time.Duration;
import javax.inject.Inject;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.annotation.RegistryType;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.annotation.PostConstruct;
import javax.ejb.Singleton;
import java.io.IOException;
import java.util.Properties;
import java.util.Set;

/**
 * EJB to access the OpenAgency web service.
 */
@Singleton
public class OpenAgencyService {
    @Inject
    @RegistryType(type = MetricRegistry.Type.APPLICATION)
    MetricRegistry metricRegistry;

    static final Metadata openAgencyTimerMetadata = Metadata.builder()
            .withName("update_openagencyservice_timer")
            .withDescription("Duration of various openagency calls")
            .withType(MetricType.SIMPLE_TIMER)
            .withUnit(MetricUnits.MILLISECONDS).build();

    static final Metadata openAgencyErrorCounterMetadata = Metadata.builder()
            .withName("update_openagencyservice_error_counter")
            .withDescription("Number of errors caught in openagency calls")
            .withType(MetricType.COUNTER)
            .withUnit("requests").build();

    private static final String METHOD_NAME_KEY = "method";

    private static final XLogger logger = XLoggerFactory.getXLogger(OpenAgencyService.class);
    private static final int CONNECT_TIMEOUT = 1 * 60 * 1000;
    private static final int REQUEST_TIMEOUT = 3 * 60 * 1000;

    private Properties settings = JNDIResources.getProperties();

    private OpenAgencyServiceFromURL service;

    public enum LibraryGroup {
        DBC("dbc"), FBS("fbs"), PH("ph");

        private final String value;

        LibraryGroup(final String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return this.getValue();
        }

        public boolean isDBC() {
            return DBC.getValue().equals(this.getValue());
        }

        // PH is also a FBS library
        public boolean isFBS() {
            return FBS.getValue().equals(this.getValue()) || PH.getValue().equals(this.getValue());
        }

        public boolean isPH() {
            return PH.getValue().equals(this.getValue());
        }
    }

    @PostConstruct
    public void init() {
        logger.entry();
        StopWatch watch = new Log4JStopWatch("service.openagency.init");

        try {
            OpenAgencyServiceFromURL.Builder builder = OpenAgencyServiceFromURL.builder();
            builder = builder.connectTimeout(CONNECT_TIMEOUT).
                    requestTimeout(REQUEST_TIMEOUT).
                    setCacheAge(Integer.parseInt(settings.getProperty(JNDIResources.OPENAGENCY_CACHE_AGE)));

            service = builder.build(settings.getProperty(JNDIResources.OPENAGENCY_URL));
        } finally {
            watch.stop();
            logger.exit();
        }
    }

    public OpenAgencyServiceFromURL getService() {
        return service;
    }

    public boolean hasFeature(String agencyId, LibraryRuleHandler.Rule feature) throws OpenAgencyException {
        logger.entry(agencyId, feature);
        StopWatch watch = new Log4JStopWatch("service.openagency.hasFeature");

        Boolean result = null;
        try {
            result = service.libraryRules().isAllowed(agencyId, feature);

            logger.info("Agency '{}' is allowed to use feature '{}': {}", agencyId, feature, result);
            return result;
        } catch (OpenAgencyException ex) {
            logger.error("Failed to read feature from OpenAgency for ['{}':'{}']: {}", agencyId, feature, ex.getMessage());
            try {
                if (ex.getRequest() != null) {
                    logger.error("Request to OpenAgency:\n{}", JsonMapper.encodePretty(ex.getRequest()));
                }
                if (ex.getResponse() != null) {
                    logger.error("Response from OpenAgency:\n{}", JsonMapper.encodePretty(ex.getResponse()));
                }
            } catch (IOException ioError) {
                logger.error("Error with encoding request/response from OpenAgency: " + ioError.getMessage(), ioError);
            }
            throw ex;

        } catch (Throwable e) {
            metricRegistry.counter(openAgencyErrorCounterMetadata,
                    new Tag(METHOD_NAME_KEY, "hasfeature")).inc();
            throw e;
        } finally {
            watch.stop();
            logger.exit(result);
            metricRegistry.simpleTimer(openAgencyTimerMetadata,
                    new Tag(METHOD_NAME_KEY, "hasfeature")).update(Duration.ofMillis(watch.getElapsedTime()));
        }
    }

    public LibraryGroup getLibraryGroup(String agencyId) throws OpenAgencyException, UpdateException {
        logger.entry(agencyId);
        StopWatch watch = new Log4JStopWatch("service.openagency.getCatalogingTemplate");

        LibraryGroup result = null;
        try {
            String reply = service.libraryRules().getCatalogingTemplate(agencyId);

            if (reply == null || reply.isEmpty()) {
                throw new UpdateException("Couldn't find cataloging template group for agency " + agencyId);
            }

            switch (reply) {
                case "dbc":
                case "ffu":
                case "lokbib":
                    result = LibraryGroup.DBC;
                    break;
                case "ph":
                    result = LibraryGroup.PH;
                    break;
                case "fbs":
                case "fbslokal":
                case "skole":
                    result = LibraryGroup.FBS;
                    break;
                default:
                    throw new UpdateException("Unknown library group: " + reply);
            }

            logger.info("Agency '{}' has LibraryGroup {}", agencyId, result.toString());
            metricRegistry.simpleTimer(openAgencyTimerMetadata,
                    new Tag(METHOD_NAME_KEY, "getlibrarygroup")).update(Duration.ofMillis(watch.getElapsedTime()));
            return result;
        } catch (OpenAgencyException ex) {
            logger.error("Failed to read CatalogingTemplate for ['{}']: {}", agencyId, ex.getMessage());
            try {
                if (ex.getRequest() != null) {
                    logger.error("Request to OpenAgency:\n{}", JsonMapper.encodePretty(ex.getRequest()));
                }
                if (ex.getResponse() != null) {
                    logger.error("Response from OpenAgency:\n{}", JsonMapper.encodePretty(ex.getResponse()));
                }
            } catch (IOException ioError) {
                logger.error("Error with encoding request/response from OpenAgency: " + ioError.getMessage(), ioError);
            }

            throw ex;
        } catch (Throwable e) {
            metricRegistry.counter(openAgencyErrorCounterMetadata,
                    new Tag(METHOD_NAME_KEY, "getlibrarygroup")).inc();
            throw e;
        } finally {
            watch.stop();
            logger.exit(result);
        }
    }

    public String getTemplateGroup(String agencyId) throws OpenAgencyException {
        logger.entry(agencyId);
        StopWatch watch = new Log4JStopWatch("service.openagency.getCatalogingTemplate");

        String result = null;
        try {
            result = service.libraryRules().getCatalogingTemplate(agencyId);

            logger.info("Agency '{}' has CatalogingTemplate {}", agencyId, result);
            return result;
        } catch (OpenAgencyException ex) {
            logger.error("Failed to read CatalogingTemplate for ['{}']: {}", agencyId, ex.getMessage());
            try {
                if (ex.getRequest() != null) {
                    logger.error("Request to OpenAgency:\n{}", JsonMapper.encodePretty(ex.getRequest()));
                }
                if (ex.getResponse() != null) {
                    logger.error("Response from OpenAgency:\n{}", JsonMapper.encodePretty(ex.getResponse()));
                }
            } catch (IOException ioError) {
                logger.error("Error with encoding request/response from OpenAgency: " + ioError.getMessage(), ioError);
            }

            throw ex;
        } finally {
            watch.stop();
            logger.exit(result);
        }
    }

    public Set<String> getLokbibLibraries() throws OpenAgencyException {
        logger.entry();
        Set<String> result = null;
        try {
            result = getLibrariesByCatalogingTemplateSet("lokbib");
            return result;
        } finally {
            logger.exit(result);
        }
    }

    public Set<String> getPHLibraries() throws OpenAgencyException {
        logger.entry();
        Set<String> result = null;
        try {
            result = getLibrariesByCatalogingTemplateSet("ph");
            return result;
        } finally {
            logger.exit(result);
        }
    }

    public Set<String> getFFULibraries() throws OpenAgencyException {
        logger.entry();
        Set<String> result = null;
        try {
            result = getLibrariesByCatalogingTemplateSet("ffu");
            return result;
        } finally {
            logger.exit(result);
        }
    }

    public Set<String> getAllowedLibraryRules(String agencyId) throws OpenAgencyException {
        logger.entry(agencyId);

        StopWatch watch = new Log4JStopWatch("service.openagency.getAllowedLibraryRules");

        Set<String> result = null;
        try {
            result = service.libraryRules().getAllowedLibraryRules(agencyId);

            return result;
        } catch (OpenAgencyException ex) {
            logger.error("Failed to read set from OpenAgency: {}", ex.getMessage());
            try {
                if (ex.getRequest() != null) {
                    logger.error("Request to OpenAgency:\n{}", JsonMapper.encodePretty(ex.getRequest()));
                }
                if (ex.getResponse() != null) {
                    logger.error("Response from OpenAgency:\n{}", JsonMapper.encodePretty(ex.getResponse()));
                }
            } catch (IOException ioError) {
                logger.error("Error with encoding request/response from OpenAgency: " + ioError.getMessage(), ioError);
            }

            throw ex;
        } finally {
            watch.stop();
            logger.exit(result);
        }
    }
    private Set<String> getLibrariesByCatalogingTemplateSet(String catalogingTemplateSet) throws OpenAgencyException {
        logger.entry(catalogingTemplateSet);

        StopWatch watch = new Log4JStopWatch("service.openagency.getLibrariesByCatalogingTemplateSet");

        Set<String> result = null;
        try {
            result = service.libraryRules().getLibrariesByCatalogingTemplateSet(catalogingTemplateSet);

            return result;
        } catch (OpenAgencyException ex) {
            logger.error("Failed to read catalogingTemplateSet: {}", ex.getMessage());
            try {
                if (ex.getRequest() != null) {
                    logger.error("Request to OpenAgency:\n{}", JsonMapper.encodePretty(ex.getRequest()));
                }
                if (ex.getResponse() != null) {
                    logger.error("Response from OpenAgency:\n{}", JsonMapper.encodePretty(ex.getResponse()));
                }
            } catch (IOException ioError) {
                logger.error("Error with encoding request/response from OpenAgency: " + ioError.getMessage(), ioError);
            }

            throw ex;
        } finally {
            watch.stop();
            logger.exit(result);
        }
    }

}
