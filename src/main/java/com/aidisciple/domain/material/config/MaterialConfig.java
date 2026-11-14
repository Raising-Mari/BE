package com.aidisciple.domain.material.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MaterialProperties.class)
public class MaterialConfig {
}
