package dk.dbc.updateservice.ws;

import dk.dbc.updateservice.service.api.Entry;
import dk.dbc.updateservice.service.api.Messages;
import dk.dbc.updateservice.service.api.UpdateRecordResult;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;

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
//TODO: VERSION2: denne her klasse virker som overkill, burde ændres til at konvertere fra intern datarepresentation til ekstern xsd format så intern data format og input/output format adskilles
public class UpdateResponseWriter {
    private UpdateRecordResult response;

    public UpdateResponseWriter() {
        response = new UpdateRecordResult();
        response.setUpdateStatus(UpdateStatusEnum.OK);
    }

    public UpdateRecordResult getResponse() {
        return response;
    }

    /**
     * Adds a list of validation entries to the response.
     * <p>
     * If the list is empty then the response is not changed. In this case the
     * function is a nop.
     *
     * @param entries List of validation errors.
     */
    public void addValidateEntries(List<Entry> entries) {
        if (entries != null && !entries.isEmpty()) {
            if (response.getMessages() == null) {
                response.setMessages(new Messages());
            }
            response.getMessages().getEntry().addAll(entries);
        }
    }

    /**
     * Sets an error in the response.
     *
     * @param entry The error to set in the response.
     */
    public void addValidateEntries(Entry entry) {
        if (entry != null) {
            if (response.getMessages() == null) {
                response.setMessages(new Messages());
            }
            response.getMessages().getEntry().add(entry);
        }
    }

    /**
     * Sets the update status in the response.
     *
     * @param value The update status.
     */
    public void setUpdateStatus(UpdateStatusEnum value) {
        response.setUpdateStatus(value);
    }
}
