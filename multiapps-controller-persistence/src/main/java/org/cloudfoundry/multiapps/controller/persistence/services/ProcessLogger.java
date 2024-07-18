package org.cloudfoundry.multiapps.controller.persistence.services;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.ObjectMessage;

public class ProcessLogger {

    protected final String spaceId;
    protected final String processId;
    protected final String activityId;
    protected final String logName;
    private ProcessLoggerProvider.LogDbAppender logDbAppender;

    public ProcessLogger(String spaceId, String processId, String activityId,
                         ProcessLoggerProvider.LogDbAppender logDbAppender, String logName) {
        this.spaceId = spaceId;
        this.processId = processId;
        this.activityId = activityId;
        this.logDbAppender = logDbAppender;
        this.logName = logName;
    }

    public void info(Object message) {
        logDbAppender.append(createEvent(message, "info"));
        logDbAppender.stop();
    }

    public void debug(Object message) {
        logDbAppender.append(createEvent(message, "debug"));
        logDbAppender.stop();
    }

    public void debug(Object message, Throwable throwable) {
        logDbAppender.append(createEvent(message, "debug", throwable));
        logDbAppender.stop();
    }

    public void error(Object message) {
        logDbAppender.append(createEvent(message, "error"));
        logDbAppender.stop();
    }

    public void error(Object message, Throwable t) {
        logDbAppender.append(createEvent(message, "error", t));
        logDbAppender.stop();
    }

    public void trace(Object message) {
        logDbAppender.append(createEvent(message, "trace"));
        logDbAppender.stop();
    }

    public void warn(Object message) {
        logDbAppender.append(createEvent(message, "warn"));
        logDbAppender.stop();
    }

    public void warn(Object message, Throwable t) {
        logDbAppender.append(createEvent(message, "warn", t));
        logDbAppender.stop();
    }

    private LogEvent createEvent(Object message, String methodName) {
        return createEvent(message, methodName, null);
    }

    private LogEvent createEvent(Object message, String methodName, Throwable t) {
        Message message1 = new ObjectMessage(message);
        StackTraceElement b = new StackTraceElement(null, null, null, ProcessLoggerProvider.class.getName(), methodName, null, 48);
        return new Log4jLogEvent(logName, null, null, b,Level.INFO, message1, null, t);
    }

    public String getProcessId() {
        return this.processId;
    }

    public String getActivityId() {
        return this.activityId;
    }

    public ProcessLoggerProvider.LogDbAppender getAppender() {
        return logDbAppender;
    }


    public void closeLoggerContext() {
    }
}
