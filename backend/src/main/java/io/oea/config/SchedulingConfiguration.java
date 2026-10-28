package io.oea.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Scheduled tasks, e.g. the summaries of suppressed audit events (design D5 of s3-throttling-and-denials). */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfiguration {

}
