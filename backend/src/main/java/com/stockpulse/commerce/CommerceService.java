package com.stockpulse.commerce;

import com.stockpulse.product.Category;
import com.stockpulse.product.Product;
import com.stockpulse.suggestion.Direction;
import com.stockpulse.suggestion.PricingSuggestion;
import com.stockpulse.suggestion.ReorderSuggestion;
import com.stockpulse.suggestion.TriggerReason;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Service
public class CommerceService {
    
    // Average demand velocity by category (simplified for this phase)
    private static final Map<Category, Integer> CATEGORY_AVERAGES = new HashMap<>();
    
    static {
        CATEGORY_AVERAGES.put(Category.ELECTRONICS, 5);
        CATEGORY_AVERAGES.put(Category.APPAREL, 8);
        CATEGORY_AVERAGES.put(Category.HOME, 3);
    }
    
    public PricingSuggestion generatePricingSuggestion(Product product) {
        BigDecimal currentPrice = product.getCurrentPrice();
        BigDecimal recommendedPrice = currentPrice;
        Direction direction = Direction.HOLD;
        String reasoning = "";
        
        // Rule 1: If stock level is below reorder threshold, recommend 10% price increase
        if (product.getStockLevel() < product.getReorderThreshold()) {
            recommendedPrice = currentPrice.multiply(BigDecimal.valueOf(1.10));
            direction = Direction.INCREASE;
            reasoning = "Stock level (" + product.getStockLevel() + ") is below reorder threshold (" + 
                       product.getReorderThreshold() + "). Recommending 10% price increase.";
        } 
        // Rule 2: If demand velocity is more than 2 times category average, recommend 5% price increase
        else if (product.getDemandVelocity() > 2 * CATEGORY_AVERAGES.getOrDefault(product.getCategory(), 5)) {
            recommendedPrice = currentPrice.multiply(BigDecimal.valueOf(1.05));
            direction = Direction.INCREASE;
            reasoning = "Demand velocity (" + product.getDemandVelocity() + ") is more than 2x category average. Recommending 5% price increase.";
        } 
        // Rule 3: Otherwise, hold the price
        else {
            direction = Direction.HOLD;
            reasoning = "No significant factors affecting price. Recommending to hold current price.";
        }
        
        return new PricingSuggestion(
            product,
            currentPrice,
            recommendedPrice,
            direction,
            0.8, // Confidence score
            reasoning,
            TriggerReason.MANUAL
        );
    }
    
    public ReorderSuggestion generateReorderSuggestion(Product product) {
        int currentStock = product.getStockLevel();
        int reorderThreshold = product.getReorderThreshold();
        
        // Recommended quantity: max((reorderThreshold * 3) - currentStock, 1)
        int recommendedQuantity = Math.max((reorderThreshold * 3) - currentStock, 1);
        
        String reasoning = "Recommended quantity calculated as max((reorderThreshold * 3) - currentStock, 1) = " +
                          "max((" + reorderThreshold + " * 3) - " + currentStock + ", 1) = " + recommendedQuantity;
        
        return new ReorderSuggestion(
            product,
            currentStock,
            recommendedQuantity,
            7, // Default lead time of 7 days
            0.9, // Confidence score
            reasoning,
            TriggerReason.MANUAL
        );
    }
}