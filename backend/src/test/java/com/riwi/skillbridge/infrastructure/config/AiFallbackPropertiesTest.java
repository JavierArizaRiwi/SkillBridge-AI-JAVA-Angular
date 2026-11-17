package com.riwi.skillbridge.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiFallbackPropertiesTest {

    @Test
    @DisplayName("Scenario 1.1: Default properties load valid fallback chains across capabilities")
    void shouldLoadValidDefaultChains() {
        AiFallbackProperties properties = new AiFallbackProperties();

        assertThat(properties.getChat().getChain()).hasSize(2);
        assertThat(properties.getChat().getChain().get(0).getProvider()).isEqualTo("gemini");
        assertThat(properties.getChat().getChain().get(0).getModel()).isEqualTo("gemini-3.8-flash");
        assertThat(properties.getChat().getChain().get(0).getTimeoutSeconds()).isEqualTo(10);
        assertThat(properties.getChat().getChain().get(0).isEnabled()).isTrue();

        assertThat(properties.getChat().getChain().get(1).getProvider()).isEqualTo("openai");
        assertThat(properties.getChat().getChain().get(1).getModel()).isEqualTo("gpt-4o-mini");

        assertThat(properties.getVision().getChain()).isNotEmpty();
        assertThat(properties.getStt().getChain()).isNotEmpty();
        assertThat(properties.getTts().getChain()).isNotEmpty();
        assertThat(properties.getRag().getChain()).isNotEmpty();
        assertThat(properties.getEmbedding().getChain()).isNotEmpty();
    }

    @Test
    @DisplayName("Scenario 1.2: Parse compact chain with redundant whitespace")
    void shouldParseCompactChainWithRedundantWhitespace() {
        String compact = "  gemini : gemini-3.8-flash ,  openai : gpt-4o-mini  ";
        List<AiFallbackProperties.ChainElementConfig> parsed = AiFallbackProperties.parseCompactChain(compact, 15);

        assertThat(parsed).hasSize(2);
        assertThat(parsed.get(0).getProvider()).isEqualTo("gemini");
        assertThat(parsed.get(0).getModel()).isEqualTo("gemini-3.8-flash");
        assertThat(parsed.get(0).getTimeoutSeconds()).isEqualTo(15);
        assertThat(parsed.get(0).isEnabled()).isTrue();

        assertThat(parsed.get(1).getProvider()).isEqualTo("openai");
        assertThat(parsed.get(1).getModel()).isEqualTo("gpt-4o-mini");
        assertThat(parsed.get(1).getTimeoutSeconds()).isEqualTo(15);
    }

    @Test
    @DisplayName("Compact chain parsing throws on invalid format")
    void shouldThrowOnInvalidCompactChain() {
        assertThatThrownBy(() -> AiFallbackProperties.parseCompactChain("invalid_without_colon", 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid compact chain element format");
    }

    @Test
    @DisplayName("Scenario 1.2: Environment compact override takes precedence on initialization")
    void shouldOverrideChainFromEnvironmentVariable() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("AI_CHAT_FALLBACK_CHAIN", "openai:gpt-4o, gemini:gemini-3.8-flash-lite");

        AiFallbackProperties properties = new AiFallbackProperties();
        properties.setEnvironment(env);
        properties.initFromEnvironment();

        List<AiFallbackProperties.ChainElementConfig> chatChain = properties.getChat().getChain();
        assertThat(chatChain).hasSize(2);
        assertThat(chatChain.get(0).getProvider()).isEqualTo("openai");
        assertThat(chatChain.get(0).getModel()).isEqualTo("gpt-4o");
        assertThat(chatChain.get(1).getProvider()).isEqualTo("gemini");
        assertThat(chatChain.get(1).getModel()).isEqualTo("gemini-3.8-flash-lite");
    }

    @Test
    @DisplayName("Scenario 1.3: Backward compatibility with legacy AI_PROVIDER")
    void shouldSupportLegacyProviderWhenChainIsEmpty() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("AI_PROVIDER", "openai");
        env.setProperty("AI_AUDIO_PROVIDER", "groq");
        env.setProperty("AI_TIMEOUT_SECONDS", "25");

        AiFallbackProperties properties = new AiFallbackProperties();
        // Clear default chains to simulate environment with no explicit advanced fallback config
        properties.getChat().getChain().clear();
        properties.getStt().getChain().clear();

        properties.setEnvironment(env);
        properties.initFromEnvironment();

        assertThat(properties.getChat().getChain()).hasSize(1);
        assertThat(properties.getChat().getChain().get(0).getProvider()).isEqualTo("openai");
        assertThat(properties.getChat().getChain().get(0).getModel()).isEqualTo("gpt-4o-mini");
        assertThat(properties.getChat().getChain().get(0).getTimeoutSeconds()).isEqualTo(25);

        assertThat(properties.getStt().getChain()).hasSize(1);
        assertThat(properties.getStt().getChain().get(0).getProvider()).isEqualTo("groq");
        assertThat(properties.getStt().getChain().get(0).getModel()).isEqualTo("whisper-large-v3-turbo");
    }
}
