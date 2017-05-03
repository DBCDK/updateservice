/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;

import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;

import java.util.Date;
import java.util.Set;

/**
 * @author stp
 */
public class RawRepoRecordMock implements Record {
    private RecordId id = null;
    private byte[] content = null;
    private String mimeType = null;
    private Date created = null;
    private Date modified = null;
    private String trackingId = null;
    private boolean original = false;
    private boolean deleted = false;
    private boolean enriched = false;
    private Set<RecordId> references = null;

    public RawRepoRecordMock(String id, int library) {
        this.id = new RecordId(id, library);
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

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    @Override
    public Date getCreated() {
        return created;
    }

    @Override
    public void setCreated(Date created) {
        this.created = created;
    }

    @Override
    public Date getModified() {
        return modified;
    }

    @Override
    public void setModified(Date modified) {
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
