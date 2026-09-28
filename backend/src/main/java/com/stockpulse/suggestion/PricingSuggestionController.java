package com.stockpulse.suggestion;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/pricing-suggestions")
public class PricingSuggestionController {
    
    @Autowired
    private SuggestionService suggestionService;
    
    @PatchMapping("/{id}")
    public ResponseEntity<PricingSuggestion> updatePricingSuggestionStatus(
            @PathVariable Long id, 
            @RequestBody StatusUpdateRequest request) {
        try {
            PricingSuggestion updatedSuggestion = suggestionService.updatePricingSuggestionStatus(
                id, request.getStatus());
            if (updatedSuggestion != null) {
                return new ResponseEntity<>(updatedSuggestion, HttpStatus.OK);
            } else {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
        } catch (IllegalStateException e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }
    
    public static class StatusUpdateRequest {
        private Status status;
        
        public Status getStatus() {
            return status;
        }
        
        public void setStatus(Status status) {
            this.status = status;
        }
    }
}