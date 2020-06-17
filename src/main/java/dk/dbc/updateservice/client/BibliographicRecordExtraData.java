/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.client;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.util.Objects;

/**
 * This class represent extra data to associated with the record in an update
 * request.
 * <p>
 * <h3>Properties</h3>
 * <p>
 * <dl>
 * <dt>providerName</dt>
 * <dd>
 * Provider name is a string that is used to specify which queue in rawrepo that the updated record should
 * be placed in then the record is updated.
 * </dd>
 * </dl>
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "")
@XmlRootElement(namespace = BibliographicRecordExtraData.NAMESPACE,
        name = "updateRecordExtraData")
public class BibliographicRecordExtraData {

    public static final String NAMESPACE = "http://oss.dbc.dk/ns/updateRecordExtraData";

    private String providerName;
    private Integer priority;

    public BibliographicRecordExtraData() {
        this.providerName = null;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BibliographicRecordExtraData that = (BibliographicRecordExtraData) o;
        return Objects.equals(providerName, that.providerName) &&
                Objects.equals(priority, that.priority);
    }

    @Override
    public int hashCode() {
        return Objects.hash(providerName, priority);
    }

    @Override
    public String toString() {
        return "BibliographicRecordExtraData{" +
                "providerName='" + providerName + '\'' +
                ", priority=" + priority +
                '}';
    }
}
