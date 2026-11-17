package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.GenerateRecommendationUseCase;
import com.riwi.skillbridge.application.port.in.GetTrendingServicesUseCase;
import com.riwi.skillbridge.application.port.in.SynthesizeSpeechUseCase;
import com.riwi.skillbridge.application.recommendation.InputType;
import com.riwi.skillbridge.application.recommendation.RecommendationRequest;
import com.riwi.skillbridge.application.recommendation.RecommendationResult;
import com.riwi.skillbridge.application.port.out.SpeechToTextPort;
import com.riwi.skillbridge.application.port.out.VisionPort;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.AiRecommendationRequest;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.TextToSpeechRequest;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.TrendingServiceResponse;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.validation.MediaFileValidator;
import com.riwi.skillbridge.infrastructure.security.CurrentActorResolver;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai")
public class AiController {
    private final GenerateRecommendationUseCase useCase;
    private final GetTrendingServicesUseCase trendingUseCase;
    private final SpeechToTextPort speech;
    private final VisionPort vision;
    private final MediaFileValidator files;
    private final CurrentActorResolver actors;
    private final SynthesizeSpeechUseCase synthesize;

    public AiController(CurrentActorResolver actors,
                        GenerateRecommendationUseCase useCase,
                        GetTrendingServicesUseCase trendingUseCase,
                        SpeechToTextPort speech,
                        VisionPort vision,
                        MediaFileValidator files,
                        SynthesizeSpeechUseCase synthesize) {
        this.actors = actors;
        this.useCase = useCase;
        this.trendingUseCase = trendingUseCase;
        this.speech = speech;
        this.vision = vision;
        this.files = files;
        this.synthesize = synthesize;
    }

    @PostMapping("/recommendations")
    public RecommendationResult recommend(@Valid @RequestBody AiRecommendationRequest request, Authentication authentication) {
        return useCase.recommend(new RecommendationRequest(InputType.TEXT, request.goal(), null, actors.from(authentication).id()));
    }

    @PostMapping(value = "/recommendations/voice", consumes = "multipart/form-data")
    public RecommendationResult voice(@RequestPart("file") MultipartFile file, Authentication a) throws java.io.IOException {
        files.audio(file);
        return useCase.recommend(new RecommendationRequest(InputType.VOICE, speech.transcribe(file.getBytes(), file.getContentType(), file.getOriginalFilename()), null, actors.from(a).id()));
    }

    @PostMapping(value = "/recommendations/image", consumes = "multipart/form-data")
    public RecommendationResult image(@RequestPart("file") MultipartFile file, Authentication a) throws java.io.IOException {
        files.image(file);
        return useCase.recommend(new RecommendationRequest(InputType.IMAGE, null, vision.extractContext(file.getBytes(), file.getContentType(), file.getOriginalFilename()), actors.from(a).id()));
    }

    @PostMapping(value = "/tts", produces = "audio/mpeg")
    public ResponseEntity<byte[]> tts(@Valid @RequestBody TextToSpeechRequest request) {
        var audio = synthesize.synthesize(request.text());
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(audio.contentType()))
            .cacheControl(CacheControl.noStore())
            .body(audio.bytes());
    }

    @GetMapping("/trending")
    @ResponseStatus(HttpStatus.OK)
    public List<TrendingServiceResponse> trending() {
        return trendingUseCase.getTrending();
    }
}
