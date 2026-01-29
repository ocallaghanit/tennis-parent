package com.tennis.prediction.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Tennis Prediction API")
                        .version("1.0.0")
                        .description("API for predicting tennis match outcomes based on historical data, player statistics, and various prediction models.")
                        .contact(new Contact().name("Tennis Prediction").email("prediction@example.com")))
                .servers(List.of(
                        new Server().url("http://localhost:8082").description("Local Development"),
                        new Server().url("http://prediction-api:8080").description("Docker")
                ));
    }
}

