/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;

import org.w3c.dom.Document;

import javax.annotation.PostConstruct;
import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

@Stateless
@LocalBean
public class DocumentFactory {
    /*
     * @brief initialize document builder factory.
    */
    private DocumentBuilderFactory dbf;

    @PostConstruct
    public void init() {
        dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware( true );
    }

    public Document getNewDocument() throws ParserConfigurationException {
        final DocumentBuilder documentBuilder = dbf.newDocumentBuilder();
        return documentBuilder.newDocument();
    }
}
