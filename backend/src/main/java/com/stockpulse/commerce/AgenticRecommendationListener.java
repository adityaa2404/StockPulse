package com.stockpulse.commerce;

import com.stockpulse.event.InventoryChangedEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AgenticRecommendationListener {
    
    @Autowired
    private AgenticRecommendationService agenticRecommendationService;
    
    @Async
    @TransactionalEventListener
    public void handleInventoryChangedEvent(InventoryChangedEvent event) {
        agenticRecommendationService.generateRecommendationsForEvent(
            event.getProductId(), event.getTriggerReason());
    }
}