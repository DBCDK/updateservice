package dk.dbc.updateservice.utils;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A logger that defers statements until something has gone wrong.
 * Per default this logger will defer statements for trace, debug and info, until a warning or an error is logged,
 * upon which all buffered statements for the context will be logged.
 * Since this logging context would pollute stacktraces heavily, it filters itself out of the logged stacktraces
 */
public class DeferredLogger {
    public static final boolean DEFER_ENABLED = Boolean.parseBoolean(System.getProperty("DEFER_ENABLED", "true"));
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

    /**
     * Creates a logging context that returns a value for a block with a checked exception
     */
    public <T, E extends Exception> T callChecked(SpicyFunction<T, E> f) throws E {
        Context context = new Context(deferredLogs.get() == null);
        try {
            return f.apply(context);
        } finally {
            if (context.owner) deferredLogs.remove();
        }
    }

    /**
     * Creates a logging context that returns a value for a block with two checked exceptions
     * Please notice that currently Java cannot infer the exceptions correctly, so they must be declared explicitly like so:
     * <Void, Exception1, Exception2>callChecked2(log -> {some crazy code})
     */
    public <R, E1 extends Exception, E2 extends Exception> R callChecked2(SpicyFunction2<R, E1, E2> f) throws E1, E2 {
        Context context = new Context(deferredLogs.get() == null);
        try {
            return f.apply(context);
        } finally {
            if (context.owner) deferredLogs.remove();
        }
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

    public static boolean traceFilter(StackTraceElement stackTraceElement) {
        return !DeferredLogger.class.getName().equals(stackTraceElement.getClassName());
    }

    private StackTraceElement[] cleanStackTrace(StackTraceElement[] stackTraceElements) {
        return Arrays.stream(stackTraceElements).filter(DeferredLogger::traceFilter).toArray(StackTraceElement[]::new);
    }

    /**
     * A Function with a checked exception
     */
    public interface SpicyFunction<R, E extends Exception> {
        R apply (Context c) throws E;
    }

    /**
     * A Function with two checked exceptions
     */
    public interface SpicyFunction2<R, E1 extends Exception, E2 extends Exception> {
        R apply(Context c) throws E1, E2;
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

        @Override
        public void log(Level level, String msg, Object[] params, Throwable t) {
            log(level, msg, params, t, true);
        }

        @Override
        public void log(Level level, String msg, Object[] params, Throwable t, boolean deferrable) {
            if(!logger.isEnabledFor(level)) return;
            if(t != null) {
                t.setStackTrace(cleanStackTrace(t.getStackTrace()));
            }
            LoggingEvent le = new FilteredLoggingEvent(DeferredLogger.class.getName(), logger, level, msg, t, params);
            if(!DEFER_ENABLED) {
                logger.callAppenders(le);
                return;
            }
            boolean deferredLevel = deferring.contains(level);
            if(deferrable && deferredLevel) {
                LinkedList<LoggingEvent> events = deferredLogs.get();
                events.add(le);
                if(events.size() > eventLimit) events.removeFirst();
            } else {
                if(!deferredLevel) logDeferred();
                logger.callAppenders(le);
            }
        }


        private void logDeferred() {
            LinkedList<LoggingEvent> events = deferredLogs.get();
            events.forEach(logger::callAppenders);
            events.clear();
        }
    }

    public class FilteredLoggingEvent extends LoggingEvent {
        public FilteredLoggingEvent(String name, Logger logger, Level level, String msg, Throwable t, Object[] params) {
            super(name, logger, level, msg, t, params);
        }

        @Override
        public StackTraceElement[] getCallerData() {
            return cleanStackTrace(super.getCallerData());
        }
    }
}
