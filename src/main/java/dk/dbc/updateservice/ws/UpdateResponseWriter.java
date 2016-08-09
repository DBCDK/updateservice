package dk.dbc.updateservice.ws;

import dk.dbc.updateservice.service.api.UpdateRecordResult;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import dk.dbc.updateservice.service.api.ValidateEntry;
import dk.dbc.updateservice.service.api.ValidateInstance;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;

/**
 * Class to generate a complete response.
 * <p>
 * Usage:
 * <pre>
 *  UpdateResponseWriter writer = new UpdateResponseWriter();
 *  writer.addValidateResults( valErrorsList );
 *  writer.setUpdateStatus( UpdateStatusEnum.VALIDATION_ERROR );
 *
 *  UpdateRecordResult response = writer.getResponse();
 * </pre>
 * After the sequence the variable <code>response</code> will contain a
 * complete valid response that can be returned thought the JavaEE container.
 *
 * @author stp
 */
public class UpdateResponseWriter {
    private static final XLogger logger = XLoggerFactory.getXLogger(UpdateResponseWriter.class);
    private UpdateRecordResult response;

    public UpdateResponseWriter() {
        this.response = new UpdateRecordResult();
        this.response.setUpdateStatus(UpdateStatusEnum.OK);
    }

    public UpdateRecordResult getResponse() {
        return response;
    }

    /**
     * Adds a list of validation errors to the response.
     * <p>
     * If the list is empty then the response is not changed. In this case the
     * function is a nop.
     *
     * @param valErrors List of validation errors.
     */
    public void addValidateResults(List<ValidationError> valErrors) {
        logger.entry(valErrors);

        try {
            if (!valErrors.isEmpty()) {
                ValidateInstance instance = new ValidateInstance();
                for (ValidationError err : valErrors) {
                    ValidateEntry entry = new ValidateEntry();

                    HashMap<String, Object> params = err.getParams();
                    Object value;

                    entry.setWarningOrError(err.getType());

                    value = params.get("url");
                    if (value != null) {
                        entry.setUrlForDocumentation(value.toString());
                    }

                    value = params.get("message");
                    if (value != null) {
                        entry.setMessage(value.toString());
                    }

                    value = params.get("fieldno");
                    if (value != null) {
                        entry.setOrdinalPositionOfField(new BigDecimal(value.toString()).toBigInteger());
                    }

                    value = params.get("subfieldno");
                    if (value != null) {
                        entry.setOrdinalPositionOfSubField(new BigDecimal(value.toString()).toBigInteger());
                    }

                    instance.getValidateEntry().add(entry);
                }

                this.response.setValidateInstance(instance);
            }
        } finally {
            logger.exit();
        }
    }

    /**
     * Adds a list of validation entires to the response.
     * <p>
     * If the list is empty then the response is not changed. In this case the
     * function is a nop.
     *
     * @param entries List of validation errors.
     */
    public void addValidateEntries(List<ValidateEntry> entries) {
        logger.entry(entries);

        try {
            if (entries.isEmpty()) {
                return;
            }

            ValidateInstance instance = new ValidateInstance();
            for (ValidateEntry entry : entries) {
                instance.getValidateEntry().add(entry);
            }

            this.response.setValidateInstance(instance);
        } finally {
            logger.exit();
        }
    }

    /**
     * Sets the update status in the response.
     *
     * @param value The update status.
     */
    public void setUpdateStatus(UpdateStatusEnum value) {
        logger.entry();
        this.response.setUpdateStatus(value);
        logger.exit();
    }

    /**
     * Sets an error in the response.
     *
     * @param error The error to set in the response.
     */
    public void setError(dk.dbc.updateservice.service.api.Error error) {
        logger.entry();
        response.setError(error);
        logger.exit();
    }
}
