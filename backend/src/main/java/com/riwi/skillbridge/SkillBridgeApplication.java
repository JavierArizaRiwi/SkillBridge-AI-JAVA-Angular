package com.riwi.skillbridge;

import com.riwi.skillbridge.infrastructure.config.ElevenLabsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;

// ElevenLabsProperties es un record inmutable y requiere registro explícito.
@SpringBootApplication
@EnableFeignClients
@EnableConfigurationProperties(ElevenLabsProperties.class)
public class SkillBridgeApplication {
    public static void main(String[] args) {
        SpringApplication.run(SkillBridgeApplication.class, args);
    }
}