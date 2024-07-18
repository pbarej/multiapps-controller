package org.cloudfoundry.multiapps.controller.persistence.services;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.sql.DataSource;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.filter.LevelMatchFilter;
import org.apache.logging.log4j.core.layout.AbstractStringLayout;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.cloudfoundry.multiapps.controller.persistence.Constants;
import org.cloudfoundry.multiapps.controller.persistence.Messages;
import org.cloudfoundry.multiapps.controller.persistence.model.ImmutableOperationLogEntry;
import org.cloudfoundry.multiapps.controller.persistence.model.OperationLogEntry;
import org.cloudfoundry.multiapps.controller.persistence.query.providers.SqlOperationLogQueryProvider;
import org.flowable.engine.delegate.DelegateExecution;

@Named("processLoggerProvider")
public class ProcessLoggerProvider {

    static final String LOG_LAYOUT = "#2.0#%d{yyyy MM dd HH:mm:ss.SSS}#%d{XXX}#%p#%c#%n%X{MsgCode}#%X{CSNComponent}#%X{DCComponent}##%X{DSRCorrelationId}#%X{Application}#%C#%X{User}#%X{Session}#%X{Transaction}#%X{DSRRootContextId}#%X{DSRTransaction}#%X{DSRConnection}#%X{DSRCounter}#%t##%X{ResourceBundle}#%n%m#%n%n";
    private static final LevelMatchFilter DEBUG_FILTER = LevelMatchFilter.newBuilder()
                                                                         .setLevel(Level.DEBUG)
                                                                         .setOnMatch(Filter.Result.ACCEPT)
                                                                         .setOnMismatch(Filter.Result.ACCEPT)
                                                                         .build();
    private static final String PARENT_LOGGER = "com.sap.cloud.lm.sl.xs2";
    private static final String DEFAULT_LOG_NAME = "OPERATION";
    private static final String LOG_FILE_EXTENSION = ".log";
    private final DataSource dataSource;

    private final Map<String, ProcessLogger> loggersCache = new ConcurrentHashMap<>();
    private final LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false);

    @Inject
    public ProcessLoggerProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public ProcessLoggerProvider() {
        this.dataSource = null;
    }

    public ProcessLogger getLogger(DelegateExecution execution) {
        return getLogger(execution, DEFAULT_LOG_NAME);
    }

    public ProcessLogger getLogger(DelegateExecution execution, String logName) {
        return getLogger(execution, logName, loggerContextDel -> PatternLayout.newBuilder()
                                                                              .withPattern(LOG_LAYOUT)
                                                                              .withConfiguration(loggerContextDel.getConfiguration())
                                                                              .build());
    }

    public ProcessLogger getLogger(DelegateExecution execution, String logName,
                                   Function<LoggerContext, AbstractStringLayout> layoutCreatorFunction) {
        String name = getLoggerName(execution, logName);
        String correlationId = getCorrelationId(execution);
        String spaceId = getSpaceId(execution);
        String activityId = getTaskId(execution);
        String logNameWithExtension = logName + LOG_FILE_EXTENSION;
        AbstractStringLayout layout = layoutCreatorFunction.apply(loggerContext);
        if (correlationId == null || activityId == null) {
            return new NullProcessLogger(spaceId, execution.getProcessInstanceId(), activityId);
        }
        return loggersCache.computeIfAbsent(name, (String loggerName) -> createProcessLogger(spaceId, correlationId, activityId, loggerName,
                                                                                             logNameWithExtension, layout));
    }

    private String getLoggerName(DelegateExecution execution, String logName) {
        return PARENT_LOGGER + '.' + getCorrelationId(execution) + '.' + logName + '.' + getTaskId(execution);
    }

    private String getCorrelationId(DelegateExecution execution) {
        return (String) execution.getVariable(Constants.CORRELATION_ID);
    }

    private String getTaskId(DelegateExecution execution) {
        String taskId = (String) execution.getVariable(Constants.TASK_ID);
        return taskId != null ? taskId : execution.getCurrentActivityId();
    }

    private ProcessLogger createProcessLogger(String spaceId, String correlationId, String activityId, String loggerName, String logName,
                                              AbstractStringLayout patternLayout) {
        LogDbAppender logDbAppender = new LogDbAppender(correlationId, spaceId, logName, loggerName, patternLayout);
        attachFileAppender(loggerName, logDbAppender);

        Logger logger = loggerContext.getLogger(loggerName);
        logDbAppender.start();
        loggerContext.getConfiguration()
                     .addLoggerAppender(logger, logDbAppender);
        loggerContext.updateLoggers();
        return new ProcessLogger(loggerContext, logger, spaceId, correlationId, activityId, logDbAppender);
    }

    private PatternLayout createPatternLayout() {
        return PatternLayout.newBuilder()
                            .withPattern(LOG_LAYOUT)
                            .withConfiguration(loggerContext.getConfiguration())
                            .build();
    }

    private void attachFileAppender(String loggerName, LogDbAppender logDbAppender) {
        loggerContext.addFilter(DEBUG_FILTER);
        LoggerConfig loggerConfig = getLoggerConfig(loggerContext, loggerName);
        setLoggerConfigLoggingLevel(loggerConfig, Level.DEBUG);
        addAppenderToLoggerConfig(loggerConfig, logDbAppender, Level.DEBUG);
        disableConsoleLogging(loggerContext);
    }

    private LoggerConfig getLoggerConfig(LoggerContext loggerContext, String loggerName) {
        return LoggerConfig.newBuilder()
                           .withConfig(loggerContext.getConfiguration())
                           .withLoggerName(loggerName)
                           .build();
    }

    private void setLoggerConfigLoggingLevel(LoggerConfig loggerConfig, Level level) {
        loggerConfig.setLevel(level != null ? level : Level.DEBUG);
    }

    private void addAppenderToLoggerConfig(LoggerConfig loggerConfig, LogDbAppender logDbAppender, Level level) {
        loggerConfig.addAppender(logDbAppender, level != null ? level : Level.DEBUG, DEBUG_FILTER);
    }

    private void disableConsoleLogging(LoggerContext loggerContext) {
        for (Appender appender : getAllAppenders(loggerContext)) {
            if (appender.getName()
                        .contains(Messages.DEFAULT_CONSOLE)) {
                loggerContext.getRootLogger()
                             .removeAppender(appender);
            }
        }
    }

    private Collection<Appender> getAllAppenders(LoggerContext loggerContext) {
        return Collections.unmodifiableCollection(loggerContext.getRootLogger()
                                                               .getAppenders()
                                                               .values());
    }

    private String getSpaceId(DelegateExecution execution) {
        return (String) execution.getVariable(Constants.VARIABLE_NAME_SPACE_ID);
    }

    public List<ProcessLogger> getExistingLoggers(String processId, String activityId) {
        return loggersCache.values()
                           .stream()
                           .filter(logger -> hasLoggerSpecificProcessIdAndActivityId(processId, activityId, logger))
                           .collect(Collectors.toList());
    }

    private boolean hasLoggerSpecificProcessIdAndActivityId(String processId, String activityId, ProcessLogger logger) {
        return processId.equals(logger.getProcessId()) && activityId.equals(logger.getActivityId());
    }

    public void removeLoggersCache(ProcessLogger processLogger) {
        loggersCache.remove(processLogger.getLoggerName());
    }

    public class LogDbAppender extends AbstractAppender {

        private static final String SPRINGFRAMEWORK_CLASS_NAME = "springframework";
        private final String spaceId;
        private final String correlationId;
        private final String logName;

        public LogDbAppender(String correlationId, String spaceId, String logName, String loggerName,
                             Layout<? extends Serializable> layout) {
            super(loggerName, DEBUG_FILTER, layout, Boolean.FALSE, null);
            this.spaceId = spaceId;
            this.logName = logName;
            this.correlationId = correlationId;
            start();
        }

        @Override
        public void append(LogEvent event) {
            if (event.getSource()
                     .getClassName()
                     .contains(SPRINGFRAMEWORK_CLASS_NAME)
                || dataSource == null) {
                return;
            }
            try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(SqlOperationLogQueryProvider.INSERT_FILE_ATTRIBUTES_AND_CONTENT)) {
                String formatterMessage = getLayout().toSerializable(event)
                                                     .toString();

                OperationLogEntry enhancedWithMessageOperationLogEntry = ImmutableOperationLogEntry.builder()
                                                                                                   .space(spaceId)
                                                                                                   .operationLogName(logName)
                                                                                                   .operationId(correlationId)
                                                                                                   .id(UUID.randomUUID()
                                                                                                           .toString())
                                                                                                   .modified(LocalDateTime.now())
                                                                                                   .operationLog(formatterMessage)
                                                                                                   .build();

                SqlOperationLogQueryProvider.enhanceInsertOperationLogQuery(enhancedWithMessageOperationLogEntry, statement);
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new OperationLogStorageException(Messages.FAILED_TO_SAVE_OPERATION_LOG_IN_DATABASE, e);
            }
        }

    }
}
