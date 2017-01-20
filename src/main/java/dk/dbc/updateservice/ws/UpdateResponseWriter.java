package dk.dbc.updateservice.ws;

import dk.dbc.updateservice.actions.ServiceResult;
import dk.dbc.updateservice.dto.DoubleRecordFrontendDto;
import dk.dbc.updateservice.dto.MessageEntryDto;
import dk.dbc.updateservice.dto.UpdateRecordResponseDto;
import dk.dbc.updateservice.dto.UpdateStatusEnumDto;
import dk.dbc.updateservice.service.api.*;
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
public class UpdateResponseWriter {
    private static final XLogger logger = XLoggerFactory.getXLogger(UpdateResponseWriter.class);

    private UpdateRecordResponseDto updateRecordResponseDto;

    public UpdateResponseWriter() {
        updateRecordResponseDto = new UpdateRecordResponseDto();
        updateRecordResponseDto.setUpdateStatusEnumDto(UpdateStatusEnumDto.OK);
    }

    public UpdateRecordResult getResponse() {
        return convertResponseFromInternalFormatToExternalFormat(updateRecordResponseDto);
    }

    private void addMessageEntries(List<MessageEntryDto> entries) {
        if (entries != null && entries.size() > 0) {
            updateRecordResponseDto.addMessageEntryDtos(entries);
        }
    }

    private void addDoubleRecordFrontendDtos(List<DoubleRecordFrontendDto> doubleRecordFrontendDtos) {
        if (doubleRecordFrontendDtos != null && !doubleRecordFrontendDtos.isEmpty()) {
            updateRecordResponseDto.addDoubleRecordFrontendDtos(doubleRecordFrontendDtos);
        }
    }

    public void setUpdateStatus(UpdateStatusEnumDto value) {
        updateRecordResponseDto.setUpdateStatusEnumDto(value);
    }

    public void setServiceResult(ServiceResult serviceResult) {
        if (serviceResult != null) {
            updateRecordResponseDto.setUpdateStatusEnumDto(serviceResult.getStatus());
            updateRecordResponseDto.setDoubleRecordKey(serviceResult.getDoubleRecordKey());
            addMessageEntries(serviceResult.getEntries());
            addDoubleRecordFrontendDtos(serviceResult.getDoubleRecordFrontendDtos());
        }
    }

    private UpdateRecordResult convertResponseFromInternalFormatToExternalFormat(UpdateRecordResponseDto updateRecordResponseDto) {
        UpdateRecordResult updateRecordResult = new UpdateRecordResult();
        updateRecordResult.setUpdateStatus(convertUpdateStatusEnumFromInternalToExternalFormat(updateRecordResponseDto));
        if (updateRecordResponseDto.getDoubleRecordKey() != null) {
            updateRecordResult.setDoubleRecordKey(updateRecordResponseDto.getDoubleRecordKey());
            DoubleRecordEntries doubleRecordEntries = new DoubleRecordEntries();
            updateRecordResult.setDoubleRecordEntries(doubleRecordEntries);
            if (updateRecordResponseDto.getDoubleRecordFrontendDtos() != null && !updateRecordResponseDto.getDoubleRecordFrontendDtos().isEmpty()) {
                for (DoubleRecordFrontendDto doubleRecordFrontendDto : updateRecordResponseDto.getDoubleRecordFrontendDtos()) {
                    doubleRecordEntries.getDoubleRecordEntry().add(convertDoubleRecordEntryFromInternalToExternalFormat(doubleRecordFrontendDto));
                }
            }
        }
        if (updateRecordResponseDto.getMessageEntryDtos() != null) {
            Messages messages = new Messages();
            updateRecordResult.setMessages(messages);
            if (updateRecordResponseDto.getMessageEntryDtos() != null && !updateRecordResponseDto.getMessageEntryDtos().isEmpty()) {
                for (MessageEntryDto med : updateRecordResponseDto.getMessageEntryDtos()) {
                    messages.getMessageEntry().add(convertMessageEntryFromInternalToExternalFormat(med));
                }
            }
        }
        return updateRecordResult;
    }

    private UpdateStatusEnum convertUpdateStatusEnumFromInternalToExternalFormat(UpdateRecordResponseDto updateRecordResponseDto) {
        if (updateRecordResponseDto != null && updateRecordResponseDto.getUpdateStatusEnumDto() != null) {
            switch (updateRecordResponseDto.getUpdateStatusEnumDto()) {
                case OK:
                    return UpdateStatusEnum.OK;
                case FAILED:
                    return UpdateStatusEnum.FAILED;
                default:
                    break;
            }
        }
        return null;
    }

    private MessageEntry convertMessageEntryFromInternalToExternalFormat(MessageEntryDto messageEntryDto) {
        MessageEntry messageEntry = new MessageEntry();
        messageEntry.setType(convertInternalTypeEnumDtoToExternalType(messageEntryDto));
        messageEntry.setMessage(messageEntryDto.getMessage());
        messageEntry.setCode(messageEntryDto.getCode());
        messageEntry.setUrlForDocumentation(messageEntryDto.getUrlForDocumentation());
        messageEntry.setOrdinalPositionOfField(messageEntryDto.getOrdinalPositionOfField());
        messageEntry.setOrdinalPositionOfSubfield(messageEntryDto.getOrdinalPositionOfSubfield());
        messageEntry.setOrdinalPositionInSubfield(messageEntryDto.getOrdinalPositionInSubfield());
        return messageEntry;
    }

    private Type convertInternalTypeEnumDtoToExternalType(MessageEntryDto messageEntryDto) {
        if (messageEntryDto != null && messageEntryDto.getType() != null) {
            switch (messageEntryDto.getType()) {
                case ERROR:
                    return Type.ERROR;
                case FATAL:
                    return Type.FATAL;
                case WARNING:
                    return Type.WARNING;
                default:
                    break;
            }
        }
        logger.warn("Got messageEntryDto without type, returning ERROR as type, messageEntryDto: " + messageEntryDto);
        return Type.ERROR;
    }

    private DoubleRecordEntry convertDoubleRecordEntryFromInternalToExternalFormat(DoubleRecordFrontendDto doubleRecordFrontendDto) {
        DoubleRecordEntry doubleRecordEntry = new DoubleRecordEntry();
        doubleRecordEntry.setPid(doubleRecordFrontendDto.getPid());
        doubleRecordEntry.setMessage(doubleRecordFrontendDto.getMessage());
        return doubleRecordEntry;
    }
}
