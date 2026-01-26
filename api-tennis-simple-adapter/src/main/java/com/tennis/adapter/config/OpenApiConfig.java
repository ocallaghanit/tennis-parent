package com.tennis.adapter.config;

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
    public OpenAPI apiTennisOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("API Tennis Simple Adapter")
                        .description("REST API for ingesting and querying tennis data from API Tennis. " +
                                "Data is stored in MongoDB with raw JSON preserved.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Tennis Adapter")
                                .email("admin@example.com")))
                .servers(List.of(
                        new Server().url("http://localhost:8081").description("Docker"),
                        new Server().url("http://localhost:8080").description("Local Dev")
                ));
    }
}

