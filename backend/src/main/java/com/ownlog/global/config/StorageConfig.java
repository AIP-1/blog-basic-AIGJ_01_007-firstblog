package com.ownlog.global.config;

import com.ownlog.global.storage.UploadProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(UploadProperties.class)
public class StorageConfig {
}
