/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.dto;

import java.util.ArrayList;
import java.util.List;

public class SchemasResponseDTO {
    private UpdateStatusEnumDTO updateStatusEnumDTO;
    private List<SchemaDTO> schemaDTOList;
    private boolean error = false;
    private String errorMessage;

    public UpdateStatusEnumDTO getUpdateStatusEnumDTO() {
        return updateStatusEnumDTO;
    }

    public void setUpdateStatusEnumDTO(UpdateStatusEnumDTO updateStatusEnumDTO) {
        this.updateStatusEnumDTO = updateStatusEnumDTO;
    }

    public List<SchemaDTO> getSchemaDTOList() {
        if (schemaDTOList == null) {
            schemaDTOList = new ArrayList<>();
        }
        return schemaDTOList;
    }

    public void setSchemaDTOList(List<SchemaDTO> schemaDTOList) {
        this.schemaDTOList = schemaDTOList;
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

        SchemasResponseDTO that = (SchemasResponseDTO) o;

        if (error != that.error) return false;
        if (updateStatusEnumDTO != that.updateStatusEnumDTO) return false;
        if (schemaDTOList != null ? !schemaDTOList.equals(that.schemaDTOList) : that.schemaDTOList != null)
            return false;
        return errorMessage != null ? errorMessage.equals(that.errorMessage) : that.errorMessage == null;

    }

    @Override
    public int hashCode() {
        int result = updateStatusEnumDTO != null ? updateStatusEnumDTO.hashCode() : 0;
        result = 31 * result + (schemaDTOList != null ? schemaDTOList.hashCode() : 0);
        result = 31 * result + (error ? 1 : 0);
        result = 31 * result + (errorMessage != null ? errorMessage.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "SchemasResponseDTO{" +
                "updateStatusEnumDTO=" + updateStatusEnumDTO +
                ", schemaDTOList=" + schemaDTOList +
                ", error=" + error +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}
