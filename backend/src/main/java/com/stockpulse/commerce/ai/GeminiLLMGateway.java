package com.stockpulse.commerce.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Service
public class GeminiLLMGateway implements LLMGateway {
    
    @Value("${GEMINI_API_KEY:}")
    private String apiKey;
    
    @Value("${stockpulse.ai.model:gemini-pro}")
    private String model;
    
    @Value("${stockpulse.ai.timeout-ms:15000}")
    private int timeoutMs;
    
    private HttpClient httpClient;
    
    @PostConstruct
    public void init() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }
    
    @Override
    public String generate(String prompt) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new RuntimeException("GEMINI_API_KEY is not configured");
        }
        
        try {
            // Construct the Gemini API URL
            String url = String.format("https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s", 
                                     model, apiKey);
            
            // Create the request body
            String requestBody = String.format("""
                {
                  "contents": [{
                    "parts": [{
                      "text": "%s"
                    }]
                  }],
                  "generationConfig": {
                    "temperature": 0.7,
                    "topK": 40,
                    "topP": 0.95,
                    "maxOutputTokens": 1024,
                    "responseMimeType": "application/json"
                  }
                }
                """, escapeJson(prompt));
            
            // Create the HTTP request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            
            // Send the request
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                throw new RuntimeException("Gemini API returned status code: " + response.statusCode() + 
                                         ", body: " + response.body());
            }
            
            return response.body();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to call Gemini API", e);
        }
    }
    
    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
}