package org.cloudfoundry.multiapps.controller.process.steps;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Named;

import org.cloudfoundry.multiapps.common.util.JsonUtil;
import org.cloudfoundry.multiapps.controller.client.lib.domain.CloudApplicationExtended;
import org.cloudfoundry.multiapps.controller.client.lib.domain.ImmutableCloudApplicationExtended;
import org.cloudfoundry.multiapps.controller.client.lib.domain.ServiceKeyToInject;
import org.cloudfoundry.multiapps.controller.core.helpers.ApplicationFileDigestDetector;
import org.cloudfoundry.multiapps.controller.persistence.services.FileStorageException;
import org.cloudfoundry.multiapps.controller.process.Messages;
import org.cloudfoundry.multiapps.controller.process.util.ApplicationAttributeUpdater;
import org.cloudfoundry.multiapps.controller.process.util.ApplicationAttributeUpdater.UpdateState;
import org.cloudfoundry.multiapps.controller.process.util.ControllerClientFacade;
import org.cloudfoundry.multiapps.controller.process.util.DiskQuotaApplicationAttributeUpdater;
import org.cloudfoundry.multiapps.controller.process.util.ElementUpdater.UpdateStrategy;
import org.cloudfoundry.multiapps.controller.process.util.EnvironmentApplicationAttributeUpdater;
import org.cloudfoundry.multiapps.controller.process.util.MemoryApplicationAttributeUpdater;
import org.cloudfoundry.multiapps.controller.process.util.ServiceOperationUtil;
import org.cloudfoundry.multiapps.controller.process.util.StagingApplicationAttributeUpdater;
import org.cloudfoundry.multiapps.controller.process.util.UrisApplicationAttributeUpdater;
import org.cloudfoundry.multiapps.controller.process.variables.Variables;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import com.sap.cloudfoundry.client.facade.CloudControllerClient;
import com.sap.cloudfoundry.client.facade.domain.CloudApplication;

@Named("createOrUpdateAppStep")
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class CreateOrUpdateAppStep extends SyncFlowableStep {

    protected BooleanSupplier shouldPrettyPrint = () -> true;

    @Override
    protected StepPhase executeStep(ProcessContext context) throws FileStorageException {
        CloudApplicationExtended app = context.getVariable(Variables.APP_TO_PROCESS);

        CloudControllerClient client = context.getControllerClient();
        CloudApplication existingApp = client.getApplication(app.getName(), false);
        context.setVariable(Variables.EXISTING_APP, existingApp);

        StepFlowHandler flowHandler = createStepFlowHandler(context, client, app, existingApp);

        flowHandler.printStepStartMessage();

        flowHandler.handleApplicationAttributes();
        flowHandler.injectServiceKeysCredentialsInAppEnv();
        flowHandler.handleApplicationServices();
        flowHandler.handleApplicationEnv();
        flowHandler.printStepEndMessage();

        return StepPhase.DONE;
    }

    @Override
    protected String getStepErrorMessage(ProcessContext context) {
        return MessageFormat.format(Messages.ERROR_CREATING_OR_UPDATING_APP, context.getVariable(Variables.APP_TO_PROCESS)
                                                                                    .getName());
    }

    private StepFlowHandler createStepFlowHandler(ProcessContext context, CloudControllerClient client, CloudApplicationExtended app,
                                                  CloudApplication existingApp) {
        if (existingApp == null) {
            return new CreateAppFlowHandler(context, client, app);
        }
        return new UpdateAppFlowHandler(context, client, app, existingApp);
    }

    private abstract class StepFlowHandler {

        final ProcessContext context;
        CloudApplicationExtended app;
        final CloudControllerClient client;

        public StepFlowHandler(ProcessContext context, CloudControllerClient client, CloudApplicationExtended app) {
            this.context = context;
            this.app = app;
            this.client = client;
        }

        public void injectServiceKeysCredentialsInAppEnv() {
            Map<String, String> appEnv = new LinkedHashMap<>(app.getEnv());
            Map<String, String> appServiceKeysCredentials = buildServiceKeysCredentials(client, app, appEnv);
            app = ImmutableCloudApplicationExtended.copyOf(app)
                                                   .withEnv(appEnv);
            updateContextWithServiceKeysCredentials(app, appServiceKeysCredentials);
        }

        private Map<String, String> buildServiceKeysCredentials(CloudControllerClient client, CloudApplicationExtended app,
                                                                Map<String, String> appEnv) {
            Map<String, String> appServiceKeysCredentials = new HashMap<>();
            for (ServiceKeyToInject serviceKeyToInject : app.getServiceKeysToInject()) {
                String serviceKeyCredentials = JsonUtil.toJson(ServiceOperationUtil.getServiceKeyCredentials(client,
                                                                                                             serviceKeyToInject.getServiceName(),
                                                                                                             serviceKeyToInject.getServiceKeyName()),
                                                               shouldPrettyPrint.getAsBoolean());
                appEnv.put(serviceKeyToInject.getEnvVarName(), serviceKeyCredentials);
                appServiceKeysCredentials.put(serviceKeyToInject.getEnvVarName(), serviceKeyCredentials);
            }
            return appServiceKeysCredentials;
        }

        private void updateContextWithServiceKeysCredentials(CloudApplicationExtended app, Map<String, String> appServiceKeysCredentials) {
            Map<String, Map<String, String>> serviceKeysCredentialsToInject = context.getVariable(Variables.SERVICE_KEYS_CREDENTIALS_TO_INJECT);
            serviceKeysCredentialsToInject.put(app.getName(), appServiceKeysCredentials);

            // Update current process context
            context.setVariable(Variables.APP_TO_PROCESS, app);
            context.setVariable(Variables.SERVICE_KEYS_CREDENTIALS_TO_INJECT, serviceKeysCredentialsToInject);
        }

        public abstract void printStepStartMessage();

        public abstract void handleApplicationAttributes();

        public abstract void handleApplicationServices();

        public abstract void handleApplicationEnv();

        public abstract void printStepEndMessage();
    }

    private class CreateAppFlowHandler extends StepFlowHandler {

        public CreateAppFlowHandler(ProcessContext context, CloudControllerClient client, CloudApplicationExtended app) {
            super(context, client, app);
        }

        @Override
        public void handleApplicationAttributes() {
            Integer diskQuota = (app.getDiskQuota() != 0) ? app.getDiskQuota() : null;
            Integer memory = (app.getMemory() != 0) ? app.getMemory() : null;
            List<String> uris = app.getUris();

            if (app.getDockerInfo() != null) {
                context.getStepLogger()
                       .info(Messages.CREATING_APP_FROM_DOCKER_IMAGE, app.getName(), app.getDockerInfo()
                                                                                        .getImage());
            }
            client.createApplication(app.getName(), app.getStaging(), diskQuota, memory, uris, app.getDockerInfo());
            context.setVariable(Variables.VCAP_APP_PROPERTIES_CHANGED, true);
        }

        @Override
        public void handleApplicationServices() {
            context.setVariable(Variables.SERVICES_TO_UNBIND_BIND, app.getServices());
        }

        @Override
        public void handleApplicationEnv() {
            client.updateApplicationEnv(app.getName(), app.getEnv());
            context.setVariable(Variables.USER_PROPERTIES_CHANGED, true);
        }

        @Override
        public void printStepStartMessage() {
            getStepLogger().info(Messages.CREATING_APP_FROM_MODULE, app.getName(), app.getModuleName());
        }

        @Override
        public void printStepEndMessage() {
            getStepLogger().debug(Messages.APP_CREATED, app.getName());
        }

    }

    private class UpdateAppFlowHandler extends StepFlowHandler {

        final CloudApplication existingApp;

        public UpdateAppFlowHandler(ProcessContext context, CloudControllerClient client, CloudApplicationExtended app,
                                    CloudApplication existingApp) {
            super(context, client, app);
            this.existingApp = existingApp;
        }

        @Override
        public void handleApplicationAttributes() {
            List<UpdateState> updateStates = getApplicationAttributeUpdaters().stream()
                                                                              .map(updater -> updater.update(existingApp, app))
                                                                              .collect(Collectors.toList());

            boolean arePropertiesChanged = updateStates.contains(UpdateState.UPDATED);

            reportApplicationUpdateStatus(app, arePropertiesChanged);
            context.setVariable(Variables.VCAP_APP_PROPERTIES_CHANGED, arePropertiesChanged);
        }

        @Override
        public void handleApplicationServices() {
            if (context.getVariable(Variables.SHOULD_SKIP_SERVICE_REBINDING)) {
                return;
            }
            List<String> services = getMtaAndExistingSevices();
            context.setVariable(Variables.SERVICES_TO_UNBIND_BIND, services);
        }

        @Override
        public void handleApplicationEnv() {
            Map<String, String> envAsMap = new LinkedHashMap<>(app.getEnv());
            addCurrentAppDigestToNewEnv(envAsMap, existingApp.getEnv());
            app = ImmutableCloudApplicationExtended.copyOf(app)
                                                   .withEnv(envAsMap);

            UpdateState updateApplicationEnvironmentState = updateApplicationEnvironment(app, existingApp, client,
                                                                                         app.getAttributesUpdateStrategy());

            context.setVariable(Variables.USER_PROPERTIES_CHANGED, updateApplicationEnvironmentState == UpdateState.UPDATED);
        }

        @Override
        public void printStepStartMessage() {
            getStepLogger().info(Messages.UPDATING_APP, app.getName());
        }

        @Override
        public void printStepEndMessage() {
            getStepLogger().debug(Messages.APP_UPDATED, app.getName());
        }

        private List<String> getMtaAndExistingSevices() {
            return Stream.of(app.getServices(), existingApp.getServices())
                         .flatMap(List::stream)
                         .distinct()
                         .collect(Collectors.toList());
        }

        private UpdateState
                updateApplicationEnvironment(CloudApplicationExtended app, CloudApplication existingApp, CloudControllerClient client,
                                             CloudApplicationExtended.AttributeUpdateStrategy applicationAttributesUpdateStrategy) {
            ControllerClientFacade.Context context = new ControllerClientFacade.Context(client, getStepLogger());
            return new EnvironmentApplicationAttributeUpdater(context,
                                                              getUpdateStrategy(applicationAttributesUpdateStrategy.shouldKeepExistingEnv())).update(existingApp,
                                                                                                                                                     app);
        }

        private void reportApplicationUpdateStatus(CloudApplicationExtended app, boolean appPropertiesChanged) {
            if (!appPropertiesChanged) {
                getStepLogger().info(Messages.APPLICATION_ATTRIBUTES_UNCHANGED, app.getName());
                return;
            }
            getStepLogger().debug(Messages.APP_UPDATED, app.getName());
        }

        protected List<ApplicationAttributeUpdater> getApplicationAttributeUpdaters() {
            ControllerClientFacade.Context context = new ControllerClientFacade.Context(client, getStepLogger());
            return Arrays.asList(new StagingApplicationAttributeUpdater(context), new MemoryApplicationAttributeUpdater(context),
                                 new DiskQuotaApplicationAttributeUpdater(context),
                                 new UrisApplicationAttributeUpdater(context, UpdateStrategy.REPLACE));
        }

        private UpdateStrategy getUpdateStrategy(boolean shouldKeepAttributes) {
            return shouldKeepAttributes ? UpdateStrategy.MERGE : UpdateStrategy.REPLACE;
        }

        private void addCurrentAppDigestToNewEnv(Map<String, String> newAppEnv, Map<String, String> existingAppEnv) {
            String existingFileDigest = getExistingAppFileDigest(existingAppEnv);
            if (existingFileDigest == null) {
                return;
            }
            String newAppDeployAttributes = newAppEnv.get(org.cloudfoundry.multiapps.controller.core.Constants.ENV_DEPLOY_ATTRIBUTES);
            TreeMap<String, Object> newAppDeployAttributesMap = new TreeMap<>(JsonUtil.convertJsonToMap(newAppDeployAttributes));
            newAppDeployAttributesMap.put(org.cloudfoundry.multiapps.controller.core.Constants.ATTR_APP_CONTENT_DIGEST, existingFileDigest);
            newAppEnv.put(org.cloudfoundry.multiapps.controller.core.Constants.ENV_DEPLOY_ATTRIBUTES,
                          JsonUtil.toJson(newAppDeployAttributesMap, shouldPrettyPrint.getAsBoolean()));
        }

        private String getExistingAppFileDigest(Map<String, String> envAsMap) {
            return new ApplicationFileDigestDetector(envAsMap).detectCurrentAppFileDigest();
        }

    }

}
