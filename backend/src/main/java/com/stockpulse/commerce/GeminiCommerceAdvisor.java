package com.stockpulse.commerce;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpulse.product.Product;
import com.stockpulse.suggestion.TriggerReason;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class GeminiCommerceAdvisor implements CommerceAdvisor {
    @Autowired private RuleBasedCommerceAdvisor fallback;
    @Value("$"+"{stockpulse.ai.model:gemini-2.5-flash}") private String model;
    @Value("$"+"{stockpulse.ai.timeout-ms:15000}") private long timeoutMs;

    @Override public AdvisorRecommendation generateRecommendations(Product p) {
        return generateRecommendations(p, TriggerReason.MANUAL);
    }

    public AdvisorRecommendation generateRecommendations(Product p, TriggerReason trigger) {
        String key = System.getenv("GEMINI_API_KEY");
        if (key == null || key.isBlank()) return fallback.generateRecommendations(p);
        try {
            ObjectMapper m = new ObjectMapper();
            String prompt = "Return JSON: recommendedPrice,direction,pricingConfidence,pricingReasoning," +
                    "recommendedQuantity,leadTimeDays,reorderConfidence,reorderReasoning. " +
                    "Product="+p.getName()+", category="+p.getCategory()+", price="+p.getCurrentPrice()+
                    ", stock="+p.getStockLevel()+", threshold="+p.getReorderThreshold()+
                    ", velocity="+p.getDemandVelocity()+", trigger="+trigger+
                    ". Keep price within 50%-200% of current and confidence 0-1.";
            String body = "{\"contents\":[{\"parts\":[{\"text\":"+m.writeValueAsString(prompt)+"}]}]," +
                    "\"generationConfig\":{\"temperature\":0.2,\"responseMimeType\":\"application/json\"}}";
            String url = "https://generativelanguage.googleapis.com/v1beta/models/"+model+
                    ":generateContent?key="+key;
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url))
                    .timeout(Duration.ofMillis(Math.max(timeoutMs,1000)))
                    .header("Content-Type","application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> res = HttpClient.newHttpClient().send(req,HttpResponse.BodyHandlers.ofString());
            if (res.statusCode()<200 || res.statusCode()>=300) return fallback.generateRecommendations(p);
            JsonNode a=m.readTree(res.body()).path("candidates").path(0).path("content").path("parts").path(0);
            String raw=a.path("text").asText("");
            if(raw.isBlank()) return fallback.generateRecommendations(p);
            JsonNode j=m.readTree(raw.trim());
            double price=j.path("recommendedPrice").asDouble(-1);
            String dir=j.path("direction").asText("HOLD");
            double pc=j.path("pricingConfidence").asDouble(-1);
            int qty=j.path("recommendedQuantity").asInt(-1);
            int lead=j.path("leadTimeDays").asInt(-1);
            double rc=j.path("reorderConfidence").asDouble(-1);
            String pr=j.path("pricingReasoning").asText("");
            String rr=j.path("reorderReasoning").asText("");
            double cur=p.getCurrentPrice().doubleValue();
            if(price<=0 || price>cur*2 || price<cur*.5 || pc<0 || pc>1 || rc<0 || rc>1 ||
               qty<=0 || lead<=0 || pr.isBlank() || rr.isBlank()) return fallback.generateRecommendations(p);
            if(!dir.equals("INCREASE") && !dir.equals("DECREASE") && !dir.equals("HOLD"))
                return fallback.generateRecommendations(p);
            return new AdvisorRecommendation(new PricingRecommendation(price,dir,pc,pr),
                    new ReorderRecommendation(qty,lead,rc,rr));
        } catch(Exception e) {
            return fallback.generateRecommendations(p);
        }
    }
}