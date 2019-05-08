package com.ringoid.graphaware;

import com.codahale.metrics.MetricRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Picked up by GraphAware classpath scanning (because it lived in a package containing the String "graphaware" on path).
 */
@Configuration
public class SpringConfig {

    @Bean
    public MetricRegistry metrics() {
        return new MetricRegistry();
    }

}