package dk.dbc.updateservice.utils;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class DeferredLoggerTest {
    private static final DeferredLogger LOGGER = new DeferredLogger(DeferredLoggerTest.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @Before
    public void setup() {
        appender.start();
        LOGGER.logger.setLevel(Level.DEBUG);
        LOGGER.logger.addAppender(appender);
    }

    @After
    public void tearDown() {
        LOGGER.logger.detachAppender(appender);
    }

    @Test
    public void testLoggingSimple() {
        LOGGER.use(log -> {
            log.trace("trace will not log");
            log.info("info will log");
            log.warn("warn will log");
            log.info("info will not log");
        });
        LOGGER.use(log -> log.info("test will not log"));
        Assert.assertEquals("Two messages should be logged", 2, appender.list.size());
        Assert.assertTrue("All messages must contain \"will log\"", appender.list.stream().allMatch(e -> e.getMessage().contains("will log")));
    }

    @Test
    public void testLoggingNestedWithReturn() {
        int returnValue = LOGGER.call(l1 -> {
            l1.debug("debug will log");
            return LOGGER.call(l2 -> {
                l2.trace("trace will not log");
                l2.info("info will log");
                return LOGGER.call(l3 -> {
                    l3.error("error will log");
                    l3.warn("warn will log");
                    l3.info("info will not log");
                    return 0;
                });
            });
        });
        Assert.assertEquals("Return value must be 0", 0, returnValue);
        Assert.assertEquals("Four messages should be logged", 4, appender.list.size());
        Assert.assertTrue("All messages must contain \"will log\"", appender.list.stream().allMatch(e -> e.getMessage().contains("will log")));
    }

    @Test
    public void testStacktrace() {
        int returnValue = LOGGER.call(l1 -> {
            try {
                return LOGGER.call(l2 -> {
                    l2.trace("trace will not log");
                    l2.info("info will log");
                    return LOGGER.call(l3 -> {
                        l3.error("error will log");
                        l3.warn("warn will log");
                        l3.info("info will not log");
                        throw new RuntimeException("test");
                    });

                });
            } catch (Exception e) {
                l1.warn("Got some exception", e);
                return 1;
            }
        });
    }
}
