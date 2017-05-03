/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.dto;

public class DoubleRecordFrontendDTO {
    private String message;
    private String pid;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getPid() {
        return pid;
    }

    public void setPid(String pid) {
        this.pid = pid;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DoubleRecordFrontendDTO that = (DoubleRecordFrontendDTO) o;

        if (message != null ? !message.equals(that.message) : that.message != null) return false;
        return pid != null ? pid.equals(that.pid) : that.pid == null;

    }

    @Override
    public int hashCode() {
        int result = message != null ? message.hashCode() : 0;
        result = 31 * result + (pid != null ? pid.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "DoubleRecordFrontendContent{" +
                "message='" + message + '\'' +
                ", pid='" + pid + '\'' +
                '}';
    }
}
