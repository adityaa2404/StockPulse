package com.stockpulse.commerce;

import com.stockpulse.product.Product;
import com.stockpulse.product.ProductService;
import com.stockpulse.suggestion.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class AgenticRecommendationService {
    
    @Autowired
    private ProductService productService;
    
    @Autowired
    private CommerceAdvisorService commerceAdvisorService;
    
    @Autowired
    private PricingSuggestionRepository pricingSuggestionRepository;
    
    @Autowired
    private ReorderSuggestionRepository reorderSuggestionRepository;
    
    @Value("${stockpulse.demand-spike.multiplier:3.0}")
    private double demandSpikeMultiplier;
    
    public void generateRecommendationsForEvent(Long productId, TriggerReason triggerReason) {
        try {
            Optional<Product> optionalProduct = productService.getProductById(productId);
            if (optionalProduct.isPresent()) {
                Product product = optionalProduct.get();
                
                // Check if PENDING suggestions already exist for this product and trigger reason
                List<PricingSuggestion> existingPricingSuggestions = 
                    pricingSuggestionRepository.findByProductAndStatusAndTriggerReason(
                        product, Status.PENDING, triggerReason);
                
                List<ReorderSuggestion> existingReorderSuggestions = 
                    reorderSuggestionRepository.findByProductAndStatusAndTriggerReason(
                        product, Status.PENDING, triggerReason);
                
                // Get the active commerce advisor
                CommerceAdvisor advisor = commerceAdvisorService.getActiveAdvisor();
                
                // Generate recommendations
                CommerceAdvisor.AdvisorRecommendation recommendations;
                if (advisor instanceof GeminiCommerceAdvisor) {
                    // Use the enhanced method that takes trigger reason
                    recommendations = ((GeminiCommerceAdvisor) advisor).generateRecommendations(product, triggerReason);
                } else {
                    // Use the standard method
                    recommendations = advisor.generateRecommendations(product);
                }
                
                // Create pricing suggestion if it doesn't already exist
                if (existingPricingSuggestions.isEmpty()) {
                    CommerceAdvisor.PricingRecommendation pricingRec = recommendations.getPricing();
                    
                    PricingSuggestion pricingSuggestion = new PricingSuggestion();
                    pricingSuggestion.setProduct(product);
                    pricingSuggestion.setCurrentPrice(product.getCurrentPrice());
                    pricingSuggestion.setRecommendedPrice(BigDecimal.valueOf(pricingRec.getRecommendedPrice()));
                    pricingSuggestion.setDirection(Direction.valueOf(pricingRec.getDirection()));
                    pricingSuggestion.setConfidence(pricingRec.getConfidence());
                    pricingSuggestion.setReasoning(pricingRec.getReasoning());
                    pricingSuggestion.setStatus(Status.PENDING);
                    pricingSuggestion.setTriggerReason(triggerReason);
                    
                    pricingSuggestionRepository.save(pricingSuggestion);
                }
                
                // Create reorder suggestion if it doesn't already exist
                if (existingReorderSuggestions.isEmpty()) {
                    CommerceAdvisor.ReorderRecommendation reorderRec = recommendations.getReorder();
                    
                    ReorderSuggestion reorderSuggestion = new ReorderSuggestion();
                    reorderSuggestion.setProduct(product);
                    reorderSuggestion.setCurrentStock(product.getStockLevel());
                    reorderSuggestion.setRecommendedQuantity(reorderRec.getRecommendedQuantity());
                    reorderSuggestion.setSuggestedLeadTimeDays(reorderRec.getSuggestedLeadTimeDays());
                    reorderSuggestion.setConfidence(reorderRec.getConfidence());
                    reorderSuggestion.setReasoning(reorderRec.getReasoning());
                    reorderSuggestion.setStatus(Status.PENDING);
                    reorderSuggestion.setTriggerReason(triggerReason);
                    
                    reorderSuggestionRepository.save(reorderSuggestion);
                }
            }
        } catch (Exception e) {
            // Log the error and fall back to rule-based advisor
            e.printStackTrace();
            // In a real implementation, we would log this properly
        }
    }
}