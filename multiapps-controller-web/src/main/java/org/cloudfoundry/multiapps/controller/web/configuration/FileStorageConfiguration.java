package org.cloudfoundry.multiapps.controller.web.configuration;

import org.cloudfoundry.multiapps.controller.persistence.util.EnvironmentServicesFinder;
import org.cloudfoundry.multiapps.controller.web.configuration.bean.factory.ObjectStoreFileStorageFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.inject.Inject;

@Configuration
public class FileStorageConfiguration {

    private static final String OBJECT_STORE_SERVICE_NAME = "deploy-service-os";

    @Inject
    @Bean
    public ObjectStoreFileStorageFactoryBean objectStoreFileStorage(EnvironmentServicesFinder vcapServiceFinder) {
        return new ObjectStoreFileStorageFactoryBean(OBJECT_STORE_SERVICE_NAME, vcapServiceFinder);
    }
}
