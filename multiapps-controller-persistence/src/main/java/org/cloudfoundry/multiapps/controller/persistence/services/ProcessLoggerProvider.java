package org.cloudfoundry.multiapps.controller.persistence.services;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Function;

import javax.inject.Inject;
import javax.inject.Named;
import javax.sql.DataSource;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.filter.LevelMatchFilter;
import org.apache.logging.log4j.core.layout.AbstractStringLayout;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.cloudfoundry.multiapps.controller.persistence.Constants;
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
    private final LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false);
    private final PatternLayout patternLayout = PatternLayout.newBuilder()
                                                             .withPattern(LOG_LAYOUT)
                                                             .withConfiguration(loggerContext.getConfiguration())
                                                             .build();

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
        return getLogger(execution, logName, loggerContextDel -> patternLayout);
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
        return createProcessLogger(spaceId, correlationId, activityId, name, logNameWithExtension, layout);
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
        logDbAppender.start();
        return new ProcessLogger(logDbAppender, loggerName);
    }

    private String getSpaceId(DelegateExecution execution) {
        return (String) execution.getVariable(Constants.VARIABLE_NAME_SPACE_ID);
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
        }

        @Override
        public void append(LogEvent event) {
            if (event.getSource()
                     .getClassName()
                     .contains(SPRINGFRAMEWORK_CLASS_NAME)
                || dataSource == null) {
                return;
            }
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

            SqlOperationLogQueryProvider.saveLogInDatabase(enhancedWithMessageOperationLogEntry, dataSource);
        }
    }
}
