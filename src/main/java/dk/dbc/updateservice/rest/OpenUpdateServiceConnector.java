/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */


package dk.dbc.updateservice.rest;

import dk.dbc.invariant.InvariantUtil;
import dk.dbc.updateservice.service.api.Authentication;
import dk.dbc.updateservice.service.api.BibliographicRecord;
import dk.dbc.updateservice.service.api.CatalogingUpdatePortType;
import dk.dbc.updateservice.service.api.Options;
import dk.dbc.updateservice.service.api.UpdateOptionEnum;
import dk.dbc.updateservice.service.api.UpdateRecordRequest;
import dk.dbc.updateservice.service.api.UpdateRecordResult;
import dk.dbc.updateservice.service.api.UpdateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.ws.BindingProvider;

/**
 * Update web service connector.
 * Instances of this class are NOT thread safe.
 */
public class OpenUpdateServiceConnector {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenUpdateServiceConnector.class);

    private static final String CONNECT_TIMEOUT_PROPERTY = "com.sun.xml.ws.connect.timeout";
    private static final String REQUEST_TIMEOUT_PROPERTY = "com.sun.xml.ws.request.timeout";
    private static final int CONNECT_TIMEOUT_DEFAULT_IN_MS = 60 * 1000;       // 1 minute
    private static final int REQUEST_TIMEOUT_DEFAULT_IN_MS = 60 * 60 * 1000;  // 60 minutes -- we wait and wait on open update.

    private final String endpoint;
    private final String userName;
    private final String password;

    /* web-service proxy */
    private final CatalogingUpdatePortType proxy;

    private final boolean validateOnly;

    public OpenUpdateServiceConnector() {
        this.endpoint = "http://localhost:8080/UpdateService/2.0";
        this.userName = "";
        this.password = "";
        this.proxy = this.getProxy(new UpdateService());
        this.validateOnly = true;
    }

    /**
     * Calls updateRecord operation of the Open Update Web service
     *
     * @param groupId             group id used for authorization
     * @param schemaName          the template towards which the validation should be performed
     * @param bibliographicRecord containing the MarcXChange to validate
     * @param trackingId          unique ID for each OpenUpdate request
     * @return UpdateRecordResult instance
     * @throws NullPointerException     if passed any null valued {@code template} or {@code bibliographicRecord} argument
     * @throws IllegalArgumentException if passed empty valued {@code template}
     */
    public UpdateRecordResult updateRecord(String groupId, String schemaName, BibliographicRecord bibliographicRecord, String trackingId)
            throws NullPointerException, IllegalArgumentException {
        InvariantUtil.checkNotNullNotEmptyOrThrow(groupId, "groupId");
        InvariantUtil.checkNotNullNotEmptyOrThrow(schemaName, "schemaName");
        InvariantUtil.checkNotNullOrThrow(bibliographicRecord, "bibliographicRecord");
        LOGGER.trace("Using endpoint: {}", endpoint);
        final UpdateRecordRequest updateRecordRequest = buildUpdateRecordRequest(groupId, schemaName, bibliographicRecord, trackingId);
        return proxy.updateRecord(updateRecordRequest);
    }

    /**
     * Builds an UpdateRecordRequest
     *
     * @param groupId             group id used for authorization
     * @param schemaName          the template towards which the validation should be performed
     * @param bibliographicRecord containing the MarcXChange to validate
     * @param trackingId          unique ID for each OpenUpdate request
     * @return a new updateRecordRequest containing schemeName and bibliographicRecord
     */
    private UpdateRecordRequest buildUpdateRecordRequest(String groupId, String schemaName, BibliographicRecord bibliographicRecord, String trackingId) {
        final UpdateRecordRequest updateRecordRequest = new UpdateRecordRequest();
        final Authentication authentication = new Authentication();
        authentication.setGroupIdAut(groupId);
        authentication.setUserIdAut(userName);
        authentication.setPasswordAut(password);
        updateRecordRequest.setAuthentication(authentication);
        updateRecordRequest.setSchemaName(schemaName);
        updateRecordRequest.setBibliographicRecord(bibliographicRecord);
        updateRecordRequest.setTrackingId(trackingId);
        if (validateOnly) {
            if (updateRecordRequest.getOptions() == null) {
                final Options options = new Options();
                options.getOption().add(UpdateOptionEnum.VALIDATE_ONLY);
                updateRecordRequest.setOptions(options);
            } else {
                updateRecordRequest.getOptions().getOption().add(
                        UpdateOptionEnum.VALIDATE_ONLY);
            }
        }
        return updateRecordRequest;
    }

    private CatalogingUpdatePortType getProxy(UpdateService service) {
        final CatalogingUpdatePortType proxy = service.getCatalogingUpdatePort();

        BindingProvider bindingProvider = (BindingProvider) proxy;
        bindingProvider.getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, endpoint);
        bindingProvider.getRequestContext().put(CONNECT_TIMEOUT_PROPERTY, CONNECT_TIMEOUT_DEFAULT_IN_MS);
        bindingProvider.getRequestContext().put(REQUEST_TIMEOUT_PROPERTY, REQUEST_TIMEOUT_DEFAULT_IN_MS);

        return proxy;
    }
}
