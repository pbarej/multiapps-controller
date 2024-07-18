package org.cloudfoundry.multiapps.controller.persistence.services;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.ObjectMessage;

public class ProcessLogger {

    private static final String INFO_METHOD_NAME = "info";
    private static final String WARN_METHOD_NAME = "warn";
    private static final String TRACE_METHOD_NAME = "trace";
    private static final String ERROR_METHOD_NAME = "error";
    private static final String DEBUG_METHOD_NAME = "debug";
    private final String logName;
    private final ProcessLoggerProvider.LogDbAppender logDbAppender;

    public ProcessLogger(ProcessLoggerProvider.LogDbAppender logDbAppender, String logName) {
        this.logDbAppender = logDbAppender;
        this.logName = logName;
    }

    public void info(Object message) {
        logDbAppender.append(createEvent(message, INFO_METHOD_NAME));
        logDbAppender.stop();
    }

    public void debug(Object message) {
        logDbAppender.append(createEvent(message, DEBUG_METHOD_NAME));
        logDbAppender.stop();
    }

    public void debug(Object message, Throwable throwable) {
        logDbAppender.append(createEvent(message, DEBUG_METHOD_NAME, throwable));
        logDbAppender.stop();
    }

    public void error(Object message) {
        logDbAppender.append(createEvent(message, ERROR_METHOD_NAME));
        logDbAppender.stop();
    }

    public void error(Object message, Throwable t) {
        logDbAppender.append(createEvent(message, ERROR_METHOD_NAME, t));
        logDbAppender.stop();
    }

    public void trace(Object message) {
        logDbAppender.append(createEvent(message, TRACE_METHOD_NAME));
        logDbAppender.stop();
    }

    public void warn(Object message) {
        logDbAppender.append(createEvent(message, WARN_METHOD_NAME));
        logDbAppender.stop();
    }

    public void warn(Object message, Throwable t) {
        logDbAppender.append(createEvent(message, WARN_METHOD_NAME, t));
        logDbAppender.stop();
    }

    private LogEvent createEvent(Object message, String methodName) {
        return createEvent(message, methodName, null);
    }

    private LogEvent createEvent(Object message, String methodName, Throwable t) {
        Message logMessage = new ObjectMessage(message);
        StackTraceElement stackTrace = new StackTraceElement(null, null, null, ProcessLoggerProvider.class.getName(), methodName, null, 48);
        return new Log4jLogEvent(logName, null, null, stackTrace, Level.INFO, logMessage, null, t);
    }
}
