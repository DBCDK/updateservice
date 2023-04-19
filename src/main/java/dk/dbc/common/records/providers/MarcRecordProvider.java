package dk.dbc.common.records.providers;

import dk.dbc.common.records.MarcRecord;

import java.io.IOException;

/**
 * Interface for providing one or more MarcRecords from a source.
 * <p/>
 * It extends Iterable so it is posible to use the interface like this:
 * <code>
 * MarcRecordProvider provider = ...
 * <p>
 * for( MarcRecord record : provider ) {
 * // Do some stoff with record
 * }
 * </code>
 * For this to work each implementation of this interface must override
 * <code>Iterator<MarcRecord> iterator()</code> to return an iterator that knows
 * out to iterate over the data structure in the implementation.
 */
public interface MarcRecordProvider extends Iterable<MarcRecord> {
    boolean hasMultibleRecords();

    void close() throws IOException;
}
