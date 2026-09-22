package com.aicommandcenter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AiCommandCenterApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiCommandCenterApplication.class, args);
    }
}
