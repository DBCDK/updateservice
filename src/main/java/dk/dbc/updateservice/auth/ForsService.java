/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.auth;

import dk.dbc.forsrights.client.ForsRights;
import dk.dbc.forsrights.client.ForsRightsException;
import dk.dbc.forsrights.client.ForsRightsServiceFromURL;
import dk.dbc.updateservice.actions.GlobalActionState;
import dk.dbc.updateservice.ws.JNDIResources;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.annotation.PostConstruct;
import javax.ejb.ConcurrencyManagement;
import javax.ejb.ConcurrencyManagementType;
import javax.ejb.Singleton;
import java.util.Properties;

/**
 * This class encapsulate calls to the Forsrights service using SOAP.
 *
 * @author stp
 */
@Singleton
@ConcurrencyManagement(ConcurrencyManagementType.BEAN)
public class ForsService {
    private static final XLogger logger = XLoggerFactory.getXLogger(ForsService.class);
    private static final long CACHE_ENTRY_TIMEOUT = 10 * 60 * 1000;
    private static final int CONNECT_TIMEOUT = 1 * 60 * 1000;
    private static final int REQUEST_TIMEOUT = 3 * 60 * 1000;

    private Properties settings = JNDIResources.getProperties();

    private ForsRights.RightsCache forsRightsCache;
    private ForsRights forsRights;

    @PostConstruct
    public void init() {
        StopWatch watch = new Log4JStopWatch("service.forsrights.init");
        try {
            forsRightsCache = new ForsRights.RightsCache(CACHE_ENTRY_TIMEOUT);
            ForsRightsServiceFromURL.Builder builder = ForsRightsServiceFromURL.builder();
            builder = builder.connectTimeout(CONNECT_TIMEOUT).requestTimeout(REQUEST_TIMEOUT);
            forsRights = builder.build(settings.getProperty(JNDIResources.FORSRIGHTS_URL)).forsRights(forsRightsCache);
        } finally {
            watch.stop();
        }
    }

    /**
     * Calls the forsrights service
     *
     * @param globalActionState global state object
     * @return A response from forsrights.
     */
    public ForsRights.RightSet forsRights(GlobalActionState globalActionState) throws ForsRightsException {
        logger.entry(globalActionState.getUpdateServiceRequestDTO().getAuthenticationDTO().getUserId(), globalActionState.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), "****");
        StopWatch watch = new Log4JStopWatch("service.forsrights.rights");
        try {
            logger.info("Authenticating user {}/{} against forsright at {}", globalActionState.getUpdateServiceRequestDTO().getAuthenticationDTO().getUserId(), globalActionState.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), settings.getProperty(JNDIResources.FORSRIGHTS_URL));
            return forsRights.lookupRight(globalActionState.getUpdateServiceRequestDTO().getAuthenticationDTO().getUserId(), globalActionState.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), globalActionState.getUpdateServiceRequestDTO().getAuthenticationDTO().getPassword(), null);
        } finally {
            watch.stop();
            logger.exit();
        }
    }

    /**
     * Calls the forsrights service with an IP address.
     *
     * @param globalActionState global state object
     * @param ipAddress IP-address from the caller of this web service.
     * @return A response from forsrights.
     */
    public ForsRights.RightSet forsRightsWithIp(GlobalActionState globalActionState, String ipAddress) throws ForsRightsException {
        logger.entry(globalActionState.getUpdateServiceRequestDTO().getAuthenticationDTO().getUserId(), globalActionState.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), "****", ipAddress);
        StopWatch watch = new Log4JStopWatch("service.forsrights.rightsWithIp");
        try {
            logger.info("Authenticating user {}/{} with ip-address {} against forsright at {}", globalActionState.getUpdateServiceRequestDTO().getAuthenticationDTO().getUserId(), globalActionState.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), ipAddress, settings.getProperty(JNDIResources.FORSRIGHTS_URL));
            return forsRights.lookupRight(globalActionState.getUpdateServiceRequestDTO().getAuthenticationDTO().getUserId(), globalActionState.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), globalActionState.getUpdateServiceRequestDTO().getAuthenticationDTO().getPassword(), ipAddress);
        } finally {
            watch.stop();
            logger.exit();
        }
    }
}
