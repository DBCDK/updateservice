package dk.dbc.updateservice.entities;

import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import java.io.Serializable;
import java.util.Date;

@Entity
@Table(name = "dpk_override")
public class DpkOverride implements Serializable {
    @Id
    @Column(name = "request_uuid", nullable = false, length = 36)
    private String RequestUuid;

    @Column(name = "created_dtm", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date CreatedDtm;

    public String getRequestUuid() {
        return RequestUuid;
    }

    public void setRequestUuid(String requestUuid) {
        RequestUuid = requestUuid;
    }

    public Date getCreatedDtm() {
        return CreatedDtm;
    }

    public void setCreatedDtm(Date createdDtm) {
        CreatedDtm = createdDtm;
    }

    @Override
    public String toString() {
        return "DpkOverride{" +
                "RequestUuid='" + RequestUuid + '\'' +
                ", CreatedDtm=" + CreatedDtm +
                '}';
    }
}
