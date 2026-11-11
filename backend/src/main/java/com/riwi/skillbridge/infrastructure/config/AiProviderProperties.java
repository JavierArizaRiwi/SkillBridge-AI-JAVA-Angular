package com.riwi.skillbridge.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiProviderProperties {
    
    private String provider = "gemini";
    private int timeoutSeconds = 10;
    private GeminiProperties gemini = new GeminiProperties();
    private OpenAiProperties openai = new OpenAiProperties();
    
    public String getProvider() {
        return provider;
    }
    
    public void setProvider(String provider) {
        this.provider = provider;
    }
    
    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }
    
    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
    
    public GeminiProperties getGemini() {
        return gemini;
    }
    
    public void setGemini(GeminiProperties gemini) {
        this.gemini = gemini;
    }
    
    public OpenAiProperties getOpenai() {
        return openai;
    }
    
    public void setOpenai(OpenAiProperties openai) {
        this.openai = openai;
    }
    
    public static class GeminiProperties {
        private boolean enabled = true;
        
        public boolean isEnabled() {
            return enabled;
        }
        
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
    
    public static class OpenAiProperties {
        private boolean enabled = false;
        
        public boolean isEnabled() {
            return enabled;
        }
        
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
