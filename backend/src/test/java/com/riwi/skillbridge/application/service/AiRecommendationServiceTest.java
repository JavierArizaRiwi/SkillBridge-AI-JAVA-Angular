package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.AiRecommendationPort;
import com.riwi.skillbridge.application.port.out.AiStructuredResponse;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.application.port.out.event.AuditEventPublisherPort;
import com.riwi.skillbridge.application.recommendation.InputType;
import com.riwi.skillbridge.application.recommendation.RecommendationRequest;
import com.riwi.skillbridge.domain.model.Offering;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AiRecommendationServiceTest {
 @Test void generatesStructuredResultAndPublishesTwoEvents() {
  AiRecommendationPort ai=mock(AiRecommendationPort.class); OfferingRepositoryPort offerings=mock(OfferingRepositoryPort.class); AuditEventPublisherPort events=mock(AuditEventPublisherPort.class);
  Offering offering=new Offering(UUID.randomUUID(),UUID.randomUUID(),"Java","Mentoría Java","BACKEND",BigDecimal.TEN,true);
  when(offerings.findAllActive()).thenReturn(List.of(offering)); 
  
  AiStructuredResponse mockResponse = new AiStructuredResponse(
      "Recomendación Gemini", 
      List.of(new AiStructuredResponse.AiItem(offering.id().toString(), 0.95, "Mock reason"))
  );
  when(ai.recommend(any(),any())).thenReturn(mockResponse);
  
  var result=new AiRecommendationService(ai,offerings,events).recommend(new RecommendationRequest(InputType.TEXT,"Aprender Java",null,UUID.randomUUID()));
  assertNotNull(result.recommendationId()); assertEquals(InputType.TEXT,result.inputType()); assertEquals(1,result.recommendations().size()); assertEquals(offering.id(),result.recommendations().getFirst().offeringId()); verify(events,times(2)).publish(any());
 }
}