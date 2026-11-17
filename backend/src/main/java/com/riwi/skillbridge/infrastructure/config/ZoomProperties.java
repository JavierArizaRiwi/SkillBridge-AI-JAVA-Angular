package com.riwi.skillbridge.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "zoom")
public class ZoomProperties {
    private String accountId = "", clientId = "", clientSecret = "", sdkKey = "", sdkSecret = "";
    public boolean configured() { return !accountId.isBlank() && !clientId.isBlank() && !clientSecret.isBlank() && !sdkKey.isBlank() && !sdkSecret.isBlank(); }
    public String getAccountId(){return accountId;} public void setAccountId(String v){accountId=v;}
    public String getClientId(){return clientId;} public void setClientId(String v){clientId=v;}
    public String getClientSecret(){return clientSecret;} public void setClientSecret(String v){clientSecret=v;}
    public String getSdkKey(){return sdkKey;} public void setSdkKey(String v){sdkKey=v;}
    public String getSdkSecret(){return sdkSecret;} public void setSdkSecret(String v){sdkSecret=v;}
}
