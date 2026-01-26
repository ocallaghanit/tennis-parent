package com.tennis.adapter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.tennis.adapter.config.ApiTennisProperties;

@SpringBootApplication
@EnableConfigurationProperties(ApiTennisProperties.class)
public class ApiTennisSimpleAdapterApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiTennisSimpleAdapterApplication.class, args);
    }
}

