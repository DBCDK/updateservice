/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.dto;

import java.util.List;

public class DoubleRecordFrontendStatusDTO {
    private String status;
    private List<DoubleRecordFrontendDTO> doubleRecordFrontendDTOs;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<DoubleRecordFrontendDTO> getDoubleRecordFrontendDTOs() {
        return doubleRecordFrontendDTOs;
    }

    public void setDoubleRecordFrontendDTOs(List<DoubleRecordFrontendDTO> doubleRecordFrontendDTOs) {
        this.doubleRecordFrontendDTOs = doubleRecordFrontendDTOs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DoubleRecordFrontendStatusDTO that = (DoubleRecordFrontendStatusDTO) o;

        if (status != null ? !status.equals(that.status) : that.status != null) return false;
        return doubleRecordFrontendDTOs != null ? doubleRecordFrontendDTOs.equals(that.doubleRecordFrontendDTOs) : that.doubleRecordFrontendDTOs == null;

    }

    @Override
    public int hashCode() {
        int result = status != null ? status.hashCode() : 0;
        result = 31 * result + (doubleRecordFrontendDTOs != null ? doubleRecordFrontendDTOs.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "DoubleRecordFrontendStatus{" +
                "status='" + status + '\'' +
                ", doubleRecordFrontendContents=" + doubleRecordFrontendDTOs +
                '}';
    }
}
