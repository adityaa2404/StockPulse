package com.stockpulse.commerce;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CommerceAdvisorService {
    
    @Autowired
    private RuleBasedCommerceAdvisor ruleBasedCommerceAdvisor;
    
    @Autowired
    private GeminiCommerceAdvisor geminiCommerceAdvisor;
    
    @Value("${stockpulse.commerce.strategy:RULE}")
    private String strategy;
    
    public CommerceAdvisor getActiveAdvisor() {
        if ("AI".equalsIgnoreCase(strategy)) {
            return geminiCommerceAdvisor;
        }
        // Default to rule-based advisor
        return ruleBasedCommerceAdvisor;
    }
}