/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.ws;

import dk.dbc.updateservice.actions.ServiceResult;
import dk.dbc.updateservice.dto.DoubleRecordFrontendDTO;
import dk.dbc.updateservice.dto.MessageEntryDTO;
import dk.dbc.updateservice.dto.UpdateRecordResponseDTO;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
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
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(UpdateResponseWriter.class);

    private UpdateRecordResponseDTO updateRecordResponseDTO;

    public UpdateResponseWriter() {
        updateRecordResponseDTO = new UpdateRecordResponseDTO();
        updateRecordResponseDTO.setUpdateStatusEnumDTO(UpdateStatusEnumDTO.OK);
    }

    public UpdateResponseWriter(UpdateRecordResponseDTO updateRecordResponseDTO) {
        this.updateRecordResponseDTO = updateRecordResponseDTO;
    }

    public UpdateRecordResult getResponse() {
        return convertResponseFromInternalFormatToExternalFormat(updateRecordResponseDTO);
    }

    public UpdateRecordResponseDTO getUpdateRecordResponseDTO() {
        return updateRecordResponseDTO;
    }

    private void addMessageEntries(List<MessageEntryDTO> entries) {
        if (entries != null && entries.size() > 0) {
            updateRecordResponseDTO.addMessageEntryDtos(entries);
        }
    }

    private void addDoubleRecordFrontendDtos(List<DoubleRecordFrontendDTO> doubleRecordFrontendDTOS) {
        if (doubleRecordFrontendDTOS != null && !doubleRecordFrontendDTOS.isEmpty()) {
            updateRecordResponseDTO.addDoubleRecordFrontendDtos(doubleRecordFrontendDTOS);
        }
    }

    public void setUpdateStatus(UpdateStatusEnumDTO value) {
        updateRecordResponseDTO.setUpdateStatusEnumDTO(value);
    }

    public void setServiceResult(ServiceResult serviceResult) {
        if (serviceResult != null) {
            updateRecordResponseDTO.setUpdateStatusEnumDTO(serviceResult.getStatus());
            updateRecordResponseDTO.setDoubleRecordKey(serviceResult.getDoubleRecordKey());
            addMessageEntries(serviceResult.getEntries());
            addDoubleRecordFrontendDtos(serviceResult.getDoubleRecordFrontendDTOS());
        }
    }

    private UpdateRecordResult convertResponseFromInternalFormatToExternalFormat(UpdateRecordResponseDTO updateRecordResponseDTO) {
        UpdateRecordResult updateRecordResult = new UpdateRecordResult();
        updateRecordResult.setUpdateStatus(convertUpdateStatusEnumFromInternalToExternalFormat(updateRecordResponseDTO));
        if (updateRecordResponseDTO.getDoubleRecordKey() != null) {
            updateRecordResult.setDoubleRecordKey(updateRecordResponseDTO.getDoubleRecordKey());
            DoubleRecordEntries doubleRecordEntries = new DoubleRecordEntries();
            updateRecordResult.setDoubleRecordEntries(doubleRecordEntries);
            if (updateRecordResponseDTO.getDoubleRecordFrontendDTOS() != null && !updateRecordResponseDTO.getDoubleRecordFrontendDTOS().isEmpty()) {
                for (DoubleRecordFrontendDTO doubleRecordFrontendDTO : updateRecordResponseDTO.getDoubleRecordFrontendDTOS()) {
                    doubleRecordEntries.getDoubleRecordEntry().add(convertDoubleRecordEntryFromInternalToExternalFormat(doubleRecordFrontendDTO));
                }
            }
        }
        if (updateRecordResponseDTO.getMessageEntryDTOS() != null) {
            Messages messages = new Messages();
            updateRecordResult.setMessages(messages);
            if (updateRecordResponseDTO.getMessageEntryDTOS() != null && !updateRecordResponseDTO.getMessageEntryDTOS().isEmpty()) {
                for (MessageEntryDTO med : updateRecordResponseDTO.getMessageEntryDTOS()) {
                    messages.getMessageEntry().add(convertMessageEntryFromInternalToExternalFormat(med));
                }
            }
        }
        return updateRecordResult;
    }

    private UpdateStatusEnum convertUpdateStatusEnumFromInternalToExternalFormat(UpdateRecordResponseDTO updateRecordResponseDTO) {
        if (updateRecordResponseDTO != null && updateRecordResponseDTO.getUpdateStatusEnumDTO() != null) {
            switch (updateRecordResponseDTO.getUpdateStatusEnumDTO()) {
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

    private MessageEntry convertMessageEntryFromInternalToExternalFormat(MessageEntryDTO messageEntryDTO) {
        MessageEntry messageEntry = new MessageEntry();
        messageEntry.setType(convertInternalTypeEnumDtoToExternalType(messageEntryDTO));
        messageEntry.setMessage(messageEntryDTO.getMessage());
        messageEntry.setCode(messageEntryDTO.getCode());
        messageEntry.setUrlForDocumentation(messageEntryDTO.getUrlForDocumentation());
        messageEntry.setOrdinalPositionOfField(messageEntryDTO.getOrdinalPositionOfField());
        messageEntry.setOrdinalPositionOfSubfield(messageEntryDTO.getOrdinalPositionOfSubfield());
        messageEntry.setOrdinalPositionInSubfield(messageEntryDTO.getOrdinalPositionInSubfield());
        return messageEntry;
    }

    private Type convertInternalTypeEnumDtoToExternalType(MessageEntryDTO messageEntryDTO) {
        if (messageEntryDTO != null && messageEntryDTO.getType() != null) {
            switch (messageEntryDTO.getType()) {
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
        LOGGER.warn("Got messageEntryDTO without type, returning ERROR as type, messageEntryDTO: " + messageEntryDTO);
        return Type.ERROR;
    }

    private DoubleRecordEntry convertDoubleRecordEntryFromInternalToExternalFormat(DoubleRecordFrontendDTO doubleRecordFrontendDTO) {
        DoubleRecordEntry doubleRecordEntry = new DoubleRecordEntry();
        doubleRecordEntry.setPid(doubleRecordFrontendDTO.getPid());
        doubleRecordEntry.setMessage(doubleRecordFrontendDTO.getMessage());
        return doubleRecordEntry;
    }
}
