package com.realteeth.image_processing.config;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.realteeth.image_processing.client.ImageWorkerProperties;

@Configuration
@EnableConfigurationProperties(ImageWorkerProperties.class)
public class RestClientConfig {

    @Bean
    public RestClient imageWorkerRestClient(ImageWorkerProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder()
                         .baseUrl(props.baseUrl())
                         .requestFactory(factory)
                         .build();
    }
}
