package dk.dbc.updateservice.utils;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A logger that defers statements until something has gone wrong.
 * Per default this logger will defer statements for trace, debug and info, until a warning or an error is logged,
 * upon which all buffered statements for the context will be logged.
 */
public class DeferredLogger {
    public final Set<Level> deferring;
    public final Logger logger;
    public final int eventLimit;
    private static final ThreadLocal<LinkedList<LoggingEvent>> deferredLogs = new ThreadLocal<>();

    public DeferredLogger(Class<?> clazz) {
        this(clazz, 100, Level.TRACE, Level.DEBUG, Level.INFO);
    }

    public DeferredLogger(Class<?> clazz, int eventLimit, Level... deferringLevels) {
        this.logger = (Logger) LoggerFactory.getLogger(clazz);
        this.eventLimit = eventLimit;
        deferring = Set.of(deferringLevels);
    }

    /**
     * Creates a logging context that returns a value
     */
    public <T> T call(Function<Context, T> c) {
        Context context = new Context(deferredLogs.get() == null);
        try {
            return c.apply(context);
        } finally {
            if (context.owner) deferredLogs.remove();
        }
    }

    public <T, E extends Exception> T callChecked(SpicyFunction<T, E> f) throws E {
        Context context = new Context(deferredLogs.get() == null);
        try {
            return f.apply(context);
        } finally {
            if (context.owner) deferredLogs.remove();
        }
    }

    public <R, E1 extends Exception, E2 extends Exception> R callChecked2(SpicyFunction2<R, E1, E2> f) throws E1, E2 {
        Context context = new Context(deferredLogs.get() == null);
        try {
            return f.apply(context);
        } finally {
            if (context.owner) deferredLogs.remove();
        }
    }

    public interface SpicyFunction<R, E extends Exception> {
        R apply (Context c) throws E;
    }

    public interface SpicyFunction2<R, E1 extends Exception, E2 extends Exception> {
        R apply(Context c) throws E1, E2;
    }

    /**
     * Creates a logging context that does not return anything
     */
    public void use(Consumer<Context> c) {
        call(ctx -> {
            c.accept(ctx);
            return null;
        });
    }

    /**
     * A logging context in which logging statements are deferred until a non-deferred statements is invoked
     */
    public class Context implements BasicLogger {
        private final boolean owner;

        public Context(boolean owner) {
            this.owner = owner;
            if(owner) deferredLogs.set(new LinkedList<>());
        }

        @Override
        public String getName() {
            return logger.getName();
        }

        @Override
        public boolean isEnabledFor(Level level) {
            return logger.isEnabledFor(level);
        }

        public void log(Level level, String msg, Object[] params, Throwable t) {
            if(!logger.isEnabledFor(level)) return;
            LoggingEvent le = new LoggingEvent(DeferredLogger.class.getName(), logger, level, msg, t, params);
            if(deferring.contains(level)) {
                LinkedList<LoggingEvent> events = deferredLogs.get();
                events.add(le);
                if(events.size() > eventLimit) events.removeFirst();
            } else {
                logger.callAppenders(le);
                logDeferred();
            }
        }

        private void logDeferred() {
            LinkedList<LoggingEvent> events = deferredLogs.get();
            events.forEach(logger::callAppenders);
            events.clear();
        }
    }
}
