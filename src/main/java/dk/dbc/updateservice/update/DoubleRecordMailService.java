package dk.dbc.updateservice.update;

import dk.dbc.updateservice.ws.JNDIResources;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
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

/**
 * EJB to send a mail has part of the double record checkings.
 */
@Stateless
public class DoubleRecordMailService {
    private static XLogger logger = XLoggerFactory.getXLogger(DoubleRecordMailService.class);

    private final String MAIL_HOST_PROPERTY = "mail.smtp.host";
    private final String MAIL_PORT_PROPERTY = "mail.smtp.port";
    private final String MAIL_USER_PROPERTY = "mail.user";
    private final String MAIL_PASSWORD_PROPERTY = "mail.password";

    /**
     * Resource to lookup the product name for authentication.
     */
    @Resource(lookup = JNDIResources.JNDI_NAME_UPDATESERVICE)
    private Properties settings;

    /**
     * Default constructor for the EJB container.
     */
    public DoubleRecordMailService() {
        this(null);
    }

    /**
     * Constructor with settings.
     * <p>
     * Used by unit tests.
     * </p>
     *
     * @param settings Properties to send a mail.
     */
    public DoubleRecordMailService(Properties settings) {
        this.settings = settings;
    }

    /**
     * Send the actual mail message.
     * <p>
     * The body is formatted as plan text so any HTML tags will have
     * no effect.
     * </p>
     * <p>
     * Any exceptions is eaten and send to the log files.
     * </p>
     *
     * @param subject Subject of the mail.
     * @param body    Body of the message.
     */
    public void sendMessage(String subject, String body) {
        logger.entry(subject, body);
        StopWatch watch = new Log4JStopWatch("service.mail");
        try {
            // Setup Mail server properties
            Properties properties = System.getProperties();
            properties.setProperty(MAIL_HOST_PROPERTY, settings.getProperty(JNDIResources.DOUBLE_RECORD_MAIL_HOST_KEY));
            properties.setProperty(MAIL_PORT_PROPERTY, settings.getProperty(JNDIResources.DOUBLE_RECORD_MAIL_PORT_KEY));
            if (settings.containsKey(JNDIResources.DOUBLE_RECORD_MAIL_USER_KEY)) {
                properties.setProperty(MAIL_USER_PROPERTY, settings.getProperty(JNDIResources.DOUBLE_RECORD_MAIL_USER_KEY));
            }
            if (settings.containsKey(JNDIResources.DOUBLE_RECORD_MAIL_PASSWORD_KEY)) {
                properties.setProperty(MAIL_PASSWORD_PROPERTY, settings.getProperty(JNDIResources.DOUBLE_RECORD_MAIL_PASSWORD_KEY));
            }
            // Create a new Session object for the mail message.
            Session session = Session.getInstance(properties);
            try {
                MimeMessage message = new MimeMessage(session);
                message.setFrom(new InternetAddress(settings.getProperty(JNDIResources.DOUBLE_RECORD_MAIL_FROM_KEY)));
                String receipientAddresses = settings.getProperty(JNDIResources.DOUBLE_RECORD_MAIL_RECIPIENT_KEY);
                for (String addr : receipientAddresses.split(";")) {
                    message.addRecipient(Message.RecipientType.TO, new InternetAddress(addr));
                }
                message.setSubject(subject);
                message.setText(body);
                Transport.send(message);
                logger.info("Double Record Checker: Sent message with subject '{}' successfully.", subject);
            } catch (MessagingException ex) {
                logger.warn("Double Record Checker: Unable to send mail message to {}: {}", settings.getProperty(JNDIResources.DOUBLE_RECORD_MAIL_RECIPIENT_KEY), ex.getMessage());
                logger.warn("Mail message: {}\n{}", subject, body);
                logger.error("Mail service error");
                logger.catching(XLogger.Level.ERROR, ex);
            }
        } finally {
            watch.stop();
            logger.exit();
        }
    }
}
