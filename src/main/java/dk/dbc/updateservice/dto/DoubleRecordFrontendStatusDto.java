package dk.dbc.updateservice.dto;

import java.util.List;

public class DoubleRecordFrontendStatusDto {
    private String status;
    private List<DoubleRecordFrontendDto> doubleRecordFrontendDtos;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<DoubleRecordFrontendDto> getDoubleRecordFrontendDtos() {
        return doubleRecordFrontendDtos;
    }

    public void setDoubleRecordFrontendDtos(List<DoubleRecordFrontendDto> doubleRecordFrontendDtos) {
        this.doubleRecordFrontendDtos = doubleRecordFrontendDtos;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DoubleRecordFrontendStatusDto that = (DoubleRecordFrontendStatusDto) o;

        if (status != null ? !status.equals(that.status) : that.status != null) return false;
        return doubleRecordFrontendDtos != null ? doubleRecordFrontendDtos.equals(that.doubleRecordFrontendDtos) : that.doubleRecordFrontendDtos == null;

    }

    @Override
    public int hashCode() {
        int result = status != null ? status.hashCode() : 0;
        result = 31 * result + (doubleRecordFrontendDtos != null ? doubleRecordFrontendDtos.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "DoubleRecordFrontendStatus{" +
                "status='" + status + '\'' +
                ", doubleRecordFrontendContents=" + doubleRecordFrontendDtos +
                '}';
    }
}
