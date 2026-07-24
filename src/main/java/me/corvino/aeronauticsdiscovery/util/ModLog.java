package me.corvino.aeronauticsdiscovery.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ModLog {
    private static final Logger LOG = LoggerFactory.getLogger("aeronauticsdiscovery");

    public static void info(LogCategory category, String msg) {
        if (category.isEnabled()) LOG.info("[" + category.name() + "] " + msg);
    }

    public static void info(LogCategory category, String msg, Object arg1) {
        if (category.isEnabled()) LOG.info("[" + category.name() + "] " + msg, arg1);
    }

    public static void info(LogCategory category, String msg, Object arg1, Object arg2) {
        if (category.isEnabled()) LOG.info("[" + category.name() + "] " + msg, arg1, arg2);
    }

    public static void info(LogCategory category, String msg, Object... args) {
        if (category.isEnabled()) LOG.info("[" + category.name() + "] " + msg, args);
    }

    public static void warn(LogCategory category, String msg) {
        if (category.isEnabled()) LOG.warn("[" + category.name() + "] " + msg);
    }

    public static void warn(LogCategory category, String msg, Object arg1) {
        if (category.isEnabled()) LOG.warn("[" + category.name() + "] " + msg, arg1);
    }

    public static void warn(LogCategory category, String msg, Object arg1, Object arg2) {
        if (category.isEnabled()) LOG.warn("[" + category.name() + "] " + msg, arg1, arg2);
    }

    public static void warn(LogCategory category, String msg, Object... args) {
        if (category.isEnabled()) LOG.warn("[" + category.name() + "] " + msg, args);
    }

    public static void error(LogCategory category, String msg) {
        if (category.isEnabled()) LOG.error("[" + category.name() + "] " + msg);
    }

    public static void error(LogCategory category, String msg, Object arg1) {
        if (category.isEnabled()) LOG.error("[" + category.name() + "] " + msg, arg1);
    }

    public static void error(LogCategory category, String msg, Object arg1, Object arg2) {
        if (category.isEnabled()) LOG.error("[" + category.name() + "] " + msg, arg1, arg2);
    }

    public static void error(LogCategory category, String msg, Object... args) {
        if (category.isEnabled()) LOG.error("[" + category.name() + "] " + msg, args);
    }

    public static void debug(LogCategory category, String msg) {
        if (category.isEnabled()) LOG.debug("[" + category.name() + "] " + msg);
    }

    public static void debug(LogCategory category, String msg, Object arg1) {
        if (category.isEnabled()) LOG.debug("[" + category.name() + "] " + msg, arg1);
    }

    public static void debug(LogCategory category, String msg, Object arg1, Object arg2) {
        if (category.isEnabled()) LOG.debug("[" + category.name() + "] " + msg, arg1, arg2);
    }

    public static void debug(LogCategory category, String msg, Object... args) {
        if (category.isEnabled()) LOG.debug("[" + category.name() + "] " + msg, args);
    }

    public static void trace(LogCategory category, String msg) {
        if (category.isEnabled()) LOG.trace("[" + category.name() + "] " + msg);
    }

    public static void trace(LogCategory category, String msg, Object arg1) {
        if (category.isEnabled()) LOG.trace("[" + category.name() + "] " + msg, arg1);
    }

    public static void trace(LogCategory category, String msg, Object arg1, Object arg2) {
        if (category.isEnabled()) LOG.trace("[" + category.name() + "] " + msg, arg1, arg2);
    }

    public static void trace(LogCategory category, String msg, Object... args) {
        if (category.isEnabled()) LOG.trace("[" + category.name() + "] " + msg, args);
    }

    private ModLog() {}
}
