package com.stockpulse.commerce;

import com.stockpulse.product.Category;
import com.stockpulse.product.Product;
import com.stockpulse.product.ProductRepository;
import com.stockpulse.suggestion.Direction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;

@Service
public class RuleBasedCommerceAdvisor implements CommerceAdvisor {
    
    @Autowired
    private ProductRepository productRepository;
    
    // Setter for fallback mechanism
    public void setProductRepository(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }
    
    @Override
    public AdvisorRecommendation generateRecommendations(Product product) {
        PricingRecommendation pricing = generatePricingRecommendation(product);
        ReorderRecommendation reorder = generateReorderRecommendation(product);
        return new AdvisorRecommendation(pricing, reorder);
    }
    
    private PricingRecommendation generatePricingRecommendation(Product product) {
        BigDecimal currentPrice = product.getCurrentPrice();
        double recommendedPrice = currentPrice.doubleValue();
        String direction = "HOLD";
        String reasoning = "";
        
        // Rule 1: If stock level is below reorder threshold, recommend 10% price increase
        if (product.getStockLevel() < product.getReorderThreshold()) {
            recommendedPrice = currentPrice.doubleValue() * 1.10;
            direction = "INCREASE";
            reasoning = "Stock level (" + product.getStockLevel() + ") is below reorder threshold (" + 
                       product.getReorderThreshold() + "). Recommending 10% price increase.";
        } 
        // Rule 2: If demand velocity is more than 2 times category average, recommend 5% price increase
        else if (product.getDemandVelocity() > 2 * getCategoryAverageDemand(product.getCategory())) {
            recommendedPrice = currentPrice.doubleValue() * 1.05;
            direction = "INCREASE";
            reasoning = "Demand velocity (" + product.getDemandVelocity() + ") is more than 2x category average (" + 
                       getCategoryAverageDemand(product.getCategory()) + "). Recommending 5% price increase.";
        } 
        // Rule 3: Otherwise, hold the price
        else {
            direction = "HOLD";
            reasoning = "No significant factors affecting price. Recommending to hold current price.";
        }
        
        return new PricingRecommendation(recommendedPrice, direction, 0.8, reasoning);
    }
    
    private ReorderRecommendation generateReorderRecommendation(Product product) {
        int currentStock = product.getStockLevel();
        int reorderThreshold = product.getReorderThreshold();
        
        // Recommended quantity: max((reorderThreshold * 3) - currentStock, 1)
        int recommendedQuantity = Math.max((reorderThreshold * 3) - currentStock, 1);
        
        String reasoning = "Recommended quantity calculated as max((reorderThreshold * 3) - currentStock, 1) = " +
                          "max((" + reorderThreshold + " * 3) - " + currentStock + ", 1) = " + recommendedQuantity;
        
        return new ReorderRecommendation(recommendedQuantity, 7, 0.9, reasoning);
    }
    
    private double getCategoryAverageDemand(Category category) {
        List<Product> products = productRepository.findAll();
        int totalDemand = 0;
        int categoryCount = 0;
        
        for (Product product : products) {
            if (product.getCategory() == category) {
                totalDemand += product.getDemandVelocity();
                categoryCount++;
            }
        }
        
        return categoryCount > 0 ? (double) totalDemand / categoryCount : 0;
    }
}