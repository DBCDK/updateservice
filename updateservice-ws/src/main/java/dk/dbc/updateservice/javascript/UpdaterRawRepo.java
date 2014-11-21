//-----------------------------------------------------------------------------
package dk.dbc.updateservice.javascript;

//-----------------------------------------------------------------------------
import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.ws.JNDIResources;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.rawrepo.RawRepoDAO;
import dk.dbc.rawrepo.RawRepoException;
import dk.dbc.rawrepo.Record;
import dk.dbc.updateservice.update.Updater;

//-----------------------------------------------------------------------------
/**
 * This class exports a RawRepo instance to the JavaScript environment.
 * 
 * @author stp
 */
public class UpdaterRawRepo {
	/**
	 * Fetch a record from the rawrepo.
	 * 
	 * If the record does not exist, it is created.
	 * 
	 * @param recordId  The record id from 001a to identify the record.
	 * @param libraryNo The library no from 001b to identify the record.
	 * 
	 * @return The record.
	 * 
	 * @throws NamingException 
	 * @throws SQLException 
	 * @throws RawRepoException 
	 * @throws UnsupportedEncodingException 
	 * 
	 */
	public static MarcRecord fetchRecord( String recordId, String libraryNo ) throws SQLException, NamingException, RawRepoException, UnsupportedEncodingException {
		logger.entry( recordId, libraryNo );
		
		MarcRecord result = null;
		try( Connection con = getConnection() ) {			
			RawRepoDAO rawRepoDAO = RawRepoDAO.newInstance(con);

			Record record = rawRepoDAO.fetchRecord( recordId, Integer.valueOf( libraryNo ) );
			if( record.getContent() == null ) {
				result = new MarcRecord();
			}
			else {
				result = new Updater().decodeRecord( record.getContent() );
			}
			
			return result;
		}
		finally {
			logger.exit( result );
		}
	}

	/**
	 * Checks if a record exists in the rawrepo.
	 * 
	 * @param recordId  The record id from 001a to identify the record.
	 * @param libraryNo The library no from 001b to identify the record.
	 * 
	 * @return <code>true</code> if the record exists, <code>false</code> 
	 * 		   otherwise.
	 * 
	 * @throws NamingException 
	 * @throws SQLException 
	 * @throws RawRepoException 
	 */
	public static Boolean recordExists( String recordId, String libraryNo ) throws SQLException, NamingException, RawRepoException {
		logger.entry( recordId, libraryNo );
		boolean result = false;
		
		try( Connection con = getConnection() ) {			
			RawRepoDAO rawRepoDAO = RawRepoDAO.newInstance(con);
			
			result = rawRepoDAO.recordExists( recordId, Integer.valueOf( libraryNo ) );
			
			return result;
		}
		finally {
			logger.exit( result );
		}
	}

	public static List<MarcRecord> getRelationsChildren( String recordId, String libraryNo ) throws SQLException, NamingException, RawRepoException, UnsupportedEncodingException {
		logger.entry( recordId, libraryNo );
		List<MarcRecord> result = null;

		try( Connection con = getConnection() ) {
			result = new ArrayList<>();

			RawRepoDAO rawRepoDAO = RawRepoDAO.newInstance(con);
			Set<RecordId> records = rawRepoDAO.getRelationsChildren( new RecordId( recordId, Integer.valueOf( libraryNo ) ) );
			Iterator<RecordId> iterator = records.iterator();
			while( iterator.hasNext() ) {
				RecordId rawRepoRecordId = iterator.next();
				result.add( fetchRecord( rawRepoRecordId.getBibliographicRecordId(), String.valueOf(rawRepoRecordId.getAgencyId())) );
			}

			return result;
		}
		finally {
			logger.exit( result );
		}
	}

	/**
	 * Lookup a sql connection for the rawrepo database from the Java EE container.
	 * 
	 * Remember to close the connection, when you are done using it.
	 * 
	 * @return The SQL connection.
	 * 
	 * @throws NamingException Throwned if the datasource can not be looked up in 
	 * 						   the container.
	 * @throws SQLException	   Throwned if the datasource can not open a connection.
	 */
	private static Connection getConnection() throws NamingException, SQLException {
		InitialContext ctx = new InitialContext();
		DataSource ds = (DataSource) ctx.lookup( JNDIResources.JDBC_RAW_REPO_NAME );
		
		return ds.getConnection();
	}

	private static final XLogger logger = XLoggerFactory.getXLogger( UpdaterRawRepo.class );

}
