package com.riwi.skillbridge.infrastructure.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiFallbackValidatorTest {

    private AiFallbackValidator validator;
    private AiFallbackProperties properties;

    @BeforeEach
    void setUp() {
        validator = new AiFallbackValidator();
        properties = new AiFallbackProperties();
    }

    @Test
    @DisplayName("Default valid configuration passes validation")
    void shouldPassValidationWithDefaults() {
        assertThatCode(() -> validator.validate(properties))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Scenario 1.4: Fail-fast when timeout is <= 0")
    void shouldFailFastWhenTimeoutIsZeroOrNegative() {
        properties.getChat().getChain().get(0).setTimeoutSeconds(0);

        assertThatThrownBy(() -> validator.validate(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("timeout")
                .hasMessageContaining("chat")
                .hasMessageContaining("<= 0");

        properties.getChat().getChain().get(0).setTimeoutSeconds(-5);

        assertThatThrownBy(() -> validator.validate(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("timeout");
    }

    @Test
    @DisplayName("Scenario 1.5: Fail-fast when an active capability chain is empty")
    void shouldFailFastWhenChainIsEmpty() {
        properties.getVision().getChain().clear();

        assertThatThrownBy(() -> validator.validate(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("vision")
                .hasMessageContaining("empty");
    }

    @Test
    @DisplayName("Scenario 1.5: Fail-fast when all candidates in a chain are disabled")
    void shouldFailFastWhenAllCandidatesAreDisabled() {
        for (AiFallbackProperties.ChainElementConfig candidate : properties.getChat().getChain()) {
            candidate.setEnabled(false);
        }

        assertThatThrownBy(() -> validator.validate(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("chat")
                .hasMessageContaining("no enabled candidates");
    }

    @Test
    @DisplayName("Scenario 1.4: Fail-fast when provider name is unsupported or unknown")
    void shouldFailFastWhenProviderIsUnknown() {
        properties.getChat().getChain().add(new AiFallbackProperties.ChainElementConfig(
                "unsupported-vendor",
                "model-x",
                true,
                10
        ));

        assertThatThrownBy(() -> validator.validate(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unsupported-vendor")
                .hasMessageContaining("Unsupported AI provider");
    }

    @Test
    @DisplayName("Fail-fast when provider or model is blank or null")
    void shouldFailFastWhenProviderOrModelIsBlank() {
        properties.getChat().getChain().add(new AiFallbackProperties.ChainElementConfig(
                "",
                "model-x",
                true,
                10
        ));

        assertThatThrownBy(() -> validator.validate(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("provider name must not be blank");
    }
}
