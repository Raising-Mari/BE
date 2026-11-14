package com.aidisciple.domain.question.config;


import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(QuestionProperties.class)
public class QuestionConfig {
}
