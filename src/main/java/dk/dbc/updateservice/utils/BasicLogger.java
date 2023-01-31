package dk.dbc.updateservice.utils;

import ch.qos.logback.classic.Level;
import org.slf4j.Logger;
import org.slf4j.Marker;

public interface BasicLogger extends Logger {

    void log(Level level, String msg, Object[] params, Throwable t);

    void log(Level level, String msg, Object[] params, Throwable t, boolean deferrable);

    boolean isEnabledFor(Level level);

    default void debugImmediately(String msg) {
        log(Level.DEBUG, msg, null, null, false);
    }

    default void debugImmediately(String msg, Object p) {
        log(Level.DEBUG, msg, new Object[]{p}, null, false);
    }

    default void debugImmediately(String msg, Object p, Object p1) {
        log(Level.DEBUG, msg, new Object[]{p, p1}, null, false);
    }

    default void debugImmediately(String msg, Object... objects) {
        log(Level.DEBUG, msg, objects, null, false);
    }

    default void debugImmediately(String msg, Throwable throwable) {
        log(Level.DEBUG, msg, null, throwable, false);
    }

    default void infoImmediately(String msg) {
        log(Level.INFO, msg, null, null, false);
    }

    default void infoImmediately(String msg, Object p) {
        log(Level.INFO, msg, new Object[]{p}, null, false);
    }

    default void infoImmediately(String msg, Object p, Object p1) {
        log(Level.INFO, msg, new Object[]{p, p1}, null, false);
    }

    default void infoImmediately(String msg, Object... objects) {
        log(Level.INFO, msg, objects, null, false);
    }

    default void infoImmediately(String msg, Throwable throwable) {
        log(Level.INFO, msg, null, throwable, false);
    }

    default void warnImmediately(String msg) {
        log(Level.WARN, msg, null, null, false);
    }

    default void warnImmediately(String msg, Object p) {
        log(Level.WARN, msg, new Object[]{p}, null, false);
    }

    default void warnImmediately(String msg, Object p, Object p1) {
        log(Level.WARN, msg, new Object[]{p, p1}, null, false);
    }

    default void warnImmediately(String msg, Object... objects) {
        log(Level.WARN, msg, objects, null, false);
    }

    default void warnImmediately(String msg, Throwable throwable) {
        log(Level.WARN, msg, null, throwable, false);
    }


    // Basic log interface
    @Override
    default void trace(String msg) {
        log(Level.TRACE, msg, null, null);
    }

    @Override
    default void trace(String msg, Object p) {
        log(Level.TRACE, msg, new Object[]{p}, null);
    }

    @Override
    default void trace(String msg, Object p, Object p1) {
        log(Level.TRACE, msg, new Object[]{p, p1}, null);
    }

    @Override
    default void trace(String msg, Object... objects) {
        log(Level.TRACE, msg, objects, null);
    }

    @Override
    default void trace(String msg, Throwable throwable) {
        log(Level.TRACE, msg, null, throwable);
    }

    @Override
    default void debug(String msg) {
        log(Level.DEBUG, msg, null, null);
    }

    @Override
    default void debug(String msg, Object p) {
        log(Level.DEBUG, msg, new Object[]{p}, null);
    }

    @Override
    default void debug(String msg, Object p, Object p1) {
        log(Level.DEBUG, msg, new Object[]{p, p1}, null);
    }

    @Override
    default void debug(String msg, Object... objects) {
        log(Level.DEBUG, msg, objects, null);
    }

    @Override
    default void debug(String msg, Throwable throwable) {
        log(Level.DEBUG, msg, null, throwable);
    }

    @Override
    default void info(String msg) {
        log(Level.INFO, msg, null, null);
    }

    @Override
    default void info(String msg, Object p) {
        log(Level.INFO, msg, new Object[]{p}, null);
    }

    @Override
    default void info(String msg, Object p, Object p1) {
        log(Level.INFO, msg, new Object[]{p, p1}, null);
    }

    @Override
    default void info(String msg, Object... objects) {
        log(Level.INFO, msg, objects, null);
    }

    @Override
    default void info(String msg, Throwable throwable) {
        log(Level.INFO, msg, null, throwable);
    }

    @Override
    default void warn(String msg) {
        log(Level.WARN, msg, null, null);
    }

    @Override
    default void warn(String msg, Object p) {
        log(Level.WARN, msg, new Object[]{p}, null);
    }

    @Override
    default void warn(String msg, Object p, Object p1) {
        log(Level.WARN, msg, new Object[]{p, p1}, null);
    }

    @Override
    default void warn(String msg, Object... objects) {
        log(Level.WARN, msg, objects, null);
    }

    @Override
    default void warn(String msg, Throwable throwable) {
        log(Level.WARN, msg, null, throwable);
    }

    @Override
    default void error(String msg) {
        log(Level.ERROR, msg, null, null);
    }

    @Override
    default void error(String msg, Object p) {
        log(Level.ERROR, msg, new Object[]{p}, null);
    }

    @Override
    default void error(String msg, Object p, Object p1) {
        log(Level.ERROR, msg, new Object[]{p, p1}, null);
    }

    @Override
    default void error(String msg, Object... objects) {
        log(Level.ERROR, msg, objects, null);
    }

    @Override
    default void error(String msg, Throwable throwable) {
        log(Level.ERROR, msg, null, throwable);
    }


    @Override
    default boolean isTraceEnabled() {
        return isEnabledFor(Level.TRACE);
    }

    @Override
    default boolean isDebugEnabled() {
        return isEnabledFor(Level.DEBUG);
    }

    @Override
    default boolean isInfoEnabled() {
        return isEnabledFor(Level.INFO);
    }

    @Override
    default boolean isWarnEnabled() {
        return isEnabledFor(Level.WARN);
    }

    @Override
    default boolean isErrorEnabled() {
        return isEnabledFor(Level.ERROR);
    }

    // Garbage section
    @Override
    default boolean isTraceEnabled(Marker marker) {
        return false;
    }

    @Override
    default void trace(Marker marker, String msg) {}

    @Override
    default void trace(Marker marker, String msg, Object o) {}

    @Override
    default void trace(Marker marker, String msg, Object o, Object o1) {}

    @Override
    default void trace(Marker marker, String msg, Object... objects) {}

    @Override
    default void trace(Marker marker, String msg, Throwable throwable) {}

    @Override
    default boolean isDebugEnabled(Marker marker) {
        return false;
    }

    @Override
    default void debug(Marker marker, String msg) {}

    @Override
    default void debug(Marker marker, String msg, Object o) {}

    @Override
    default void debug(Marker marker, String msg, Object o, Object o1) {}

    @Override
    default void debug(Marker marker, String msg, Object... objects) {}

    @Override
    default void debug(Marker marker, String msg, Throwable throwable) {}

    @Override
    default boolean isInfoEnabled(Marker marker) {
        return false;
    }

    @Override
    default void info(Marker marker, String msg) {}

    @Override
    default void info(Marker marker, String msg, Object o) {}

    @Override
    default void info(Marker marker, String msg, Object o, Object o1) {}

    @Override
    default void info(Marker marker, String msg, Object... objects) {}

    @Override
    default void info(Marker marker, String msg, Throwable throwable) {}

    @Override
    default boolean isWarnEnabled(Marker marker) {
        return false;
    }

    @Override
    default void warn(Marker marker, String msg) {}

    @Override
    default void warn(Marker marker, String msg, Object o) {}

    @Override
    default void warn(Marker marker, String msg, Object o, Object o1) {}

    @Override
    default void warn(Marker marker, String msg, Object... objects) {}

    @Override
    default void warn(Marker marker, String msg, Throwable throwable) {}

    @Override
    default boolean isErrorEnabled(Marker marker) {
        return false;
    }

    @Override
    default void error(Marker marker, String msg) {}

    @Override
    default void error(Marker marker, String msg, Object o) {}

    @Override
    default void error(Marker marker, String msg, Object o, Object o1) {}

    @Override
    default void error(Marker marker, String msg, Object... objects) {}

    @Override
    default void error(Marker marker, String msg, Throwable throwable) {}
}
