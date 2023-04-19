package dk.dbc.common.records;

import dk.dbc.common.records.marcxchange.CollectionType;
import dk.dbc.common.records.marcxchange.DataFieldType;
import dk.dbc.common.records.marcxchange.ObjectFactory;
import dk.dbc.common.records.marcxchange.RecordType;
import dk.dbc.common.records.marcxchange.SubfieldatafieldType;
import org.w3c.dom.Document;

import javax.xml.bind.JAXB;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.util.ArrayList;

/**
 * Converter class to convert between marcXchange and MarcRecord.
 */
public class MarcConverter {
    private static final JAXBContext jaxbContext;

    private MarcConverter() {

    }

    static {
        try {
            jaxbContext = JAXBContext.newInstance(RecordType.class);
        } catch (JAXBException e) {
            throw new RuntimeException("Exception while calling JAXBContext.newInstance", e);
        }
    }

    private static JAXBContext getJAXBContext() {
        return jaxbContext;
    }

    /**
     * Constructs a MarcRecord from a String that contains an marcxchange xml document.
     *
     * @param xml Some marcxchange xml.
     * @return A MarcRecord. If no records are found, when we returns an empty MarcRecord.
     */
    public static MarcRecord convertFromMarcXChange(String xml) {
        // Try to unmarshal a collection
        CollectionType collection = JAXB.unmarshal(new StreamSource(new StringReader(xml)), CollectionType.class);
        if (!collection.getRecord().isEmpty()) {
            return convertFromRecordType(collection.getRecord().get(0));
        }
        // Try to unmarshal a single record
        RecordType record = JAXB.unmarshal(new StreamSource(new StringReader(xml)), RecordType.class);
        return convertFromRecordType(record);
    }

    /**
     * Constructs a MarcRecord from a Source that contains an marcxchange xml document.
     *
     * @param xml Some marcxchange xml.
     * @return A MarcRecord. If no records are found, when we returns an
     * empty MarcRecord.
     */
    public static MarcRecord createFromMarcXChange(Source xml) {
        // Try to unmarshal a collection
        CollectionType collection = JAXB.unmarshal(xml, CollectionType.class);
        if (!collection.getRecord().isEmpty()) {
            return convertFromRecordType(collection.getRecord().get(0));
        }

        // Try to unmarshal a single record
        RecordType record = JAXB.unmarshal(xml, RecordType.class);
        return convertFromRecordType(record);
    }

    /**
     * Constructs a MarcRecord from a RecordType that contains a from a marcxchange xml.
     *
     * @param record Marcxchange record.
     * @return A MarcRecord.
     */
    public static MarcRecord createFromMarcXChange(RecordType record) {
        return convertFromRecordType(record);
    }

    public static Document convertToMarcXChangeAsDocument(MarcRecord record) throws JAXBException, ParserConfigurationException {
        RecordType marcXhangeType = MarcXchangeFactory.createMarcXchangeFromMarc(record);
        ObjectFactory objectFactory = new ObjectFactory();
        JAXBElement<RecordType> jAXBElement = objectFactory.createRecord(marcXhangeType);

        Marshaller marshaller = getJAXBContext().createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_SCHEMA_LOCATION, "info:lc/xmlns/marcxchange-v1");

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);

        DocumentBuilder documentBuilder = dbf.newDocumentBuilder();
        Document document = documentBuilder.newDocument();
        marshaller.marshal(jAXBElement, document);
        return document;
    }

    /**
     * Constructs a MarcRecord from a RecordType.
     */
    private static MarcRecord convertFromRecordType(RecordType rt) {
        ArrayList<MarcField> mfl = new ArrayList<>();
        for (DataFieldType df : rt.getDatafield()) {
            mfl.add(convertFromDataField(df));
        }
        return new MarcRecord(mfl);
    }

    /**
     * Constructs a MarcField from a DataFieldType.
     * <p/>
     * The indicator of the field is generated from attributes ind1 - ind9
     * of DataFieldType.
     */
    private static MarcField convertFromDataField(DataFieldType df) {
        MarcField mf = new MarcField(df.getTag(), "");
        String indicator = "";
        if (df.getInd1() != null) {
            indicator += df.getInd1();
        }
        if (df.getInd2() != null) {
            indicator += df.getInd2();
        }
        if (df.getInd3() != null) {
            indicator += df.getInd3();
        }
        if (df.getInd4() != null) {
            indicator += df.getInd4();
        }
        if (df.getInd5() != null) {
            indicator += df.getInd5();
        }
        if (df.getInd6() != null) {
            indicator += df.getInd6();
        }
        if (df.getInd7() != null) {
            indicator += df.getInd7();
        }
        if (df.getInd8() != null) {
            indicator += df.getInd8();
        }
        if (df.getInd9() != null) {
            indicator += df.getInd9();
        }
        mf.setIndicator(indicator);

        ArrayList<MarcSubField> sfl = new ArrayList<>();
        for (SubfieldatafieldType sf : df.getSubfield()) {
            sfl.add(createFromSubfield(sf, df));
        }
        mf.setSubfields(sfl);
        return mf;
    }

    /**
     * Constructs a MarcSubField from a SubfieldatafieldType.
     */
    private static MarcSubField createFromSubfield(SubfieldatafieldType sf, DataFieldType df) {
        String name = sf.getCode();
        String val = sf.getValue();
        if (name.length() > 1) {
            throw new IllegalArgumentException("Subfield name cannot exceed one char. Field [" + df.getTag() + "], subfield name [" + name + "], subfield value [" + val + "]");
        }
        return new MarcSubField(name.trim(), val.trim());
    }
}
