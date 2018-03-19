/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.client;

import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

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
    private static final XLogger logger = XLoggerFactory.getXLogger(BibliographicRecordExtraData.class);

    public static final String NAMESPACE = "http://oss.dbc.dk/ns/updateRecordExtraData";

    private String providerName;

    public BibliographicRecordExtraData() {
        this.providerName = null;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }
}
