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
        this.created = null;
        this.modified = null;
        this.original = false;        
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
    public boolean hasContent() {
        return getContent() != null;
    }
     
    @Override
    public byte[] getContent() {
        return content;
    }

    @Override
    public void setContent( byte[] content ) {
        this.content = content;
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
    private Date created;
    private Date modified;
    private boolean original;
    private Set<RecordId> references; 
}
