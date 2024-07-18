package org.cloudfoundry.multiapps.controller.persistence.services;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.message.MessageFactory;

public class CustomLogger extends Logger {
    public CustomLogger(LoggerContext context, String name, MessageFactory messageFactory) {
        super(context, name, messageFactory);
    }

    public void addCustomAppender(Appender appender) {
        Configuration config = getContext().getConfiguration();
        appender.start();
        config.addAppender(appender);
        addAppender(appender);
        config.getLoggerConfig(getName()).addAppender(appender, Level.DEBUG, null);
        getContext().updateLoggers();
    }
}
