package com.stockpulse.commerce;

import com.stockpulse.product.Product;
import com.stockpulse.suggestion.Direction;
import com.stockpulse.suggestion.PricingSuggestion;
import com.stockpulse.suggestion.ReorderSuggestion;
import com.stockpulse.suggestion.TriggerReason;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;

@Service
public class CommerceService {
    
    @Autowired
    private CommerceAdvisorService commerceAdvisorService;
    
    public PricingSuggestion generatePricingSuggestion(Product product) {
        // Get the active commerce advisor
        CommerceAdvisor advisor = commerceAdvisorService.getActiveAdvisor();
        
        // Generate recommendations
        CommerceAdvisor.AdvisorRecommendation recommendations = advisor.generateRecommendations(product);
        CommerceAdvisor.PricingRecommendation pricingRec = recommendations.getPricing();
        
        return new PricingSuggestion(
            product,
            product.getCurrentPrice(),
            BigDecimal.valueOf(pricingRec.getRecommendedPrice()),
            Direction.valueOf(pricingRec.getDirection()),
            pricingRec.getConfidence(),
            pricingRec.getReasoning(),
            TriggerReason.MANUAL
        );
    }
    
    public ReorderSuggestion generateReorderSuggestion(Product product) {
        // Get the active commerce advisor
        CommerceAdvisor advisor = commerceAdvisorService.getActiveAdvisor();
        
        // Generate recommendations
        CommerceAdvisor.AdvisorRecommendation recommendations = advisor.generateRecommendations(product);
        CommerceAdvisor.ReorderRecommendation reorderRec = recommendations.getReorder();
        
        return new ReorderSuggestion(
            product,
            product.getStockLevel(),
            reorderRec.getRecommendedQuantity(),
            reorderRec.getSuggestedLeadTimeDays(),
            reorderRec.getConfidence(),
            reorderRec.getReasoning(),
            TriggerReason.MANUAL
        );
    }
}