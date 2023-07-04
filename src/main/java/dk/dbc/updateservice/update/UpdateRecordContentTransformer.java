package dk.dbc.updateservice.update;

import dk.dbc.marc.binding.MarcRecord;
import dk.dbc.marc.reader.DanMarc2LineFormatReader;
import dk.dbc.marc.reader.MarcReaderException;
import dk.dbc.marc.reader.MarcXchangeV1Reader;
import dk.dbc.marc.writer.JsonLineWriter;
import dk.dbc.marc.writer.MarcWriterException;
import dk.dbc.marc.writer.MarcXchangeV1Writer;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

public class UpdateRecordContentTransformer {

    public static MarcRecord decodeRecord(byte[] content) throws UpdateException {
        final ByteArrayInputStream buf = new ByteArrayInputStream(content);
        final MarcXchangeV1Reader reader;
        try {
            reader = new MarcXchangeV1Reader(buf, StandardCharsets.UTF_8);

            return reader.read();
        } catch (MarcReaderException e) {
            throw new UpdateException(e.getMessage(), e);
        }
    }

    public static byte[] encodeRecord(MarcRecord marcRecord) {
        final MarcXchangeV1Writer marcXchangeV1Writer = new MarcXchangeV1Writer();
        return marcXchangeV1Writer.write(marcRecord, StandardCharsets.UTF_8);
    }

    public static byte[] encodeRecordToJson(MarcRecord marcRecord) throws UpdateException {
        final JsonLineWriter writer = new JsonLineWriter();
        try {
            return writer.write(marcRecord, StandardCharsets.UTF_8);
        } catch (MarcWriterException e) {
            throw new UpdateException(e.getMessage(), e);
        }
    }

    public static MarcRecord readRecordFromString(String line) throws UpdateException {
        final ByteArrayInputStream buf = new ByteArrayInputStream(line.getBytes());

        final DanMarc2LineFormatReader reader = new DanMarc2LineFormatReader(buf, StandardCharsets.UTF_8);

        try {
            return reader.read();
        } catch (MarcReaderException e) {
            throw new UpdateException(e.getMessage(), e);
        }
    }

}
