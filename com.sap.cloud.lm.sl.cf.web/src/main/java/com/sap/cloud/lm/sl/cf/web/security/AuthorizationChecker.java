package com.sap.cloud.lm.sl.cf.web.security;

import java.text.MessageFormat;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

import org.cloudfoundry.client.lib.CloudFoundryOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.sap.cloud.lm.sl.cf.client.CloudFoundryOperationsExtended;
import com.sap.cloud.lm.sl.cf.client.util.ExecutionRetrier;
import com.sap.cloud.lm.sl.cf.client.util.TokenFactory;
import com.sap.cloud.lm.sl.cf.core.auditlogging.AuditLoggingProvider;
import com.sap.cloud.lm.sl.cf.core.cf.CloudFoundryClientProvider;
import com.sap.cloud.lm.sl.cf.core.cf.clients.SpaceGetter;
import com.sap.cloud.lm.sl.cf.core.helpers.ClientHelper;
import com.sap.cloud.lm.sl.cf.core.message.Messages;
import com.sap.cloud.lm.sl.cf.core.util.ApplicationConfiguration;
import com.sap.cloud.lm.sl.cf.core.util.UserInfo;
import com.sap.cloud.lm.sl.common.NotFoundException;
import com.sap.cloud.lm.sl.common.SLException;
import com.sap.cloud.lm.sl.common.util.Pair;
import com.sap.cloud.lm.sl.common.util.ResponseRenderer;

@Component
public class AuthorizationChecker {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationChecker.class);

    private final ExecutionRetrier retrier = new ExecutionRetrier();
    private final CloudFoundryClientProvider clientProvider;
    private final SpaceGetter spaceGetter;
    private final ApplicationConfiguration applicationConfiguration;

    @Inject
    public AuthorizationChecker(CloudFoundryClientProvider clientProvider, SpaceGetter spaceGetter,
        ApplicationConfiguration applicationConfiguration) {
        this.clientProvider = clientProvider;
        this.spaceGetter = spaceGetter;
        this.applicationConfiguration = applicationConfiguration;
    }

    public void ensureUserIsAuthorized(HttpServletRequest request, UserInfo userInfo, String organization, String space, String action) {
        try {
            if (!checkPermissions(userInfo, organization, space, request.getMethod()
                .equals(HttpMethod.GET), null)) {
                String message = MessageFormat.format(Messages.UNAUTHORISED_OPERATION_ORG_SPACE, action, organization, space);
                failWithForbiddenStatus(message);
            }
        } catch (SLException e) {
            String message = MessageFormat.format(Messages.PERMISSION_CHECK_FAILED_ORG_SPACE, action, organization, space);
            failWithUnauthorizedStatus(message);
        }
    }

    public void ensureUserIsAuthorized(HttpServletRequest request, UserInfo userInfo, String spaceGuid, String action) {
        try {
            if (!checkPermissions(userInfo, spaceGuid, request.getMethod()
                .equals(HttpMethod.GET))) {
                String message = MessageFormat.format(Messages.UNAUTHORISED_OPERATION_SPACE_ID, action, spaceGuid);
                failWithForbiddenStatus(message);
            }
        } catch (SLException e) {
            String message = MessageFormat.format(Messages.PERMISSION_CHECK_FAILED_SPACE_ID, action, spaceGuid);
            failWithUnauthorizedStatus(message);
        }
    }

    boolean checkPermissions(UserInfo userInfo, String orgName, String spaceName, boolean readOnly, String processId) throws SLException {
        if (applicationConfiguration.areDummyTokensEnabled() && isDummyToken(userInfo)) {
            return true;
        }
        if (hasAdminScope(userInfo)) {
            return true;
        }
        CloudFoundryOperations client = clientProvider.getCloudFoundryClient(userInfo.getName());
        return checkPermissions(client, userInfo, orgName, spaceName, readOnly);
    }

    boolean checkPermissions(UserInfo userInfo, String spaceGuid, boolean readOnly) throws SLException {
        if (applicationConfiguration.areDummyTokensEnabled() && isDummyToken(userInfo)) {
            return true;
        }
        if (hasAdminScope(userInfo)) {
            return true;
        }
        CloudFoundryOperations client = clientProvider.getCloudFoundryClient(userInfo.getName());
        Pair<String, String> location = new ClientHelper(client, spaceGetter).computeOrgAndSpace(spaceGuid);
        if (location == null) {
            throw new NotFoundException(Messages.ORG_AND_SPACE_NOT_FOUND, spaceGuid);
        }
        return checkPermissions(client, userInfo, location._1, location._2, readOnly);
    }

    private boolean checkPermissions(CloudFoundryOperations client, UserInfo userInfo, String orgName, String spaceName, boolean readOnly) {
        CloudFoundryOperationsExtended clientx = (CloudFoundryOperationsExtended) client;
        return hasPermissions(clientx, userInfo.getId(), orgName, spaceName, readOnly) && hasAccess(clientx, orgName, spaceName);
    }

    private boolean hasPermissions(CloudFoundryOperationsExtended client, String userId, String orgName, String spaceName,
        boolean readOnly) {
        if (client.getSpaceDevelopers2(orgName, spaceName)
            .contains(userId)) {
            return true;
        }
        if (readOnly) {
            if (client.getSpaceAuditors2(orgName, spaceName)
                .contains(userId)) {
                return true;
            }
            if (client.getSpaceManagers2(orgName, spaceName)
                .contains(userId)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAccess(CloudFoundryOperationsExtended client, String orgName, String spaceName) {
        return retrier.executeWithRetry(() -> spaceGetter.findSpace(client, orgName, spaceName)) != null;
    }

    private boolean isDummyToken(UserInfo userInfo) {
        return userInfo.getToken()
            .getValue()
            .equals(TokenFactory.DUMMY_TOKEN);
    }

    private boolean hasAdminScope(UserInfo userInfo) {
        return userInfo.getToken()
            .getScope()
            .contains(TokenFactory.SCOPE_CC_ADMIN);
    }

    private void failWithUnauthorizedStatus(String message) {
        failWithStatus(Status.UNAUTHORIZED, message);
    }

    private void failWithForbiddenStatus(String message) {
        failWithStatus(Status.FORBIDDEN, message);
    }

    private static void failWithStatus(Status status, String message) {
        LOGGER.warn(message);
        AuditLoggingProvider.getFacade()
            .logSecurityIncident(message);
        throw new WebApplicationException(ResponseRenderer.renderResponseForStatus(status, message));
    }

}
