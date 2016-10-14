package dk.dbc.updateservice.dto;

import java.util.ArrayList;
import java.util.List;

public class SchemasResponseDto {
    private UpdateStatusEnumDto updateStatusEnumDto;
    private List<SchemaDto> schemaDtoList;
    private boolean error = false;
    private String errorMessage;

    public UpdateStatusEnumDto getUpdateStatusEnumDto() {
        return updateStatusEnumDto;
    }

    public void setUpdateStatusEnumDto(UpdateStatusEnumDto updateStatusEnumDto) {
        this.updateStatusEnumDto = updateStatusEnumDto;
    }

    public List<SchemaDto> getSchemaDtoList() {
        if (schemaDtoList == null) {
            schemaDtoList = new ArrayList<>();
        }
        return schemaDtoList;
    }

    public void setSchemaDtoList(List<SchemaDto> schemaDtoList) {
        this.schemaDtoList = schemaDtoList;
    }

    public boolean isError() {
        return error;
    }

    public void setError(boolean error) {
        this.error = error;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SchemasResponseDto that = (SchemasResponseDto) o;

        if (error != that.error) return false;
        if (updateStatusEnumDto != that.updateStatusEnumDto) return false;
        if (schemaDtoList != null ? !schemaDtoList.equals(that.schemaDtoList) : that.schemaDtoList != null)
            return false;
        return errorMessage != null ? errorMessage.equals(that.errorMessage) : that.errorMessage == null;

    }

    @Override
    public int hashCode() {
        int result = updateStatusEnumDto != null ? updateStatusEnumDto.hashCode() : 0;
        result = 31 * result + (schemaDtoList != null ? schemaDtoList.hashCode() : 0);
        result = 31 * result + (error ? 1 : 0);
        result = 31 * result + (errorMessage != null ? errorMessage.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "SchemasResponseDto{" +
                "updateStatusEnumDto=" + updateStatusEnumDto +
                ", schemaDtoList=" + schemaDtoList +
                ", error=" + error +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}
