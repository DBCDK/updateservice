//-----------------------------------------------------------------------------
package dk.dbc.updateservice.update;

//-----------------------------------------------------------------------------
import dk.dbc.iscrum.utils.logback.filters.BusinessLoggerFilter;
import dk.dbc.updateservice.ws.JNDIResources;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.annotation.Resource;
import javax.ejb.Stateless;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;

//-----------------------------------------------------------------------------
/**
 * EJB to send a mail has part of the double record checkings.
 */
@Stateless
public class DoubleRecordMailService {
    /**
     * Default constructor for the EJB container.
     */
    public DoubleRecordMailService() {
        this( null );
    }

    /**
     * Constructor with settings.
     * <p>
     *     Used by unit tests.
     * </p>
     *
     * @param settings Properties to send a mail.
     */
    public DoubleRecordMailService( Properties settings ) {
        this.settings = settings;
    }

    /**
     * Send the actual mail message.
     * <p>
     *     The body is formatted as plan text so any HTML tags will have
     *     no effect.
     * </p>
     * <p>
     *     Any exceptions is eaten and send to the log files.
     * </p>
     *
     * @param subject Subject of the mail.
     * @param body    Body of the message.
     */
    public void sendMessage( String subject, String body ) {
        logger.entry( subject, body );

        try {
            // Setup Mail server properties
            Properties properties = System.getProperties();
            properties.setProperty( MAIL_HOST_PROPERTY, settings.getProperty( JNDIResources.DOUBLE_RECORD_MAIL_HOST_KEY ) );
            properties.setProperty( MAIL_PORT_PROPERTY, settings.getProperty( JNDIResources.DOUBLE_RECORD_MAIL_PORT_KEY ) );


            if( settings.containsKey( JNDIResources.DOUBLE_RECORD_MAIL_USER_KEY ) ) {
                properties.setProperty( MAIL_USER_PROPERTY, settings.getProperty( JNDIResources.DOUBLE_RECORD_MAIL_USER_KEY ) );
            }

            if( settings.containsKey( JNDIResources.DOUBLE_RECORD_MAIL_PASSWORD_KEY ) ) {
                properties.setProperty( MAIL_PASSWORD_PROPERTY, settings.getProperty( JNDIResources.DOUBLE_RECORD_MAIL_PASSWORD_KEY ) );
            }

            // Create a new Session object for the mail message.
            Session session = Session.getInstance( properties );

            try {
                // Create a default MimeMessage object.
                MimeMessage message = new MimeMessage( session );

                // Set From: header field of the header.
                message.setFrom( new InternetAddress( settings.getProperty( JNDIResources.DOUBLE_RECORD_MAIL_FROM_KEY ) ) );

                // Set To: header field of the header.
                String receipientAddresses = settings.getProperty( JNDIResources.DOUBLE_RECORD_MAIL_RECIPIENT_KEY );
                for( String addr : receipientAddresses.split( ";" ) ) {
                    message.addRecipient( Message.RecipientType.TO, new InternetAddress( addr ) );
                }

                // Set Subject: header field
                message.setSubject( subject );

                // Now set the actual message
                message.setText( body );

                // Send message
                Transport.send( message );
                bizLogger.info( "Double Record Checker: Sent message with subject '{}' successfully.", subject );
            }
            catch( MessagingException ex ) {
                bizLogger.warn( "Double Record Checker: Unable to send mail message to {}: {}", settings.getProperty( JNDIResources.DOUBLE_RECORD_MAIL_RECIPIENT_KEY ), ex.getMessage() );
                bizLogger.warn( "Mail message: {}\n{}", subject, body );
                logger.warn( "Mail service error: ", ex );
            }
        }
        finally {
            logger.exit();
        }
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    /**
     * Logger instance to write entries to the log files.
     */
    private static XLogger logger = XLoggerFactory.getXLogger( DoubleRecordMailService.class );
    private final XLogger bizLogger = XLoggerFactory.getXLogger( BusinessLoggerFilter.LOGGER_NAME );

    private final String MAIL_HOST_PROPERTY = "mail.smtp.host";
    private final String MAIL_PORT_PROPERTY = "mail.smtp.port";
    private final String MAIL_USER_PROPERTY = "mail.user";
    private final String MAIL_PASSWORD_PROPERTY = "mail.password";

    /**
     * Resource to lookup the product name for authentication.
     */
    @Resource( lookup = JNDIResources.SETTINGS_NAME )
    private Properties settings;
}
