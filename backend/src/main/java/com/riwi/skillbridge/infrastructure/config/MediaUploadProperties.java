package com.riwi.skillbridge.infrastructure.config;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
@Component
@ConfigurationProperties(prefix="app.ai.media")
public class MediaUploadProperties {
 private long maxAudioBytes=10*1024*1024; private long maxImageBytes=5*1024*1024;
 public long getMaxAudioBytes(){return maxAudioBytes;} public void setMaxAudioBytes(long value){maxAudioBytes=value;}
 public long getMaxImageBytes(){return maxImageBytes;} public void setMaxImageBytes(long value){maxImageBytes=value;}
}
