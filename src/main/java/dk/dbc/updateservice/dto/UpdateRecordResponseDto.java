package dk.dbc.updateservice.dto;

import java.util.ArrayList;
import java.util.List;

public class UpdateRecordResponseDto {
    private UpdateStatusEnumDto updateStatusEnumDto;
    private String doubleRecordKey;
    private List<MessageEntryDto> messageEntryDtos;
    private List<DoubleRecordFrontendDto> doubleRecordFrontendDtos;

    public UpdateStatusEnumDto getUpdateStatusEnumDto() {
        return updateStatusEnumDto;
    }

    public void setUpdateStatusEnumDto(UpdateStatusEnumDto updateStatusEnumDto) {
        this.updateStatusEnumDto = updateStatusEnumDto;
    }

    public String getDoubleRecordKey() {
        return doubleRecordKey;
    }

    public void setDoubleRecordKey(String doubleRecordKey) {
        this.doubleRecordKey = doubleRecordKey;
    }

    public List<MessageEntryDto> getMessageEntryDtos() {
        return messageEntryDtos;
    }

    public List<DoubleRecordFrontendDto> getDoubleRecordFrontendDtos() {
        return doubleRecordFrontendDtos;
    }

    public void addMessageEntryDtos(List<MessageEntryDto> messageEntryDtos) {
        if (messageEntryDtos != null && !messageEntryDtos.isEmpty()) {
            if (this.messageEntryDtos == null) {
                this.messageEntryDtos = new ArrayList<>();
            }
            this.messageEntryDtos.addAll(messageEntryDtos);
        }
    }

    public void addDoubleRecordFrontendDtos(List<DoubleRecordFrontendDto> doubleRecordFrontendDtos) {
        if (doubleRecordFrontendDtos != null && !doubleRecordFrontendDtos.isEmpty()) {
            if (this.doubleRecordFrontendDtos == null) {
                this.doubleRecordFrontendDtos = new ArrayList<>();
            }
            this.doubleRecordFrontendDtos.addAll(doubleRecordFrontendDtos);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UpdateRecordResponseDto that = (UpdateRecordResponseDto) o;

        if (updateStatusEnumDto != that.updateStatusEnumDto) return false;
        if (doubleRecordKey != null ? !doubleRecordKey.equals(that.doubleRecordKey) : that.doubleRecordKey != null)
            return false;
        return messageEntryDtos != null ? messageEntryDtos.equals(that.messageEntryDtos) : that.messageEntryDtos == null;

    }

    @Override
    public int hashCode() {
        int result = updateStatusEnumDto != null ? updateStatusEnumDto.hashCode() : 0;
        result = 31 * result + (doubleRecordKey != null ? doubleRecordKey.hashCode() : 0);
        result = 31 * result + (messageEntryDtos != null ? messageEntryDtos.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "UpdateRecordResponseDto{" +
                "updateStatusEnumDto=" + updateStatusEnumDto +
                ", doubleRecordKey='" + doubleRecordKey + '\'' +
                ", messageEntryDtos=" + messageEntryDtos +
                '}';
    }
}
