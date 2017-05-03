/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.dto;

public class SchemaDTO {
    private String schemaName;
    private String schemaInfo;

    public String getSchemaName() {
        return schemaName;
    }

    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }

    public String getSchemaInfo() {
        return schemaInfo;
    }

    public void setSchemaInfo(String schemaInfo) {
        this.schemaInfo = schemaInfo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SchemaDTO schemaDTO = (SchemaDTO) o;

        if (schemaName != null ? !schemaName.equals(schemaDTO.schemaName) : schemaDTO.schemaName != null) return false;
        return schemaInfo != null ? schemaInfo.equals(schemaDTO.schemaInfo) : schemaDTO.schemaInfo == null;

    }

    @Override
    public int hashCode() {
        int result = schemaName != null ? schemaName.hashCode() : 0;
        result = 31 * result + (schemaInfo != null ? schemaInfo.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "SchemaDTO{" +
                "schemaName='" + schemaName + '\'' +
                ", schemaInfo='" + schemaInfo + '\'' +
                '}';
    }
}
