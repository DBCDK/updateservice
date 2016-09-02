package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.iscrum.utils.ResourceBundles;
import dk.dbc.iscrum.utils.logback.filters.BusinessLoggerFilter;
import dk.dbc.updateservice.auth.Authenticator;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.service.api.Authentication;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.ws.MDCUtil;
import dk.dbc.updateservice.ws.ValidationError;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.ejb.EJBException;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Action to authenticate a record.
 * <p/>
 * This action needs the following to be able to authenticate a record:
 * <ol>
 * <li>The record to authenticate, <code>record</code></li>
 * <li>
 * The name of the template that contains the validation rules to check against the record,
 * <code>schemaName</code>
 * </li>
 * <li>
 * An Authenticator that do the actual authentication, <code>authenticator</code>.
 * </li>
 * <li>
 * Login information to be parsed to <code>authenticator</code>.
 * </li>
 * </ol>
 */
public class AuthenticateRecordAction extends AbstractAction {
    private static final XLogger logger = XLoggerFactory.getXLogger(AuthenticateRecordAction.class);
    private static final XLogger bizLogger = XLoggerFactory.getXLogger(BusinessLoggerFilter.LOGGER_NAME);

    private MarcRecord record;
    private Authenticator authenticator;
    private Authentication authentication;
    private ResourceBundle messages;

    /**
     * Constructs an instance with a template name and a record.
     * <p/>
     * The JavaScript logic needs an JavaScript environment and a set of
     * settings to work properly. These can be set though the properties <code>scripter</code>
     * and <code>settings</code>. This constructor initialize these to null.
     *
     * @param record The record to validate.
     */
    public AuthenticateRecordAction(MarcRecord record) {
        super("AuthenticateRecordAction");

        this.record = record;
        this.authenticator = null;
        this.authentication = null;
        this.messages = ResourceBundles.getBundle(this, "actions");
    }

    public MarcRecord getRecord() {
        return record;
    }

    public void setRecord(MarcRecord record) {
        this.record = record;
    }

    public Authenticator getAuthenticator() {
        return authenticator;
    }

    public void setAuthenticator(Authenticator authenticator) {
        this.authenticator = authenticator;
    }

    public Authentication getAuthentication() {
        return authentication;
    }

    public void setAuthentication(Authentication authentication) {
        this.authentication = authentication;
    }

    /**
     * Validates the record against the JavaScript logic.
     * <p/>
     * If the JavaScript logic returns any validation errors they are converted to
     * validation entries in the ServiceResult with the status
     * <code>UpdateStatusEnum.VALIDATION_ERROR</code>. If no errors are returned
     * we use the status from <code>okStatus</code>.
     * <p/>
     * Exceptions from the JavaScript logic is converted to a ServiceResult with the
     * status <code>UpdateStatusEnum.FAILED_VALIDATION_INTERNAL_ERROR</code>. The actual
     * exception message returned as a validation entry in the ServiceResult.
     *
     * @return The constructed ServiceResult.
     * @throws UpdateException Never thrown.
     */
    @Override
    public ServiceResult performAction() throws UpdateException {
        logger.entry();

        ServiceResult result = null;
        try {
            bizLogger.info("Login user: {}/{}", this.authentication.getUserIdAut(), this.authentication.getGroupIdAut());
            bizLogger.info("Handling record:\n{}", record);

            List<ValidationError> errors = this.authenticator.authenticateRecord(this.record, this.authentication.getUserIdAut(), this.authentication.getGroupIdAut());
            result = new ServiceResult();
            result.addEntries(errors);

            MarcRecordReader reader = new MarcRecordReader(record);
            String recordId = reader.recordId();
            String agencyId = reader.agencyId();
            if (result.hasErrors()) {
                bizLogger.warn("Authenticating of record {{}:{}} with user {}/{} failed", recordId, agencyId, this.authentication.getGroupIdAut(), this.authentication.getUserIdAut());
                result.setStatus(UpdateStatusEnum.FAILED_INVALID_AGENCY);
            } else {
                bizLogger.info("Authenticating record {{}:{}} with user {}/{} successfully", recordId, agencyId, this.authentication.getGroupIdAut(), this.authentication.getUserIdAut());
                result.setStatus(UpdateStatusEnum.OK);
            }

            return result;
        } catch (EJBException | ScripterException ex) {
            Throwable businessException = findServiceException(ex);
            String message = String.format(messages.getString("internal.authenticate.record.error"), businessException.getMessage());
            bizLogger.error(message);
            logger.warn("Exception doing authentication: ", businessException);
            return result = ServiceResult.newErrorResult(UpdateStatusEnum.FAILED_VALIDATION_INTERNAL_ERROR, message);
        } finally {
            logger.exit(result);
        }
    }

    @Override
    public void setupMDCContext() {
        MDCUtil.setupContextForRecord(record);
    }
}
