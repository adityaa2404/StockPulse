package com.stockpulse.commerce;

import com.stockpulse.product.Product;

public interface CommerceAdvisor {
    
    AdvisorRecommendation generateRecommendations(Product product);
    
    class AdvisorRecommendation {
        private final PricingRecommendation pricing;
        private final ReorderRecommendation reorder;
        
        public AdvisorRecommendation(PricingRecommendation pricing, ReorderRecommendation reorder) {
            this.pricing = pricing;
            this.reorder = reorder;
        }
        
        public PricingRecommendation getPricing() {
            return pricing;
        }
        
        public ReorderRecommendation getReorder() {
            return reorder;
        }
    }
    
    class PricingRecommendation {
        private final double recommendedPrice;
        private final String direction;
        private final double confidence;
        private final String reasoning;
        
        public PricingRecommendation(double recommendedPrice, String direction, double confidence, String reasoning) {
            this.recommendedPrice = recommendedPrice;
            this.direction = direction;
            this.confidence = confidence;
            this.reasoning = reasoning;
        }
        
        public double getRecommendedPrice() {
            return recommendedPrice;
        }
        
        public String getDirection() {
            return direction;
        }
        
        public double getConfidence() {
            return confidence;
        }
        
        public String getReasoning() {
            return reasoning;
        }
    }
    
    class ReorderRecommendation {
        private final int recommendedQuantity;
        private final int suggestedLeadTimeDays;
        private final double confidence;
        private final String reasoning;
        
        public ReorderRecommendation(int recommendedQuantity, int suggestedLeadTimeDays, double confidence, String reasoning) {
            this.recommendedQuantity = recommendedQuantity;
            this.suggestedLeadTimeDays = suggestedLeadTimeDays;
            this.confidence = confidence;
            this.reasoning = reasoning;
        }
        
        public int getRecommendedQuantity() {
            return recommendedQuantity;
        }
        
        public int getSuggestedLeadTimeDays() {
            return suggestedLeadTimeDays;
        }
        
        public double getConfidence() {
            return confidence;
        }
        
        public String getReasoning() {
            return reasoning;
        }
    }
}