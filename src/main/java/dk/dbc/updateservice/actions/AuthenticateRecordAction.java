package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.iscrum.utils.logback.filters.BusinessLoggerFilter;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.service.api.Entry;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.ws.MDCUtil;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.ejb.EJBException;
import java.util.List;

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

    MarcRecord record;
    /**
     * Constructs an instance with a template name and a record.
     * <p/>
     * The JavaScript logic needs an JavaScript environment and a set of
     * settings to work properly. These can be set though the properties <code>scripter</code>
     * and <code>settings</code>. This constructor initialize these to null.
     *
     * @param globalActionState State object containing data with data from request.
     */
    public AuthenticateRecordAction(GlobalActionState globalActionState, MarcRecord marcRecord) {
        super(AuthenticateRecordAction.class.getSimpleName(), globalActionState);
        record = marcRecord;
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
            bizLogger.info("Login user: {}/{}", state.getUpdateRecordRequest().getAuthentication().getUserIdAut(), state.getUpdateRecordRequest().getAuthentication().getGroupIdAut());
            bizLogger.info("Handling record:\n{}", record);

            List<Entry> errors = state.getAuthenticator().authenticateRecord(state, record);
            result = new ServiceResult();
            result.setEntries(errors);

//             TODO: VERSION2: det her ligner spild af arbejde
            MarcRecordReader reader = new MarcRecordReader(record);
            String recordId = reader.recordId();
            String agencyId = reader.agencyId();
            if (result.hasErrors()) {
                bizLogger.warn("Authenticating of record {{}:{}} with user {}/{} failed", recordId, agencyId, state.getUpdateRecordRequest().getAuthentication().getGroupIdAut(), state.getUpdateRecordRequest().getAuthentication().getUserIdAut());
                result.setStatus(UpdateStatusEnum.FAILED);
            } else {
                bizLogger.info("Authenticating record {{}:{}} with user {}/{} successfully", recordId, agencyId, state.getUpdateRecordRequest().getAuthentication().getGroupIdAut(), state.getUpdateRecordRequest().getAuthentication().getUserIdAut());
                result.setStatus(UpdateStatusEnum.OK);
            }
            return result;
        } catch (EJBException | ScripterException ex) {
            Throwable businessException = findServiceException(ex);
            String message = String.format(state.getMessages().getString("internal.authenticate.record.error"), businessException.getMessage());
            bizLogger.error(message);
            logger.warn("Exception doing authentication: ", businessException);
            return result = ServiceResult.newErrorResult(UpdateStatusEnum.FAILED, message, state);
        } finally {
            logger.exit(result);
        }
    }

    @Override
    public void setupMDCContext() {
        MDCUtil.setupContextForRecord(record);
    }
}
