package org.cloudfoundry.multiapps.controller.persistence.services;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;
import javax.inject.Named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

@Named
public class ProcessLoggerCleaner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessLoggerCleaner.class);
    private final Map<String, ProcessLogger> loggersToClose = new ConcurrentHashMap<>();
    @Inject
    private ProcessLoggerProvider processLoggerProvider;

    @Scheduled(fixedDelay = 300000)
    public void closeAppenders() {
//        LOGGER.info("Will performe clean up");
//        List<String> removedLoggerNames = new ArrayList<>();
//        for (var loggerToClose : loggersToClose.entrySet()) {
//            if (hasFiveMinutesPassedSinceTheLastAppenderUsage(loggerToClose.getValue())) {
//                loggerToClose.getValue()
//                             .closeLoggerContext();
//                LOGGER.info("Removed: " + loggerToClose.getValue()
//                                                       .getLoggerName());
//                removedLoggerNames.add(loggerToClose.getKey());
//            }
//        }
//        removedLoggerNames.forEach(loggersToClose::remove);
    }

    public void scheduleAppenderForClean(String processId, String activityId) {
    }

    private boolean hasFiveMinutesPassedSinceTheLastAppenderUsage(ProcessLogger processLogger) {
        long currentTime = System.currentTimeMillis();
//        long lastAppendTime = processLogger.getAppender()
//                                           .getLatestAppendTimeInMillis()
//                                           .get();

/*
        return currentTime - lastAppendTime > Duration.ofMinutes(5)
                                                      .toMillis();
*/
        return false;
    }
}
