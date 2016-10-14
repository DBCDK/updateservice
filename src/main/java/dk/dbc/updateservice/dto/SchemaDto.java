package dk.dbc.updateservice.dto;

public class SchemaDto {
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

        SchemaDto schemaDto = (SchemaDto) o;

        if (schemaName != null ? !schemaName.equals(schemaDto.schemaName) : schemaDto.schemaName != null) return false;
        return schemaInfo != null ? schemaInfo.equals(schemaDto.schemaInfo) : schemaDto.schemaInfo == null;

    }

    @Override
    public int hashCode() {
        int result = schemaName != null ? schemaName.hashCode() : 0;
        result = 31 * result + (schemaInfo != null ? schemaInfo.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "SchemaDto{" +
                "schemaName='" + schemaName + '\'' +
                ", schemaInfo='" + schemaInfo + '\'' +
                '}';
    }
}
