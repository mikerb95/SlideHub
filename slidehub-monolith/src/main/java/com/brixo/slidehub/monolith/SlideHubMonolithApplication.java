package com.brixo.slidehub.monolith;

import com.brixo.slidehub.monolith.ratelimit.GatewayRateLimitProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.brixo.slidehub")
@EnableScheduling
@EnableConfigurationProperties(GatewayRateLimitProperties.class)
public class SlideHubMonolithApplication {

    public static void main(String[] args) {
        SpringApplication.run(SlideHubMonolithApplication.class, args);
    }
}
