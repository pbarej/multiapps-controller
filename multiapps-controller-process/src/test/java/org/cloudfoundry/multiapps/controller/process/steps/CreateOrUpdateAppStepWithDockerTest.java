package org.cloudfoundry.multiapps.controller.process.steps;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.cloudfoundry.multiapps.common.test.GenericArgumentMatcher;
import org.cloudfoundry.multiapps.controller.client.lib.domain.CloudApplicationExtended;
import org.cloudfoundry.multiapps.controller.client.lib.domain.ImmutableCloudApplicationExtended;
import org.cloudfoundry.multiapps.controller.process.variables.Variables;
import org.flowable.engine.ProcessEngine;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import com.fasterxml.jackson.databind.JsonNode;
import com.sap.cloudfoundry.client.facade.domain.DockerInfo;
import com.sap.cloudfoundry.client.facade.domain.ImmutableCloudApplication;
import com.sap.cloudfoundry.client.facade.domain.ImmutableCloudMetadata;
import com.sap.cloudfoundry.client.facade.domain.ImmutableDockerCredentials;
import com.sap.cloudfoundry.client.facade.domain.ImmutableDockerInfo;

class CreateOrUpdateAppStepWithDockerTest extends CreateOrUpdateAppStepBaseTest {

    @Mock
    private ProcessEngine processEngine;

    private static final DockerInfo DOCKER_INFO = ImmutableDockerInfo.builder()
                                                                     .image("cloudfoundry/test-app")
                                                                     .credentials(ImmutableDockerCredentials.builder()
                                                                                                            .username("someUser")
                                                                                                            .password("somePassword")
                                                                                                            .build())
                                                                     .build();

    @Test
    void testWithDockerImage() {
        stepInput = createStepInput();
        loadParameters();
        prepareContext();
        prepareClient();

        step.execute(execution);
        assertStepFinishedSuccessfully();

        validateClient();
    }

    private void prepareClient() {
        Mockito.doReturn(ImmutableCloudApplication.builder()
                                                  .metadata(ImmutableCloudMetadata.builder()
                                                                                  .guid(UUID.randomUUID())
                                                                                  .build())
                                                  .build())
               .when(client)
               .getApplication(application.getName());
    }

    private void prepareContext() {
        context.setVariable(Variables.APP_ARCHIVE_ID, "archive_id");
        context.setVariable(Variables.MODULES_INDEX, stepInput.applicationIndex);
        context.setVariable(Variables.SERVICES_TO_BIND, Collections.emptyList());

        context.setVariable(Variables.SERVICE_KEYS_CREDENTIALS_TO_INJECT, new HashMap<>());
        context.setVariable(Variables.APPS_TO_DEPLOY, Collections.emptyList());
        StepsTestUtil.mockApplicationsToDeploy(stepInput.applications, execution);
    }

    private void loadParameters() {
        application = stepInput.applications.get(stepInput.applicationIndex);
    }

    private StepInput createStepInput() {
        StepInput stepInput = new StepInput();

        CloudApplicationExtended cloudApplicationExtended = createFakeCloudApplicationExtended();

        stepInput.applicationIndex = 0;
        stepInput.applications = List.of(cloudApplicationExtended);

        return stepInput;
    }

    private CloudApplicationExtended createFakeCloudApplicationExtended() {
        return ImmutableCloudApplicationExtended.builder()
                                                .name("application1")
                                                .instances(1)
                                                .memory(0)
                                                .diskQuota(512)
                                                .dockerInfo(DOCKER_INFO)
                                                .build();
    }

    private void validateClient() {
        Integer diskQuota = (application.getDiskQuota() != 0) ? application.getDiskQuota() : null;
        Integer memory = (application.getMemory() != 0) ? application.getMemory() : null;

        Mockito.verify(client)
               .createApplication(eq(application.getName()), argThat(GenericArgumentMatcher.forObject(application.getStaging())),
                                  eq(diskQuota), eq(memory), eq(application.getUris()), eq(DOCKER_INFO));
        Mockito.verify(client)
               .updateApplicationEnv(eq(application.getName()), eq(application.getEnv()));
    }

    @Override
    protected CreateOrUpdateAppStep createStep() {
        return new CreateOrUpdateAppStep(processEngine) {

            @Override
            protected JsonNode getBindUnbindServicesCallActivity(ProcessContext context) {
                return null;
            }

        };
    }

}