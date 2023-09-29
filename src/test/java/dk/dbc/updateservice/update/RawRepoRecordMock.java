package dk.dbc.updateservice.update;

import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;

import java.time.Instant;
import java.util.Set;

/**
 * @author DBC {@literal <dbc.dk>}
 */
public class RawRepoRecordMock implements Record {
    private RecordId id ;
    private byte[] content;
    private byte[] contentJson;
    private String mimeType;
    private Instant created;
    private Instant modified;
    private String trackingId;
    private boolean original;
    private boolean deleted;
    private boolean enriched;
    private Set<RecordId> references = null;

    public RawRepoRecordMock(String id, int library) {
        this.id = new RecordId(id, library);
        this.deleted = false;
        this.mimeType = "";
        this.content = new byte[0];
        this.created = Instant.now();
        this.modified = Instant.now();
        this.trackingId = "";
        this.original = true;
        this.enriched = false;
    }

    @Override
    public RecordId getId() {
        return id;
    }

    public void setId(RecordId id) {
        this.id = id;
    }

    @Override
    public byte[] getContent() {
        return content;
    }

    @Override
    public void setContent(byte[] content) {
        this.content = content;
    }

    @Override
    public byte[] getContentJson() {
        return contentJson;
    }

    @Override
    public void setContentJson(byte[] contentJson) {
        this.contentJson = contentJson;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    @Override
    public Instant getCreated() {
        return created;
    }

    @Override
    public void setCreated(Instant created) {
        this.created = created;
    }

    @Override
    public Instant getModified() {
        return modified;
    }

    @Override
    public void setModified(Instant modified) {
        this.modified = modified;
    }

    @Override
    public String getTrackingId() {
        return trackingId;
    }

    public void setTrackingId(String trackingId) {
        this.trackingId = trackingId;
    }

    @Override
    public boolean isOriginal() {
        return original;
    }

    public void setOriginal(boolean original) {
        this.original = original;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public boolean isEnriched() {
        return enriched;
    }

    public void setEnriched(boolean enriched) {
        this.enriched = enriched;
    }

    public String getEnrichmentTrail() {
        return "";
    }

    public Set<RecordId> getReferences() {
        return references;
    }

    public void setReferences(Set<RecordId> references) {
        this.references = references;
    }
}
