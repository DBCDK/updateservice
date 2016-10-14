package dk.dbc.updateservice.dto;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.updateservice.client.BibliographicRecordExtraData;

public class BibliographicRecordDto {
    private String recordSchema;
    private String recordPacking;
    private RecordDataDto recordDataDto;
    private ExtraRecordDataDto extraRecordDataDto;

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

    public RecordDataDto getRecordDataDto() {
        return recordDataDto;
    }

    public void setRecordDataDto(RecordDataDto recordDataDto) {
        this.recordDataDto = recordDataDto;
    }

    public ExtraRecordDataDto getExtraRecordDataDto() {
        return extraRecordDataDto;
    }

    public void setExtraRecordDataDto(ExtraRecordDataDto extraRecordDataDto) {
        this.extraRecordDataDto = extraRecordDataDto;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BibliographicRecordDto that = (BibliographicRecordDto) o;

        if (recordSchema != null ? !recordSchema.equals(that.recordSchema) : that.recordSchema != null) return false;
        if (recordPacking != null ? !recordPacking.equals(that.recordPacking) : that.recordPacking != null)
            return false;
        if (recordDataDto != null ? !recordDataDto.equals(that.recordDataDto) : that.recordDataDto != null)
            return false;
        return extraRecordDataDto != null ? extraRecordDataDto.equals(that.extraRecordDataDto) : that.extraRecordDataDto == null;

    }

    @Override
    public int hashCode() {
        int result = recordSchema != null ? recordSchema.hashCode() : 0;
        result = 31 * result + (recordPacking != null ? recordPacking.hashCode() : 0);
        result = 31 * result + (recordDataDto != null ? recordDataDto.hashCode() : 0);
        result = 31 * result + (extraRecordDataDto != null ? extraRecordDataDto.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "BibliographicRecordDto{" +
                "recordSchema='" + recordSchema + '\'' +
                ", recordPacking='" + recordPacking + '\'' +
                ", recordDataDto=" + recordDataDto +
                ", extraRecordDataDto=" + extraRecordDataDto +
                '}';
    }
}
