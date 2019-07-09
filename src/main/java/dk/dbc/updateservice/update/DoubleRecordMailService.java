/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;

import dk.dbc.updateservice.ws.JNDIResources;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

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

    private final String INSTANCE_NAME = "INSTANCE_NAME";

    private Properties settings = JNDIResources.getProperties();

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
            String adjustedSubject;

            if (System.getenv().containsKey(INSTANCE_NAME)) {
                adjustedSubject = System.getenv().get(INSTANCE_NAME) + ": " + subject;
            } else {
                adjustedSubject = subject;
            }

            // Setup Mail server properties
            Properties properties = System.getProperties();
            properties.setProperty(MAIL_HOST_PROPERTY, settings.getProperty(JNDIResources.DOUBLE_RECORD_MAIL_HOST));
            properties.setProperty(MAIL_PORT_PROPERTY, settings.getProperty(JNDIResources.DOUBLE_RECORD_MAIL_PORT));
            if (settings.containsKey(JNDIResources.DOUBLE_RECORD_MAIL_USER)) {
                properties.setProperty(MAIL_USER_PROPERTY, settings.getProperty(JNDIResources.DOUBLE_RECORD_MAIL_USER));
            }
            if (settings.containsKey(JNDIResources.DOUBLE_RECORD_MAIL_PASSWORD)) {
                properties.setProperty(MAIL_PASSWORD_PROPERTY, settings.getProperty(JNDIResources.DOUBLE_RECORD_MAIL_PASSWORD));
            }
            // Create a new Session object for the mail message.
            Session session = Session.getInstance(properties);
            try {
                MimeMessage message = new MimeMessage(session);
                message.setFrom(new InternetAddress(settings.getProperty(JNDIResources.DOUBLE_RECORD_MAIL_FROM)));
                String receipientAddresses = settings.getProperty(JNDIResources.DOUBLE_RECORD_MAIL_RECIPIENT);
                for (String addr : receipientAddresses.split(";")) {
                    message.addRecipient(Message.RecipientType.TO, new InternetAddress(addr));
                }
                message.setSubject(adjustedSubject);
                message.setText(body);
                Transport.send(message);
                logger.info("Double Record Checker: Sent message with subject '{}' successfully.", adjustedSubject);
            } catch (MessagingException ex) {
                logger.warn("Double Record Checker: Unable to send mail message to {}: {}", settings.getProperty(JNDIResources.DOUBLE_RECORD_MAIL_RECIPIENT), ex.getMessage());
                logger.warn("Mail message: {}\n{}", adjustedSubject, body);
                logger.error("Mail service error");
                logger.catching(XLogger.Level.ERROR, ex);
            }
        } finally {
            watch.stop();
            logger.exit();
        }
    }
}
