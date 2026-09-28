package com.stockpulse.suggestion;

import com.stockpulse.product.Product;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/products")
public class SuggestionController {
    
    @Autowired
    private SuggestionService suggestionService;
    
    @PostMapping("/{id}/suggest-pricing")
    public ResponseEntity<PricingSuggestion> createPricingSuggestion(@PathVariable Long id) {
        PricingSuggestion suggestion = suggestionService.createPricingSuggestion(id);
        if (suggestion != null) {
            return new ResponseEntity<>(suggestion, HttpStatus.CREATED);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }
    
    @PostMapping("/{id}/suggest-reorder")
    public ResponseEntity<ReorderSuggestion> createReorderSuggestion(@PathVariable Long id) {
        ReorderSuggestion suggestion = suggestionService.createReorderSuggestion(id);
        if (suggestion != null) {
            return new ResponseEntity<>(suggestion, HttpStatus.CREATED);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }
}