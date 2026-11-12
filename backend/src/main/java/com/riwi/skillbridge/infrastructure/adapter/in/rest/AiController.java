package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.GenerateRecommendationUseCase;
import com.riwi.skillbridge.application.recommendation.InputType;
import com.riwi.skillbridge.application.recommendation.RecommendationRequest;
import com.riwi.skillbridge.application.recommendation.RecommendationResult;
import com.riwi.skillbridge.application.port.out.SpeechToTextPort;
import com.riwi.skillbridge.application.port.out.VisionPort;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.validation.MediaFileValidator;
import com.riwi.skillbridge.infrastructure.security.CurrentActorResolver;
import org.springframework.web.multipart.MultipartFile;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.AiRecommendationRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai")
public class AiController {
    private final GenerateRecommendationUseCase useCase; private final SpeechToTextPort speech; private final VisionPort vision; private final MediaFileValidator files; private final CurrentActorResolver actors;
    public AiController(GenerateRecommendationUseCase useCase, SpeechToTextPort speech, VisionPort vision, MediaFileValidator files, CurrentActorResolver actors) { this.useCase=useCase; this.speech=speech; this.vision=vision; this.files=files; this.actors=actors; }

    @PostMapping("/recommendations")
    public RecommendationResult recommend(@Valid @RequestBody AiRecommendationRequest request, Authentication authentication) {
        return useCase.recommend(new RecommendationRequest(InputType.TEXT, request.goal(), null, actors.from(authentication).id()));
    }

    @PostMapping(value="/recommendations/voice", consumes="multipart/form-data") public RecommendationResult voice(@RequestPart("file") MultipartFile file, Authentication a) throws java.io.IOException { files.audio(file); return useCase.recommend(new RecommendationRequest(InputType.VOICE,speech.transcribe(file.getBytes(),file.getContentType(),file.getOriginalFilename()),null,actors.from(a).id())); }
    @PostMapping(value="/recommendations/image", consumes="multipart/form-data") public RecommendationResult image(@RequestPart("file") MultipartFile file, Authentication a) throws java.io.IOException { files.image(file); return useCase.recommend(new RecommendationRequest(InputType.IMAGE,null,vision.extractContext(file.getBytes(),file.getContentType(),file.getOriginalFilename()),actors.from(a).id())); }
}
