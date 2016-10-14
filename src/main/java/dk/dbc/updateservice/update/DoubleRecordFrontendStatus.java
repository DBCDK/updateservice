package dk.dbc.updateservice.update;

import java.util.List;

public class DoubleRecordFrontendStatus {
    private String status;
    private List<DoubleRecordFrontendContent> doubleRecordFrontendContents;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<DoubleRecordFrontendContent> getDoubleRecordFrontendContents() {
        return doubleRecordFrontendContents;
    }

    public void setDoubleRecordFrontendContents(List<DoubleRecordFrontendContent> doubleRecordFrontendContents) {
        this.doubleRecordFrontendContents = doubleRecordFrontendContents;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DoubleRecordFrontendStatus that = (DoubleRecordFrontendStatus) o;

        if (status != null ? !status.equals(that.status) : that.status != null) return false;
        return doubleRecordFrontendContents != null ? doubleRecordFrontendContents.equals(that.doubleRecordFrontendContents) : that.doubleRecordFrontendContents == null;

    }

    @Override
    public int hashCode() {
        int result = status != null ? status.hashCode() : 0;
        result = 31 * result + (doubleRecordFrontendContents != null ? doubleRecordFrontendContents.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "DoubleRecordFrontendStatus{" +
                "status='" + status + '\'' +
                ", doubleRecordFrontendContents=" + doubleRecordFrontendContents +
                '}';
    }
}
