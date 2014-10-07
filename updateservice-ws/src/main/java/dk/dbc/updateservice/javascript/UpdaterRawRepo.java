//-----------------------------------------------------------------------------
package dk.dbc.updateservice.javascript;

//-----------------------------------------------------------------------------
import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.SQLException;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

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
	private static final XLogger logger = XLoggerFactory.getXLogger( UpdaterRawRepo.class );

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
	 * @throws UpdaterRawRepoException In case of errors.
	 */
	public static MarcRecord fetchRecord( String recordId, int libraryNo ) throws UpdaterRawRepoException {
		logger.entry( recordId, libraryNo );
		
		MarcRecord result = null;
		try( Connection con = getConnection() ) {			
			RawRepoDAO rawRepoDAO = RawRepoDAO.newInstance( con );

			Record record = rawRepoDAO.fetchRecord( recordId, libraryNo );
			if( !record.hasContent() ) {
				result = new MarcRecord();
			}
			else {
				result = new Updater().decodeRecord( record.getContent() );
			}
			
			return result;
		}
		catch( NamingException ex ) {
			String msg = String.format( "Unable to lookup raw-repo database: %s", Updater.JNDI_JDBC_RAW_REPO_NAME );
			logger.error( msg, ex );			
			throw new UpdaterRawRepoException( msg, ex );
		}
		catch( SQLException ex ) {
			String msg = String.format( "Unable to get connection from datasource: %s", Updater.JNDI_JDBC_RAW_REPO_NAME ); 
			logger.error( msg, ex );
			throw new UpdaterRawRepoException( msg, ex );
		} 
		catch( RawRepoException ex ) {
			String msg = String.format( "Rawrepo error: %s", ex.getMessage() ); 
			logger.error( msg, ex );
			throw new UpdaterRawRepoException( msg, ex );
		} 
		catch( UnsupportedEncodingException ex ) {
			String msg = String.format( "The record [%s:%s] can not be readed as marcxchange", recordId, libraryNo ); 
			logger.error( msg, ex );
			throw new UpdaterRawRepoException( msg, ex );
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
	 * @throws UpdaterRawRepoException If an error occurred.
	 */
	public static boolean recordExists( String recordId, int libraryNo ) throws UpdaterRawRepoException {
		logger.entry( recordId, libraryNo );
		boolean result = false;
		
		try( Connection con = getConnection() ) {			
			RawRepoDAO rawRepoDAO = RawRepoDAO.newInstance( con );
			
			result = rawRepoDAO.recordExists( recordId, libraryNo );			
			
			return result;
		}
		catch( NamingException ex ) {
			String msg = String.format( "Unable to lookup raw-repo database: %s", Updater.JNDI_JDBC_RAW_REPO_NAME );
			logger.error( msg, ex );			
			throw new UpdaterRawRepoException( msg, ex );
		}
		catch( SQLException ex ) {
			String msg = String.format( "Unable to get connection from datasource: %s", Updater.JNDI_JDBC_RAW_REPO_NAME ); 
			logger.error( msg, ex );
			throw new UpdaterRawRepoException( msg, ex );
		} 
		catch( RawRepoException ex ) {
			String msg = String.format( "Rawrepo error: %s", ex.getMessage() ); 
			logger.error( msg, ex );
			throw new UpdaterRawRepoException( msg, ex );
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
		DataSource ds = (DataSource) ctx.lookup( Updater.JNDI_JDBC_RAW_REPO_NAME );
		
		return ds.getConnection();
	}
}
