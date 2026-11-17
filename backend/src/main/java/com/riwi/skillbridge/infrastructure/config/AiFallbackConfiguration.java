package com.riwi.skillbridge.infrastructure.config;

import com.riwi.skillbridge.infrastructure.adapter.out.ai.ElevenLabsTextToSpeechAdapter;
import com.riwi.skillbridge.infrastructure.adapter.out.ai.GeminiAiAdapter;
import com.riwi.skillbridge.infrastructure.adapter.out.ai.GeminiSpeechToTextAdapter;
import com.riwi.skillbridge.infrastructure.adapter.out.ai.GeminiVisionAdapter;
import com.riwi.skillbridge.infrastructure.adapter.out.ai.GroqSpeechToTextAdapter;
import com.riwi.skillbridge.infrastructure.adapter.out.ai.NoOpTextToSpeechAdapter;
import com.riwi.skillbridge.infrastructure.adapter.out.ai.OpenAiAiAdapter;
import com.riwi.skillbridge.infrastructure.adapter.out.ai.candidate.AiModelCandidate;
import com.riwi.skillbridge.infrastructure.adapter.out.ai.fallback.FallbackAiRecommendationAdapter;
import com.riwi.skillbridge.infrastructure.adapter.out.ai.fallback.FallbackSpeechToTextAdapter;
import com.riwi.skillbridge.infrastructure.adapter.out.ai.fallback.FallbackTextToSpeechAdapter;
import com.riwi.skillbridge.infrastructure.adapter.out.ai.fallback.FallbackVisionAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring configuration for AI fallback chains across all capabilities.
 * Resolves active candidates dynamically based on typed {@link AiFallbackProperties},
 * validates configuration at boot (fail-fast), and safely omits unconfigured providers without leaking secrets.
 */
@Configuration
public class AiFallbackConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AiFallbackConfiguration.class);

    @Value("${spring.ai.google.genai.api-key:${GEMINI_API_KEY:}}")
    private String geminiApiKey;

    @Value("${spring.ai.openai.api-key:${OPENAI_API_KEY:}}")
    private String openAiApiKey;

    @Value("${app.ai.groq.api-key:${GROQ_API_KEY:}}")
    private String groqApiKey;

    @Value("${elevenlabs.api-key:${ELEVENLABS_API_KEY:}}")
    private String elevenLabsApiKey;

    @Autowired
    public void validateOnStartup(AiFallbackProperties fallbackProperties, AiFallbackValidator fallbackValidator) {
        fallbackValidator.validate(fallbackProperties);
    }

    @Bean
    public List<FallbackAiRecommendationAdapter.CandidateExecutor> recommendationCandidates(
            ObjectProvider<GeminiAiAdapter> geminiAdapterProvider,
            ObjectProvider<OpenAiAiAdapter> openAiAdapterProvider,
            AiFallbackProperties fallbackProperties
    ) {
        List<FallbackAiRecommendationAdapter.CandidateExecutor> candidates = new ArrayList<>();
        List<AiFallbackProperties.ChainElementConfig> chain = fallbackProperties.getChat().getChain();

        int priority = 1;
        for (AiFallbackProperties.ChainElementConfig element : chain) {
            if (!element.isEnabled()) {
                continue;
            }
            if ("gemini".equalsIgnoreCase(element.getProvider())) {
                GeminiAiAdapter gemini = geminiAdapterProvider.getIfAvailable();
                if (gemini != null) {
                    boolean hasKey = geminiApiKey != null && !geminiApiKey.isBlank();
                    if (hasKey) {
                        candidates.add(new FallbackAiRecommendationAdapter.CandidateExecutor(
                                AiModelCandidate.of("gemini", element.getModel(), priority++, element.getTimeoutSeconds()),
                                gemini
                        ));
                    } else {
                        log.warn("AI recommendation candidate [gemini:{}] omitted at startup: API key is not configured", element.getModel());
                    }
                }
            } else if ("openai".equalsIgnoreCase(element.getProvider())) {
                OpenAiAiAdapter openAi = openAiAdapterProvider.getIfAvailable();
                if (openAi != null) {
                    boolean hasKey = openAiApiKey != null && !openAiApiKey.isBlank();
                    if (hasKey) {
                        candidates.add(new FallbackAiRecommendationAdapter.CandidateExecutor(
                                AiModelCandidate.of("openai", element.getModel(), priority++, element.getTimeoutSeconds()),
                                openAi
                        ));
                    } else {
                        log.warn("AI recommendation candidate [openai:{}] omitted at startup: API key is not configured", element.getModel());
                    }
                }
            }
        }

        return candidates;
    }

    @Bean
    public List<FallbackSpeechToTextAdapter.CandidateExecutor> speechToTextCandidates(
            ObjectProvider<GroqSpeechToTextAdapter> groqProvider,
            ObjectProvider<GeminiSpeechToTextAdapter> geminiProvider,
            AiFallbackProperties fallbackProperties
    ) {
        List<FallbackSpeechToTextAdapter.CandidateExecutor> candidates = new ArrayList<>();
        List<AiFallbackProperties.ChainElementConfig> chain = fallbackProperties.getStt().getChain();

        int priority = 1;
        for (AiFallbackProperties.ChainElementConfig element : chain) {
            if (!element.isEnabled()) {
                continue;
            }
            if ("groq".equalsIgnoreCase(element.getProvider())) {
                GroqSpeechToTextAdapter groq = groqProvider.getIfAvailable();
                if (groq != null) {
                    boolean hasKey = groqApiKey != null && !groqApiKey.isBlank();
                    if (hasKey) {
                        candidates.add(new FallbackSpeechToTextAdapter.CandidateExecutor(
                                AiModelCandidate.of("groq", element.getModel(), priority++, element.getTimeoutSeconds()),
                                groq
                        ));
                    } else {
                        log.warn("STT candidate [groq:{}] omitted at startup: API key is not configured", element.getModel());
                    }
                }
            } else if ("gemini".equalsIgnoreCase(element.getProvider())) {
                GeminiSpeechToTextAdapter gemini = geminiProvider.getIfAvailable();
                if (gemini != null) {
                    boolean hasKey = geminiApiKey != null && !geminiApiKey.isBlank();
                    if (hasKey) {
                        candidates.add(new FallbackSpeechToTextAdapter.CandidateExecutor(
                                AiModelCandidate.of("gemini", element.getModel(), priority++, element.getTimeoutSeconds()),
                                gemini
                        ));
                    } else {
                        log.warn("STT candidate [gemini:{}] omitted at startup: API key is not configured", element.getModel());
                    }
                }
            }
        }

        return candidates;
    }

    @Bean
    public List<FallbackVisionAdapter.CandidateExecutor> visionCandidates(
            ObjectProvider<GeminiVisionAdapter> geminiVisionProvider,
            AiFallbackProperties fallbackProperties
    ) {
        List<FallbackVisionAdapter.CandidateExecutor> candidates = new ArrayList<>();
        List<AiFallbackProperties.ChainElementConfig> chain = fallbackProperties.getVision().getChain();

        int priority = 1;
        for (AiFallbackProperties.ChainElementConfig element : chain) {
            if (!element.isEnabled()) {
                continue;
            }
            if ("gemini".equalsIgnoreCase(element.getProvider())) {
                GeminiVisionAdapter gemini = geminiVisionProvider.getIfAvailable();
                if (gemini != null) {
                    boolean hasKey = geminiApiKey != null && !geminiApiKey.isBlank();
                    if (hasKey) {
                        candidates.add(new FallbackVisionAdapter.CandidateExecutor(
                                AiModelCandidate.of("gemini", element.getModel(), priority++, element.getTimeoutSeconds()),
                                gemini
                        ));
                    } else {
                        log.warn("Vision candidate [gemini:{}] omitted at startup: API key is not configured", element.getModel());
                    }
                }
            }
        }

        return candidates;
    }

    @Bean
    public List<FallbackTextToSpeechAdapter.CandidateExecutor> textToSpeechCandidates(
            ObjectProvider<ElevenLabsTextToSpeechAdapter> elevenLabsProvider,
            ObjectProvider<NoOpTextToSpeechAdapter> noOpProvider,
            AiFallbackProperties fallbackProperties
    ) {
        List<FallbackTextToSpeechAdapter.CandidateExecutor> candidates = new ArrayList<>();
        List<AiFallbackProperties.ChainElementConfig> chain = fallbackProperties.getTts().getChain();

        int priority = 1;
        for (AiFallbackProperties.ChainElementConfig element : chain) {
            if (!element.isEnabled()) {
                continue;
            }
            if ("elevenlabs".equalsIgnoreCase(element.getProvider())) {
                ElevenLabsTextToSpeechAdapter elevenLabs = elevenLabsProvider.getIfAvailable();
                if (elevenLabs != null) {
                    boolean hasKey = elevenLabsApiKey != null && !elevenLabsApiKey.isBlank();
                    if (hasKey) {
                        candidates.add(new FallbackTextToSpeechAdapter.CandidateExecutor(
                                AiModelCandidate.of("elevenlabs", element.getModel(), priority++, element.getTimeoutSeconds()),
                                elevenLabs
                        ));
                    } else {
                        log.warn("TTS candidate [elevenlabs:{}] omitted at startup: API key is not configured", element.getModel());
                    }
                }
            } else if ("noop".equalsIgnoreCase(element.getProvider())) {
                NoOpTextToSpeechAdapter noOp = noOpProvider.getIfAvailable();
                if (noOp != null) {
                    candidates.add(new FallbackTextToSpeechAdapter.CandidateExecutor(
                            AiModelCandidate.of("noop", element.getModel(), priority++, element.getTimeoutSeconds()),
                            noOp
                    ));
                }
            }
        }

        return candidates;
    }
}
