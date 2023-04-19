package dk.dbc.common.records.utils;

import dk.dbc.common.records.MarcConverter;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcXchangeFactory;
import dk.dbc.common.records.marcxchange.CollectionType;
import dk.dbc.common.records.marcxchange.ObjectFactory;
import dk.dbc.common.records.marcxchange.RecordType;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

public class RecordContentTransformer {
    private static final XLogger logger = XLoggerFactory.getXLogger(RecordContentTransformer.class);
    private static final String SCHEMA_LOCATION = "http://www.loc.gov/standards/iso25577/marcxchange-1-1.xsd";

    private static final JAXBContext jaxbContext;

    private RecordContentTransformer() {

    }

    static {
        try {
            jaxbContext = JAXBContext.newInstance(CollectionType.class);
        } catch (JAXBException e) {
            throw new RuntimeException("Exception while calling JAXBContext.newInstance", e);
        }
    }

    private static JAXBContext getJAXBContext() {
        return jaxbContext;
    }

    /**
     * Encodes the record as marcxchange.
     *
     * @param record The record to encode.
     * @return The encoded record as a sequence of bytes.
     * @throws JAXBException                if the record can not be encoded in marcxchange.
     */
    public static byte[] encodeRecord(MarcRecord record) throws JAXBException {
        if (record.getFields().isEmpty()) {
            return null;
        }

        RecordType marcXchangeType = MarcXchangeFactory.createMarcXchangeFromMarc(record);

        ObjectFactory objectFactory = new ObjectFactory();
        JAXBElement<RecordType> jAXBElement = objectFactory.createRecord(marcXchangeType);

        Marshaller marshaller = getJAXBContext().createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_SCHEMA_LOCATION, SCHEMA_LOCATION);

        StringWriter recData = new StringWriter();
        marshaller.marshal(jAXBElement, recData);

        logger.info("Marshalled record: {}", recData.toString());
        return recData.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static MarcRecord decodeRecord(byte[] bytes) {
        return MarcConverter.convertFromMarcXChange(new String(bytes, StandardCharsets.UTF_8));
    }

}
