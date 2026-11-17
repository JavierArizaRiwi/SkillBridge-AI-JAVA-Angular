package com.riwi.skillbridge.infrastructure.adapter.out.zoom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.infrastructure.config.ZoomProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Component
public class ZoomMeetingClient {
    private final ZoomProperties properties; private final ObjectMapper mapper; private final HttpClient http = HttpClient.newHttpClient();
    private String accessToken; private Instant tokenExpiresAt = Instant.EPOCH;
    public ZoomMeetingClient(ZoomProperties properties, ObjectMapper mapper) { this.properties=properties; this.mapper=mapper; }
    public boolean configured(){ return properties.configured(); }

    public ZoomMeeting create(String topic, Instant startsAt, Instant endsAt) {
        requireConfigured();
        try {
            long duration = Math.max(1, Duration.between(startsAt, endsAt).toMinutes());
            String body = mapper.writeValueAsString(Map.of("topic", topic, "type", 2, "start_time", startsAt.toString(), "duration", duration, "timezone", "UTC", "settings", Map.of("waiting_room", true, "join_before_host", false)));
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.zoom.us/v2/users/me/meetings")).header("Authorization", "Bearer " + token()).header("Content-Type", MediaType.APPLICATION_JSON_VALUE).POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) throw new BusinessRuleException("Zoom no pudo crear la reunión ("+response.statusCode()+")");
            JsonNode node=mapper.readTree(response.body()); return new ZoomMeeting(node.path("id").asText(), node.path("password").asText(), node.path("join_url").asText());
        } catch (BusinessRuleException e) { throw e; } catch (Exception e) { throw new BusinessRuleException("No fue posible conectar con Zoom"); }
    }
    public String participantSignature(String meetingNumber) {
        requireConfigured(); Instant now=Instant.now();
        return Jwts.builder().claim("sdkKey", properties.getSdkKey()).claim("appKey", properties.getSdkKey()).claim("mn", meetingNumber).claim("role", 0).claim("tokenExp", now.plus(Duration.ofHours(2)).getEpochSecond()).issuedAt(Date.from(now)).expiration(Date.from(now.plus(Duration.ofHours(2)))).signWith(Keys.hmacShaKeyFor(properties.getSdkSecret().getBytes(StandardCharsets.UTF_8))).compact();
    }
    public String sdkKey(){return properties.getSdkKey();}
    private synchronized String token() throws Exception {
        if (accessToken != null && Instant.now().isBefore(tokenExpiresAt.minusSeconds(30))) return accessToken;
        String basic=Base64.getEncoder().encodeToString((properties.getClientId()+":"+properties.getClientSecret()).getBytes(StandardCharsets.UTF_8));
        HttpRequest request=HttpRequest.newBuilder(URI.create("https://zoom.us/oauth/token?grant_type=account_credentials&account_id="+properties.getAccountId())).header("Authorization","Basic "+basic).POST(HttpRequest.BodyPublishers.noBody()).build();
        HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()>=300) throw new BusinessRuleException("Zoom rechazó las credenciales de servidor");
        JsonNode node=mapper.readTree(response.body()); accessToken=node.path("access_token").asText(); tokenExpiresAt=Instant.now().plusSeconds(node.path("expires_in").asLong(3600)); return accessToken;
    }
    private void requireConfigured(){if(!configured()) throw new BusinessRuleException("La integración Zoom no está configurada");}
    public record ZoomMeeting(String meetingId, String passcode, String joinUrl) {}
}
