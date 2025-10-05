package com.homesync.backup.homesync.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class DataSourceConfig {
    // Currently datasource is configured via application.properties and environment variables.
    // Add custom Hikari settings here if you want to tune connection pool properties.
}
