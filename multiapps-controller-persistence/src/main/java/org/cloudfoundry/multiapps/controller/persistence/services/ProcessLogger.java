package org.cloudfoundry.multiapps.controller.persistence.services;

import java.time.LocalDateTime;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.core.layout.AbstractStringLayout;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.ObjectMessage;
import org.cloudfoundry.multiapps.controller.persistence.model.ImmutableOperationLogEntry;
import org.cloudfoundry.multiapps.controller.persistence.model.OperationLogEntry;

public class ProcessLogger {

    private static final String INFO_METHOD_NAME = "info";
    private static final String WARN_METHOD_NAME = "warn";
    private static final String TRACE_METHOD_NAME = "trace";
    private static final String ERROR_METHOD_NAME = "error";
    private static final String DEBUG_METHOD_NAME = "debug";
    private final AbstractStringLayout layout;
    private final String activityId;
    private final String logName;
    private OperationLogEntry operationLogEntry;
    private String logMessage;

    public ProcessLogger(OperationLogEntry operationLogEntry, String logName, AbstractStringLayout layout, String activityId) {
        this.operationLogEntry = operationLogEntry;
        this.layout = layout;
        this.activityId = activityId;
        this.logName = logName;
    }

    public void info(Object message) {
        addMessageAndLogTimeToOperationLogEntry(message, INFO_METHOD_NAME);
    }

    public void debug(Object message) {
        addMessageAndLogTimeToOperationLogEntry(message, DEBUG_METHOD_NAME);
    }

    public void debug(Object message, Throwable throwable) {
        addMessageAndLogTimeToOperationLogEntry(message, DEBUG_METHOD_NAME, throwable);
    }

    public void error(Object message) {
        addMessageAndLogTimeToOperationLogEntry(message, ERROR_METHOD_NAME);
    }

    public void error(Object message, Throwable t) {
        addMessageAndLogTimeToOperationLogEntry(message, ERROR_METHOD_NAME, t);
    }

    public void trace(Object message) {
        addMessageAndLogTimeToOperationLogEntry(message, TRACE_METHOD_NAME);
    }

    public void warn(Object message) {
        addMessageAndLogTimeToOperationLogEntry(message, WARN_METHOD_NAME);
    }

    public void warn(Object message, Throwable t) {
        addMessageAndLogTimeToOperationLogEntry(message, WARN_METHOD_NAME, t);
    }

    public String getLogMessage() {
        return logMessage;
    }

    public AbstractStringLayout getLayout() {
        return layout;
    }

    public String getActivityId() {
        return activityId;
    }

    public OperationLogEntry getOperationLogEntry() {
        return operationLogEntry;
    }

    private void addMessageAndLogTimeToOperationLogEntry(Object message, String methodName) {
        logMessage = layout.toSerializable(createEvent(message, methodName));
    }

    private void addMessageAndLogTimeToOperationLogEntry(Object message, String methodName, Throwable t) {
        logMessage = layout.toSerializable(createEvent(message, methodName, t));
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
