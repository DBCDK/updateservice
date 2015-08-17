//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

//-----------------------------------------------------------------------------

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.utils.logback.filters.BusinessLoggerFilter;
import dk.dbc.updateservice.update.RawRepo;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

/**
 * Abstract class to contain common members for actions that works with records
 * in the rawrepo.
 */
public abstract class AbstractRawRepoAction extends AbstractAction {
    public AbstractRawRepoAction( String name ) {
        this( name, null, null );
    }

    public AbstractRawRepoAction( String name, RawRepo rawRepo, MarcRecord record ) {
        super( name );

        this.rawRepo = rawRepo;
        this.record = record;
    }

    public RawRepo getRawRepo() {
        return rawRepo;
    }

    public void setRawRepo( RawRepo rawRepo ) {
        this.rawRepo = rawRepo;
    }

    public MarcRecord getRecord() {
        return record;
    }

    public void setRecord( MarcRecord record ) {
        this.record = record;
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    /**
     * RawRepo EJB to write records to the RawRepo.
     */
    protected RawRepo rawRepo;

    /**
     * Record to be updated.
     */
    protected MarcRecord record;
}
