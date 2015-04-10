//-----------------------------------------------------------------------------
package dk.dbc.updateservice.update;

//-----------------------------------------------------------------------------
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;

import java.util.Date;
import java.util.Set;

//-----------------------------------------------------------------------------
/**
 *
 * @author stp
 */
public class RawRepoRecordMock implements Record {
    //-------------------------------------------------------------------------
    //              Constructors
    //-------------------------------------------------------------------------
    
    public RawRepoRecordMock() {
        this.id = null;
        this.content = null;
        this.mimeType = null;
        this.created = null;
        this.modified = null;
        this.original = false;
        this.deleted = false;
        this.enriched = false;
        this.references = null;
    }

    public RawRepoRecordMock( String id, int library ) {
        this();
        this.id = new RecordId( id, library );
    }

    //-------------------------------------------------------------------------
    //              Properties
    //-------------------------------------------------------------------------
    @Override
    public RecordId getId() {
        return id;
    }

    public void setId( RecordId id ) {
        this.id = id;
    }
     
    @Override
    public byte[] getContent() {
        return content;
    }

    @Override
    public void setContent( byte[] content ) {
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
    public void setCreated( Date created ) {
        this.created = created;
    }

    @Override
    public Date getModified() {
        return modified;
    }

    @Override
    public void setModified( Date modified ) {
        this.modified = modified;
    }

    @Override
    public boolean isOriginal() {
        return original;
    }

    public void setOriginal( boolean original ) {
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

    public void setReferences( Set<RecordId> references ) {
        this.references = references;
    }
        
    //-------------------------------------------------------------------------
    //              Attributes
    //-------------------------------------------------------------------------
    
    private RecordId id;
    private byte[] content;
    private String mimeType;
    private Date created;
    private Date modified;
    private boolean original;
    private boolean deleted;
    private boolean enriched;
    private Set<RecordId> references;
}
