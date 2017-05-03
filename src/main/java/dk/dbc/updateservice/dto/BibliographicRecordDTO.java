/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.dto;

public class BibliographicRecordDTO {
    private String recordSchema;
    private String recordPacking;
    private RecordDataDTO recordDataDTO;
    private ExtraRecordDataDTO extraRecordDataDTO;

    public String getRecordSchema() {
        return recordSchema;
    }

    public void setRecordSchema(String recordSchema) {
        this.recordSchema = recordSchema;
    }

    public String getRecordPacking() {
        return recordPacking;
    }

    public void setRecordPacking(String recordPacking) {
        this.recordPacking = recordPacking;
    }

    public RecordDataDTO getRecordDataDTO() {
        return recordDataDTO;
    }

    public void setRecordDataDTO(RecordDataDTO recordDataDTO) {
        this.recordDataDTO = recordDataDTO;
    }

    public ExtraRecordDataDTO getExtraRecordDataDTO() {
        return extraRecordDataDTO;
    }

    public void setExtraRecordDataDTO(ExtraRecordDataDTO extraRecordDataDTO) {
        this.extraRecordDataDTO = extraRecordDataDTO;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BibliographicRecordDTO that = (BibliographicRecordDTO) o;

        if (recordSchema != null ? !recordSchema.equals(that.recordSchema) : that.recordSchema != null) return false;
        if (recordPacking != null ? !recordPacking.equals(that.recordPacking) : that.recordPacking != null)
            return false;
        if (recordDataDTO != null ? !recordDataDTO.equals(that.recordDataDTO) : that.recordDataDTO != null)
            return false;
        return extraRecordDataDTO != null ? extraRecordDataDTO.equals(that.extraRecordDataDTO) : that.extraRecordDataDTO == null;

    }

    @Override
    public int hashCode() {
        int result = recordSchema != null ? recordSchema.hashCode() : 0;
        result = 31 * result + (recordPacking != null ? recordPacking.hashCode() : 0);
        result = 31 * result + (recordDataDTO != null ? recordDataDTO.hashCode() : 0);
        result = 31 * result + (extraRecordDataDTO != null ? extraRecordDataDTO.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "BibliographicRecordDTO{" +
                "recordSchema='" + recordSchema + '\'' +
                ", recordPacking='" + recordPacking + '\'' +
                ", recordDataDTO=" + recordDataDTO +
                ", extraRecordDataDTO=" + extraRecordDataDTO +
                '}';
    }
}
