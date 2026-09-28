package com.stockpulse.event;

import com.stockpulse.suggestion.TriggerReason;

public class InventoryChangedEvent {
    private final Long productId;
    private final TriggerReason triggerReason;
    
    public InventoryChangedEvent(Long productId, TriggerReason triggerReason) {
        this.productId = productId;
        this.triggerReason = triggerReason;
    }
    
    public Long getProductId() {
        return productId;
    }
    
    public TriggerReason getTriggerReason() {
        return triggerReason;
    }
}