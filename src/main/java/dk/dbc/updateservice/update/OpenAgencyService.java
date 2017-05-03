/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;

import dk.dbc.iscrum.utils.json.Json;
import dk.dbc.openagency.client.LibraryRuleHandler;
import dk.dbc.openagency.client.OpenAgencyException;
import dk.dbc.openagency.client.OpenAgencyServiceFromURL;
import dk.dbc.updateservice.ws.JNDIResources;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.Singleton;
import java.io.IOException;
import java.util.Properties;
import java.util.Set;

/**
 * EJB to access the OpenAgency web service.
 */
@Singleton
public class OpenAgencyService {
    private static final XLogger logger = XLoggerFactory.getXLogger(OpenAgencyService.class);
    private static final int CONNECT_TIMEOUT = 1 * 60 * 1000;
    private static final int REQUEST_TIMEOUT = 3 * 60 * 1000;

    /**
     * Resource to lookup the product name for authentication.
     */
    @Resource(lookup = JNDIResources.JNDI_NAME_UPDATESERVICE)
    private Properties settings;

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

        // PH is also a FBS library
        public boolean isDBC() {
            return DBC.getValue().equals(this.getValue());
        }

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
            builder = builder.connectTimeout(CONNECT_TIMEOUT).requestTimeout(REQUEST_TIMEOUT);

            service = builder.build(settings.getProperty(JNDIResources.OPENAGENCY_URL_KEY));
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
                    logger.error("Request to OpenAgency:\n{}", Json.encodePretty(ex.getRequest()));
                }
                if (ex.getResponse() != null) {
                    logger.error("Response from OpenAgency:\n{}", Json.encodePretty(ex.getResponse()));
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

    public LibraryGroup getLibraryGroup(String agencyId) throws OpenAgencyException {
        logger.entry(agencyId);
        StopWatch watch = new Log4JStopWatch("service.openagency.getCatalogingTemplate");

        LibraryGroup result = null;
        try {
            String reply = service.libraryRules().getCatalogingTemplate(agencyId);

            if ("dbc".equals(reply) || "ffu".equals(reply)) {
                result = LibraryGroup.DBC;
            } else if ("ph".equals(reply)) {
                result = LibraryGroup.PH;
            } else {
                result = LibraryGroup.FBS;
            }

            logger.info("Agency '{}' has LibraryGroup {}", agencyId, result.toString());
            return result;
        } catch (OpenAgencyException ex) {
            logger.error("Failed to read CatalogingTemplate for ['{}']: {}", agencyId, ex.getMessage());
            try {
                if (ex.getRequest() != null) {
                    logger.error("Request to OpenAgency:\n{}", Json.encodePretty(ex.getRequest()));
                }
                if (ex.getResponse() != null) {
                    logger.error("Response from OpenAgency:\n{}", Json.encodePretty(ex.getResponse()));
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
                    logger.error("Request to OpenAgency:\n{}", Json.encodePretty(ex.getRequest()));
                }
                if (ex.getResponse() != null) {
                    logger.error("Response from OpenAgency:\n{}", Json.encodePretty(ex.getResponse()));
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

    public Set<String> getPHLibraries() throws OpenAgencyException {
        logger.entry();
        StopWatch watch = new Log4JStopWatch("service.openagency.getPHLibraries");

        Set<String> result = null;
        try {
            result = service.libraryRules().getLibrariesByCatalogingTemplateSet("ph");

            return result;
        } catch (OpenAgencyException ex) {
            logger.error("Failed to read PH Libraries: {}", ex.getMessage());
            try {
                if (ex.getRequest() != null) {
                    logger.error("Request to OpenAgency:\n{}", Json.encodePretty(ex.getRequest()));
                }
                if (ex.getResponse() != null) {
                    logger.error("Response from OpenAgency:\n{}", Json.encodePretty(ex.getResponse()));
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

    public Set<String> getFFULibraries() throws OpenAgencyException {
        logger.entry();
        StopWatch watch = new Log4JStopWatch("service.openagency.getFFULibraries");

        Set<String> result = null;
        try {
            result = service.libraryRules().getLibrariesByCatalogingTemplateSet("ffu");

            return result;
        } catch (OpenAgencyException ex) {
            logger.error("Failed to read FFU Libraries: {}", ex.getMessage());
            try {
                if (ex.getRequest() != null) {
                    logger.error("Request to OpenAgency:\n{}", Json.encodePretty(ex.getRequest()));
                }
                if (ex.getResponse() != null) {
                    logger.error("Response from OpenAgency:\n{}", Json.encodePretty(ex.getResponse()));
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
