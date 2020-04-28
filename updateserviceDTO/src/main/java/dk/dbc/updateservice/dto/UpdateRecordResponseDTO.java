/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.dto;

import java.util.ArrayList;
import java.util.List;

public class UpdateRecordResponseDTO {
    private UpdateStatusEnumDTO updateStatusEnumDTO;
    private String doubleRecordKey;
    private List<MessageEntryDTO> messageEntryDTOS;
    private List<DoubleRecordFrontendDTO> doubleRecordFrontendDTOS;

    public UpdateStatusEnumDTO getUpdateStatusEnumDTO() {
        return updateStatusEnumDTO;
    }

    public void setUpdateStatusEnumDTO(UpdateStatusEnumDTO updateStatusEnumDTO) {
        this.updateStatusEnumDTO = updateStatusEnumDTO;
    }

    public String getDoubleRecordKey() {
        return doubleRecordKey;
    }

    public void setDoubleRecordKey(String doubleRecordKey) {
        this.doubleRecordKey = doubleRecordKey;
    }

    public List<MessageEntryDTO> getMessageEntryDTOS() {
        return messageEntryDTOS;
    }

    public List<DoubleRecordFrontendDTO> getDoubleRecordFrontendDTOS() {
        return doubleRecordFrontendDTOS;
    }

    public void addMessageEntryDtos(List<MessageEntryDTO> messageEntryDTOS) {
        if (messageEntryDTOS != null && !messageEntryDTOS.isEmpty()) {
            if (this.messageEntryDTOS == null) {
                this.messageEntryDTOS = new ArrayList<>();
            }
            this.messageEntryDTOS.addAll(messageEntryDTOS);
        }
    }

    public void addDoubleRecordFrontendDtos(List<DoubleRecordFrontendDTO> doubleRecordFrontendDTOS) {
        if (doubleRecordFrontendDTOS != null && !doubleRecordFrontendDTOS.isEmpty()) {
            if (this.doubleRecordFrontendDTOS == null) {
                this.doubleRecordFrontendDTOS = new ArrayList<>();
            }
            this.doubleRecordFrontendDTOS.addAll(doubleRecordFrontendDTOS);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UpdateRecordResponseDTO that = (UpdateRecordResponseDTO) o;

        if (updateStatusEnumDTO != that.updateStatusEnumDTO) return false;
        if (doubleRecordKey != null ? !doubleRecordKey.equals(that.doubleRecordKey) : that.doubleRecordKey != null)
            return false;
        return messageEntryDTOS != null ? messageEntryDTOS.equals(that.messageEntryDTOS) : that.messageEntryDTOS == null;

    }

    @Override
    public int hashCode() {
        int result = updateStatusEnumDTO != null ? updateStatusEnumDTO.hashCode() : 0;
        result = 31 * result + (doubleRecordKey != null ? doubleRecordKey.hashCode() : 0);
        result = 31 * result + (messageEntryDTOS != null ? messageEntryDTOS.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "UpdateRecordResponseDTO{" +
                "updateStatusEnumDTO=" + updateStatusEnumDTO +
                ", doubleRecordKey='" + doubleRecordKey + '\'' +
                ", messageEntryDTOS=" + messageEntryDTOS +
                '}';
    }
}
