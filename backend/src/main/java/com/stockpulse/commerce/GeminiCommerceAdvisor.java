package com.stockpulse.commerce;

import com.stockpulse.product.Product;
import com.stockpulse.suggestion.TriggerReason;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GeminiCommerceAdvisor implements CommerceAdvisor {
    
    @Autowired
    private RuleBasedCommerceAdvisor ruleBasedAdvisor;
    
    @Override
    public AdvisorRecommendation generateRecommendations(Product product) {
        // Delegate to rule-based advisor for now
        return ruleBasedAdvisor.generateRecommendations(product);
    }
    
    public AdvisorRecommendation generateRecommendations(Product product, TriggerReason triggerReason) {
        // Delegate to rule-based advisor for now
        return ruleBasedAdvisor.generateRecommendations(product);
    }
}