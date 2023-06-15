package dk.dbc.updateservice.utils;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

class DeferredLoggerTest {
    private static final DeferredLogger LOGGER = new DeferredLogger(DeferredLoggerTest.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    public void setup() {
        appender.start();
        LOGGER.logger.setLevel(Level.DEBUG);
        LOGGER.logger.addAppender(appender);
    }

    @AfterEach
    public void tearDown() {
        LOGGER.logger.detachAppender(appender);
    }

    @Test
    void testLoggingSimple() {
        LOGGER.use(log -> {
            log.trace("trace will not log");
            log.info("info will log");
            log.warn("warn will log");
            log.info("info will not log");
        });
        LOGGER.use(log -> log.info("test will not log"));
        assertThat("Two messages should be logged", 2, is(appender.list.size()));
        assertThat("All messages must contain \"will log\"", appender.list.stream().allMatch(e -> e.getMessage().contains("will log")));
    }

    @Test
    void testLoggingNestedWithReturn() {
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
        assertThat("Return value must be 0", 0, is(returnValue));
        assertThat("Four messages should be logged", 4, is(appender.list.size()));
        assertThat("All messages must contain \"will log\"", appender.list.stream().allMatch(e -> e.getMessage().contains("will log")));
    }

    @Test
    void testStacktrace() {
        int returnValue = LOGGER.call(l1 -> {
            try {
                return LOGGER.call(l2 -> {
                    throw new RuntimeException("test");
                });
            } catch (Exception e) {
                l1.warn("Got some exception", e);
                return 1;
            }
        });
        assertThat("No stacktrace should contain the logger class",
                appender.list.stream()
                        .map(ILoggingEvent::getThrowableProxy)
                        .map(IThrowableProxy::getStackTraceElementProxyArray)
                        .flatMap(Arrays::stream)
                        .map(StackTraceElementProxy::getStackTraceElement)
                        .map(StackTraceElement::getClassName)
                        .noneMatch(DeferredLogger.class.getName()::equals));
    }
}
