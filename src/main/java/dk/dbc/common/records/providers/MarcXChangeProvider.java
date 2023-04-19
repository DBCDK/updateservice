package dk.dbc.common.records.providers;

import dk.dbc.common.records.MarcConverter;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.marcxchange.CollectionType;
import dk.dbc.common.records.marcxchange.RecordType;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.xml.bind.JAXB;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class MarcXChangeProvider implements MarcRecordProvider {
    private static final XLogger logger = XLoggerFactory.getXLogger(MarcXChangeProvider.class);

    private final List<RecordType> records;

    /**
     * Constructs a Marcxchange provider to provide all records from a file.
     *
     * @param file The file to read the records from.
     * @throws IOException Thorwed if the file does not exist or can not be read.
     */
    public MarcXChangeProvider(File file) throws IOException {
        this.records = loadRecords(file);
    }

    @Override
    public boolean hasMultibleRecords() {
        return this.records.size() > 1;
    }

    @Override
    public void close() throws IOException {
    }

    /**
     * Returns an iterator over elements of type {@code T}.
     *
     * @return an Iterator.
     */
    @Override
    public Iterator<MarcRecord> iterator() {
        return new MarcXChangeProviderIterator(this.records);
    }

    /**
     * @param file ddd
     * @return A list of records.
     * @brief Loads records from a xml string.
     * <p/>
     * Each record is not converted to MarcRecord by this method. This is first done
     * when the record is returned by iterator().
     */
    private static List<RecordType> loadRecords(File file) {
        // Try to unmarshal a collection
        CollectionType collection = JAXB.unmarshal(file, CollectionType.class);
        if (!collection.getRecord().isEmpty()) {
            return collection.getRecord();
        }

        // Try to unmarshal a single record
        RecordType record = JAXB.unmarshal(file, RecordType.class);
        ArrayList<RecordType> records = new ArrayList<>();
        records.add(record);

        return records;
    }

    private static class MarcXChangeProviderIterator implements Iterator<MarcRecord> {
        int currentIndex;
        private final List<RecordType> xmlRecords;

        public MarcXChangeProviderIterator(List<RecordType> xmlRecords) {
            this.currentIndex = -1;
            this.xmlRecords = xmlRecords;
        }

        /**
         * Returns {@code true} if the iteration has more elements. (In other words, returns {@code true} if {@link #next}
         * would return an element rather than throwing an exception.)
         *
         * @return {@code true} if the iteration has more elements
         */
        @Override
        public boolean hasNext() {
            logger.trace("currentIndex: {}", currentIndex);
            logger.trace("xmlRecords: {}", xmlRecords);

            if (xmlRecords != null) {
                return currentIndex + 1 < xmlRecords.size();
            }

            return false;
        }

        /**
         * Returns the next element in the iteration.
         *
         * @return the next element in the iteration
         * @throws NoSuchElementException if the iteration has no more elements
         */
        @Override
        public MarcRecord next() {
            if (xmlRecords != null) {
                logger.trace("xmlRecords: {}", xmlRecords.size());
                currentIndex++;
                if (currentIndex < xmlRecords.size()) {
                    logger.trace("Item: {}", xmlRecords.get(currentIndex));
                    return MarcConverter.createFromMarcXChange(xmlRecords.get(currentIndex));
                }
            }

            throw new NoSuchElementException("No more records in the MarcXChangeProvider");
        }

        /**
         * Removes from the underlying collection the last element returned by this iterator (optional operation).  This
         * method can be called only once per call to {@link #next}.  The behavior of an iterator is unspecified if the
         * underlying collection is modified while the iteration is in progress in any way other than by calling this
         * method.
         *
         * @throws UnsupportedOperationException if the {@code remove} operation is not supported by this iterator
         * @throws IllegalStateException         if the {@code next} method has not yet been called, or the {@code remove}
         *                                       method has already been called after the last call to the {@code next}
         *                                       method
         */
        @Override
        public void remove() {
            throw new UnsupportedOperationException("remove is not supported by marcxchange iteration");
        }
    }

}
