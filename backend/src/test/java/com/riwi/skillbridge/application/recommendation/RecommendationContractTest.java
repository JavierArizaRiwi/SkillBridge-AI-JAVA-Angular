package com.riwi.skillbridge.application.recommendation;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class RecommendationContractTest {
 @Test void preservesStructuredResult(){ UUID id=UUID.randomUUID(); var result=new RecommendationResult(id,"Explicación",InputType.IMAGE,"contexto",List.of(new Recommendation(UUID.randomUUID(),.9,"Razón"))); assertEquals(id,result.recommendationId()); assertEquals(InputType.IMAGE,result.inputType()); }
 @Test void rejectsInvalidContract(){ assertThrows(IllegalArgumentException.class,()->new RecommendationRequest(InputType.TEXT," ",null,UUID.randomUUID())); assertThrows(IllegalArgumentException.class,()->new Recommendation(UUID.randomUUID(),1.1,"x")); }
}
