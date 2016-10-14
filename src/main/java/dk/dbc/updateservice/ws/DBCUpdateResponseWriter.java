package dk.dbc.updateservice.ws;

import dk.dbc.updateservice.actions.ServiceResult;
import dk.dbc.updateservice.dto.MessageEntryDto;
import dk.dbc.updateservice.dto.UpdateRecordResponseDto;
import dk.dbc.updateservice.dto.UpdateStatusEnumDto;
import dk.dbc.updateservice.service.api.DoubleRecordEntries;
import dk.dbc.updateservice.service.api.DoubleRecordEntry;
import dk.dbc.updateservice.service.api.MessageEntry;
import dk.dbc.updateservice.service.api.Messages;
import dk.dbc.updateservice.service.api.Type;
import dk.dbc.updateservice.service.api.UpdateRecordResult;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.util.List;

/**
 * Class to generate a complete response.
 * <p>
 * Usage:
 * <pre>
 *  UpdateResponseWriter writer = new UpdateResponseWriter();
 *  writer.addValidateResults(valErrorsList);
 *  writer.setUpdateStatus(UpdateStatusEnum.VALIDATION_ERROR);
 *
 *  UpdateRecordResult response = writer.getResponse();
 * </pre>
 * After the sequence the variable <code>response</code> will contain a
 * complete valid response that can be returned thought the JavaEE container.
 */
public class DBCUpdateResponseWriter {
    private static final XLogger logger = XLoggerFactory.getXLogger(DBCUpdateResponseWriter.class);

    private UpdateRecordResponseDto updateRecordResponseDto;

    public DBCUpdateResponseWriter() {
        updateRecordResponseDto = new UpdateRecordResponseDto();
        updateRecordResponseDto.setUpdateStatusEnumDto(UpdateStatusEnumDto.OK);
    }

    public UpdateRecordResult getResponse() {
        return convertResponseFromInternalFormatToExternalFormat(updateRecordResponseDto);
    }

    public void addMessageEntries(List<MessageEntryDto> entries) {
        updateRecordResponseDto.addMessageEntryDtos(entries);
    }

    public void addMessageEntry(MessageEntryDto entry) {
        if (entry != null) {
            updateRecordResponseDto.addMessageEntryDtos(entry);
        }
    }

    public void setUpdateStatus(UpdateStatusEnumDto value) {
        updateRecordResponseDto.setUpdateStatusEnumDto(value);
    }

    public void setServiceResult(ServiceResult serviceResult) {
        updateRecordResponseDto.setUpdateStatusEnumDto(serviceResult.getStatus());
        updateRecordResponseDto.setDoubleRecordKey(serviceResult.getDoubleRecordKey());
        addMessageEntries(serviceResult.getEntries());
    }

    private UpdateRecordResult convertResponseFromInternalFormatToExternalFormat(UpdateRecordResponseDto updateRecordResponseDto) {
        UpdateRecordResult updateRecordResult = new UpdateRecordResult();
        updateRecordResult.setUpdateStatus(convertUpdateStatusEnumFromInternalToExternalFormat(updateRecordResponseDto));
        if (updateRecordResponseDto.getDoubleRecordKey() != null) {
            updateRecordResult.setDoubleRecordKey(updateRecordResponseDto.getDoubleRecordKey());
            DoubleRecordEntries doubleRecordEntries = new DoubleRecordEntries();
            updateRecordResult.setDoubleRecordEntries(doubleRecordEntries);
            if (updateRecordResponseDto.getMessageEntryDtos() != null && !updateRecordResponseDto.getMessageEntryDtos().isEmpty()) {
                for (MessageEntryDto med : updateRecordResponseDto.getMessageEntryDtos()) {
                    doubleRecordEntries.getDoubleRecordEntry().add(convertDoubleRecordEntryFromInternalToExternalFormat(med));
                }
            }
        } else {
            if (updateRecordResponseDto.getMessageEntryDtos() != null) {
                Messages messages = new Messages();
                updateRecordResult.setMessages(messages);
                if (updateRecordResponseDto.getMessageEntryDtos() != null && !updateRecordResponseDto.getMessageEntryDtos().isEmpty()) {
                    for (MessageEntryDto med : updateRecordResponseDto.getMessageEntryDtos()) {
                        messages.getMessageEntry().add(convertMessageEntryFromInternalToExternalFormat(med));
                    }
                }
            }

        }
        return updateRecordResult;
    }

    private UpdateStatusEnum convertUpdateStatusEnumFromInternalToExternalFormat(UpdateRecordResponseDto updateRecordResponseDto) {
        UpdateStatusEnum updateStatusEnum = null;
        if (updateRecordResponseDto != null && updateRecordResponseDto.getUpdateStatusEnumDto() != null) {
            switch (updateRecordResponseDto.getUpdateStatusEnumDto()) {
                case OK:
                    updateStatusEnum = UpdateStatusEnum.OK;
                    break;
                case FAILED:
                    updateStatusEnum = UpdateStatusEnum.FAILED;
                    break;
                default:
                    break;
            }
        }
        return updateStatusEnum;
    }

    private MessageEntry convertMessageEntryFromInternalToExternalFormat(MessageEntryDto messageEntryDto) {
        MessageEntry messageEntry = new MessageEntry();
        switch (messageEntryDto.getType()) {
            case ERROR:
                messageEntry.setType(Type.ERROR);
                break;
            case FATAL:
                messageEntry.setType(Type.FATAL);
                break;
            case WARNING:
                messageEntry.setType(Type.WARNING);
                break;
            default:
                break;
        }
        messageEntry.setMessage(messageEntryDto.getMessage());
        messageEntry.setCode(messageEntryDto.getCode());
        messageEntry.setUrlForDocumentation(messageEntryDto.getUrlForDocumentation());
        messageEntry.setOrdinalPositionOfField(messageEntryDto.getOrdinalPositionOfField());
        messageEntry.setOrdinalPositionOfSubfield(messageEntryDto.getOrdinalPositionOfSubfield());
        messageEntry.setOrdinalPositionInSubfield(messageEntryDto.getOrdinalPositionInSubfield());
        return messageEntry;
    }

    private DoubleRecordEntry convertDoubleRecordEntryFromInternalToExternalFormat(MessageEntryDto messageEntryDto) {
        DoubleRecordEntry doubleRecordEntry = new DoubleRecordEntry();
        doubleRecordEntry.setPid(messageEntryDto.getPid());
        doubleRecordEntry.setMessage(messageEntryDto.getMessage());
        return doubleRecordEntry;
    }
}
