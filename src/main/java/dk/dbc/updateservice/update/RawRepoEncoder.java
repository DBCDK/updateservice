//-----------------------------------------------------------------------------
package dk.dbc.updateservice.update;

//-----------------------------------------------------------------------------

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcXchangeFactory;
import dk.dbc.iscrum.records.marcxchange.CollectionType;
import dk.dbc.iscrum.records.marcxchange.ObjectFactory;
import dk.dbc.iscrum.records.marcxchange.RecordType;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;

/**
 * Class to encode a MarcRecord as marcxchange xml for the rawrepo.
 */
public class RawRepoEncoder {
    private static final XLogger logger = XLoggerFactory.getXLogger(RawRepoEncoder.class);
    private static final String SCHEMA_LOCATION = "http://www.loc.gov/standards/iso25577/marcxchange-1-1.xsd";
    static final String ENCODING = "UTF-8";

    public RawRepoEncoder() {
    }

    /**
     * Encodes the record as marcxchange.
     *
     * @param record The record to encode.
     * @return The encoded record as a sequence of bytes.
     * @throws JAXBException                if the record can not be encoded in marcxchange.
     * @throws UnsupportedEncodingException if the record can not be encoded in UTF-8
     */
    public byte[] encodeRecord(MarcRecord record) throws JAXBException, UnsupportedEncodingException {
        logger.entry(record);
        byte[] result = null;

        try {
            if (record.getFields().isEmpty()) {
                return null;
            }

            dk.dbc.iscrum.records.marcxchange.RecordType marcXchangeType = MarcXchangeFactory.createMarcXchangeFromMarc(record);

            ObjectFactory objectFactory = new ObjectFactory();
            JAXBElement<RecordType> jAXBElement = objectFactory.createRecord(marcXchangeType);

            JAXBContext jc = JAXBContext.newInstance(CollectionType.class);
            Marshaller marshaller = jc.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_SCHEMA_LOCATION, SCHEMA_LOCATION);

            StringWriter recData = new StringWriter();
            marshaller.marshal(jAXBElement, recData);

            logger.info("Marshalled record: {}", recData.toString());
            result = recData.toString().getBytes(ENCODING);

            return result;
        } finally {
            logger.exit(result);
        }
    }
}
