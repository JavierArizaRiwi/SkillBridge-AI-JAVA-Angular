package com.riwi.skillbridge.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayName("KnowledgeProperties Unit and Binding Tests")
class KnowledgePropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Configuration
    @EnableConfigurationProperties(KnowledgeProperties.class)
    static class TestConfig {
    }

    @Test
    @DisplayName("Should initialize with correct default values for fields with defaults")
    void shouldInitializeWithDefaultValuesForFieldsWithDefaults() {
        KnowledgeProperties properties = new KnowledgeProperties();

        assertAll(
                () -> assertThat(properties.getTopK())
                        .as("topK default")
                        .isEqualTo(3),
                () -> assertThat(properties.getTimeoutSeconds())
                        .as("timeoutSeconds default")
                        .isEqualTo(10),
                () -> assertThat(properties.getMaxContextChars())
                        .as("maxContextChars default")
                        .isEqualTo(6000),
                () -> assertThat(properties.getRrfK())
                        .as("rrfK default")
                        .isEqualTo(60),
                () -> assertThat(properties.getMinScoreThreshold())
                        .as("minScoreThreshold must not have an invented default (pending FR-019 calibration)")
                        .isNull()
        );
    }

    @Test
    @DisplayName("Should allow updating and retrieving all fields via getters and setters")
    void shouldAllowUpdatingAllFields() {
        KnowledgeProperties properties = new KnowledgeProperties();

        properties.setTopK(10);
        properties.setMinScoreThreshold(0.78);
        properties.setTimeoutSeconds(15);
        properties.setMaxContextChars(4000);
        properties.setRrfK(50);

        assertAll(
                () -> assertThat(properties.getTopK()).isEqualTo(10),
                () -> assertThat(properties.getMinScoreThreshold()).isEqualTo(0.78),
                () -> assertThat(properties.getTimeoutSeconds()).isEqualTo(15),
                () -> assertThat(properties.getMaxContextChars()).isEqualTo(4000),
                () -> assertThat(properties.getRrfK()).isEqualTo(50)
        );
    }

    @Test
    @DisplayName("Should bind configuration properties from environment or YAML under app.rag prefix")
    void shouldBindFromConfigurationProperties() {
        contextRunner
                .withPropertyValues(
                        "app.rag.top-k=5",
                        "app.rag.min-score-threshold=0.72",
                        "app.rag.timeout-seconds=20",
                        "app.rag.max-context-chars=8000",
                        "app.rag.rrf-k=40"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(KnowledgeProperties.class);

                    KnowledgeProperties properties = context.getBean(KnowledgeProperties.class);
                    assertAll(
                            () -> assertThat(properties.getTopK()).isEqualTo(5),
                            () -> assertThat(properties.getMinScoreThreshold()).isEqualTo(0.72),
                            () -> assertThat(properties.getTimeoutSeconds()).isEqualTo(20),
                            () -> assertThat(properties.getMaxContextChars()).isEqualTo(8000),
                            () -> assertThat(properties.getRrfK()).isEqualTo(40)
                    );
                });
    }

    @Test
    @DisplayName("Should preserve unconfigured minScoreThreshold as null when not specified in properties")
    void shouldKeepMinScoreThresholdNullWhenUnspecified() {
        contextRunner
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    KnowledgeProperties properties = context.getBean(KnowledgeProperties.class);

                    assertThat(properties.getMinScoreThreshold()).isNull();
                    assertThat(properties.getTopK()).isEqualTo(3);
                    assertThat(properties.getTimeoutSeconds()).isEqualTo(10);
                    assertThat(properties.getMaxContextChars()).isEqualTo(6000);
                    assertThat(properties.getRrfK()).isEqualTo(60);
                });
    }

    @Test
    @DisplayName("Should load application.yml values, with the provisional 0.5 threshold pending calibration (T030)")
    void shouldLoadValuesFromApplicationYml() {
        new ApplicationContextRunner()
                .withUserConfiguration(TestConfig.class)
                .withInitializer(new org.springframework.boot.test.context.ConfigDataApplicationContextInitializer())
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    KnowledgeProperties properties = context.getBean(KnowledgeProperties.class);

                    assertAll(
                            () -> assertThat(properties.getTopK()).isEqualTo(3),
                            () -> assertThat(properties.getMinScoreThreshold()).isEqualTo(0.5),
                            () -> assertThat(properties.getTimeoutSeconds()).isEqualTo(10),
                            () -> assertThat(properties.getMaxContextChars()).isEqualTo(6000),
                            () -> assertThat(properties.getRrfK()).isEqualTo(60)
                    );
                });
    }
}
