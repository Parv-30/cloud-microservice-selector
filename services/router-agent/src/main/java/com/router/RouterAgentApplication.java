package com.router;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RouterAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(RouterAgentApplication.class, args);
    }
}
