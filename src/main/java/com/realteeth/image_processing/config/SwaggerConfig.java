package com.realteeth.image_processing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                              .title("Image Processing Server API")
                              .description("이미지 처리 작업 서버 API")
                              .version("v1.0.0")
                );
    }
}
