//-----------------------------------------------------------------------------
package dk.dbc.updateservice.update;

//-----------------------------------------------------------------------------

import dk.dbc.holdingsitems.HoldingsItemsDAO;
import dk.dbc.holdingsitems.HoldingsItemsException;
import dk.dbc.iscrum.records.MarcReader;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.updateservice.ws.JNDIResources;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.annotation.Resource;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;

//-----------------------------------------------------------------------------
/**
 * EJB to provide access to the HoldingsItems database.
 */
@Stateless
@TransactionAttribute( TransactionAttributeType.NOT_SUPPORTED )
public class HoldingsItems {
    //-------------------------------------------------------------------------
    //              Constructors
    //-------------------------------------------------------------------------

    public HoldingsItems() {
        this.dataSource = null;
    }

    public HoldingsItems( DataSource dataSource ) {
        this.dataSource = dataSource;
    }

    //-------------------------------------------------------------------------
    //              Business logic
    //-------------------------------------------------------------------------

    public Set<Integer> getAgenciesThatHasHoldingsFor( MarcRecord record ) throws UpdateException {
        logger.entry( record );

        Set<Integer> result = null;
        try {
            result = getAgenciesThatHasHoldingsFor( MarcReader.getRecordValue(record, "001", "a") );
            return result;
        }
        finally {
            logger.exit( result );
        }
    }

    public Set<Integer> getAgenciesThatHasHoldingsFor( String recordId ) throws UpdateException {
        logger.entry( recordId );

        Set<Integer> result = null;
        try( Connection conn = dataSource.getConnection() ) {
            HoldingsItemsDAO dao = createDAO( conn );

            result = dao.getAgenciesThatHasHoldingsFor( recordId );
            return result;
        }
        catch( SQLException | HoldingsItemsException ex ) {
            logger.error( ex.getMessage(), ex );
            throw new UpdateException( ex.getMessage(), ex );
        }
        finally {
            logger.exit( result );
        }
    }

    //-------------------------------------------------------------------------
    //              Helpers
    //-------------------------------------------------------------------------

    protected HoldingsItemsDAO createDAO( Connection conn ) throws HoldingsItemsException {
        return HoldingsItemsDAO.newInstance( conn );
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    /**
     * Logger instance to write entries to the log files.
     */
    private static XLogger logger = XLoggerFactory.getXLogger( HoldingsItems.class );

    /**
     * Injected DataSource to access the holdingitems database.
     */
    @Resource( lookup = JNDIResources.JDBC_HOLDINGITEMS_NAME )
    private DataSource dataSource;
}
