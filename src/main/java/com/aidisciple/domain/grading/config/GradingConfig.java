package com.aidisciple.domain.grading.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({ScoringProperties.class, GradingProperties.class})
public class GradingConfig {
}
