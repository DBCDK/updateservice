package dk.dbc.updateservice.client;

import org.junit.Test;

import javax.xml.transform.stream.StreamSource;

import java.io.StringReader;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;

public class BibliographicRecordExtraDataDecoderTest {
    
    @Test
    public void fromXmlProvider() {
        String o = "<?xml version=\"1.0\" encoding=\"UTF-16\"?><cat:updateRecordExtraData xmlns:cat=\"http://oss.dbc.dk/ns/catalogingUpdate\">\n<providerName>bulk-broend</providerName>\n</cat:updateRecordExtraData>";

        final BibliographicRecordExtraData expected = new BibliographicRecordExtraData();
        expected.setProviderName("bulk-broend");

        assertThat(BibliographicRecordExtraDataDecoder.fromXml(new StreamSource(new StringReader(o))), equalTo(expected));
    }

    @Test
    public void fromXmlPriority() {
        String o = "<?xml version=\"1.0\" encoding=\"UTF-16\"?><cat:updateRecordExtraData xmlns:cat=\"http://oss.dbc.dk/ns/catalogingUpdate\">\n<priority>42</priority>\n</cat:updateRecordExtraData>";

        final BibliographicRecordExtraData expected = new BibliographicRecordExtraData();
        expected.setPriority(42);

        assertThat(BibliographicRecordExtraDataDecoder.fromXml(new StreamSource(new StringReader(o))), equalTo(expected));
    }

    @Test
    public void fromXmlProviderAndPriority() {
        String o = "<?xml version=\"1.0\" encoding=\"UTF-16\"?><cat:updateRecordExtraData xmlns:cat=\"http://oss.dbc.dk/ns/catalogingUpdate\">\n<providerName>bulk-broend</providerName>\n<priority>42</priority>\n</cat:updateRecordExtraData>";

        final BibliographicRecordExtraData expected = new BibliographicRecordExtraData();
        expected.setProviderName("bulk-broend");
        expected.setPriority(42);

        assertThat(BibliographicRecordExtraDataDecoder.fromXml(new StreamSource(new StringReader(o))), equalTo(expected));
    }

    @Test
    public void fromXmlNoOptions() {
        String o = "<?xml version=\"1.0\" encoding=\"UTF-16\"?><cat:updateRecordExtraData xmlns:cat=\"http://oss.dbc.dk/ns/catalogingUpdate\">\n</cat:updateRecordExtraData>";

        final BibliographicRecordExtraData expected = new BibliographicRecordExtraData();

        assertThat(BibliographicRecordExtraDataDecoder.fromXml(new StreamSource(new StringReader(o))), equalTo(expected));
    }

}
