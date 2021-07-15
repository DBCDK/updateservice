/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.dto.writers;

import dk.dbc.updateservice.actions.ServiceResult;
import dk.dbc.updateservice.dto.DoubleRecordFrontendDTO;
import dk.dbc.updateservice.dto.MessageEntryDTO;
import dk.dbc.updateservice.dto.UpdateRecordResponseDTO;
import java.util.List;

public class UpdateRecordResponseDTOWriter {
    public static UpdateRecordResponseDTO newInstance(ServiceResult serviceResult) {
        UpdateRecordResponseDTO updateRecordResponseDTO = new UpdateRecordResponseDTO();
        return updateWithServiceResult(serviceResult, updateRecordResponseDTO);
    }
    private static UpdateRecordResponseDTO updateWithServiceResult(ServiceResult serviceResult, UpdateRecordResponseDTO updateRecordResponseDTO) {
        if (serviceResult != null) {
            updateRecordResponseDTO.setUpdateStatusEnumDTO(serviceResult.getStatus());
            updateRecordResponseDTO.setDoubleRecordKey(serviceResult.getDoubleRecordKey());
            addMessageEntries(serviceResult.getEntries(), updateRecordResponseDTO);
            addDoubleRecordFrontendDtos(serviceResult.getDoubleRecordFrontendDTOS(), updateRecordResponseDTO);
        }
        return updateRecordResponseDTO;
    }

    private static void addMessageEntries(List<MessageEntryDTO> entries, UpdateRecordResponseDTO updateRecordResponseDTO) {
        if (entries != null && !entries.isEmpty()) {
            updateRecordResponseDTO.addMessageEntryDtos(entries);
        }
    }

    private static void addDoubleRecordFrontendDtos(List<DoubleRecordFrontendDTO> doubleRecordFrontendDTOS, UpdateRecordResponseDTO updateRecordResponseDTO) {
        if (doubleRecordFrontendDTOS != null && !doubleRecordFrontendDTOS.isEmpty()) {
            updateRecordResponseDTO.addDoubleRecordFrontendDtos(doubleRecordFrontendDTOS);
        }
    }
}
