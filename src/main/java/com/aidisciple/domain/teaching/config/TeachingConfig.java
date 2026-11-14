package com.aidisciple.domain.teaching.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TeachingProperties.class)
public class TeachingConfig {
}
