/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;

import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
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
    /**
     * @brief initialize logging.
     */
    private XLogger logger;
    
    /*
     * @brief initialize document builder factory.
    */
    private DocumentBuilderFactory dbf;

    @PostConstruct
    public void init() {
        logger = XLoggerFactory.getXLogger(this.getClass());
        logger.entry();
        dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware( true );
        logger.exit();
    }

    public Document getNewDocument() throws ParserConfigurationException {
        logger.entry();
        DocumentBuilder documentBuilder = dbf.newDocumentBuilder();
        Document document = documentBuilder.newDocument();
        logger.exit();
        return document;
    }
}
