package com.codebymike.slidehub.monolith;

import com.codebymike.slidehub.monolith.ratelimit.GatewayRateLimitProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.codebymike.slidehub")
@EnableScheduling
@EnableConfigurationProperties(GatewayRateLimitProperties.class)
@EnableJpaRepositories(basePackages = "com.codebymike.slidehub.ui.repository")
@EnableMongoRepositories(basePackages = "com.codebymike.slidehub.ai.repository")
@EntityScan(basePackages = "com.codebymike.slidehub.ui.model")
public class SlideHubMonolithApplication {

    public static void main(String[] args) {
        SpringApplication.run(SlideHubMonolithApplication.class, args);
    }
}
