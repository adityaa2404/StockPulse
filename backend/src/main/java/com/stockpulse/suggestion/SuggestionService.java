package com.stockpulse.suggestion;

import com.stockpulse.commerce.CommerceService;
import com.stockpulse.product.Product;
import com.stockpulse.product.ProductService;
import com.stockpulse.product.ProductStatus;
import com.stockpulse.product.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.Optional;

@Service
public class SuggestionService {
    
    @Autowired
    private ProductService productService;
    
    @Autowired
    private ProductRepository productRepository;
    
    @Autowired
    private PricingSuggestionRepository pricingSuggestionRepository;
    
    @Autowired
    private ReorderSuggestionRepository reorderSuggestionRepository;
    
    @Autowired
    private CommerceService commerceService;
    
    public PricingSuggestion createPricingSuggestion(Long productId) {
        Optional<Product> optionalProduct = productService.getProductById(productId);
        if (optionalProduct.isPresent()) {
            Product product = optionalProduct.get();
            // Use the rule-based commerce service to generate suggestion
            return commerceService.generatePricingSuggestion(product);
        }
        return null;
    }
    
    public ReorderSuggestion createReorderSuggestion(Long productId) {
        Optional<Product> optionalProduct = productService.getProductById(productId);
        if (optionalProduct.isPresent()) {
            Product product = optionalProduct.get();
            // Use the rule-based commerce service to generate suggestion
            return commerceService.generateReorderSuggestion(product);
        }
        return null;
    }
    
    @Transactional
    public PricingSuggestion updatePricingSuggestionStatus(Long suggestionId, Status newStatus) {
        Optional<PricingSuggestion> optionalSuggestion = pricingSuggestionRepository.findById(suggestionId);
        if (optionalSuggestion.isPresent()) {
            PricingSuggestion suggestion = optionalSuggestion.get();
            
            // Check if suggestion is already finalized
            if (suggestion.getStatus() != Status.PENDING) {
                throw new IllegalStateException("Cannot update finalized suggestion");
            }
            
            suggestion.setStatus(newStatus);
            PricingSuggestion savedSuggestion = pricingSuggestionRepository.save(suggestion);
            
            // If accepted, update product price
            if (newStatus == Status.ACCEPTED) {
                Product product = suggestion.getProduct();
                product.setCurrentPrice(suggestion.getRecommendedPrice());
                productRepository.save(product);
                
                // Update product status back to ACTIVE if it was PRICE_REVIEW_PENDING
                if (product.getStatus() == ProductStatus.PRICE_REVIEW_PENDING) {
                    productService.updateProductStatus(product.getId(), ProductStatus.ACTIVE);
                }
            }
            
            return savedSuggestion;
        }
        return null;
    }
    
    @Transactional
    public ReorderSuggestion updateReorderSuggestionStatus(Long suggestionId, Status newStatus) {
        Optional<ReorderSuggestion> optionalSuggestion = reorderSuggestionRepository.findById(suggestionId);
        if (optionalSuggestion.isPresent()) {
            ReorderSuggestion suggestion = optionalSuggestion.get();
            
            // Check if suggestion is already finalized
            if (suggestion.getStatus() != Status.PENDING) {
                throw new IllegalStateException("Cannot update finalized suggestion");
            }
            
            suggestion.setStatus(newStatus);
            ReorderSuggestion savedSuggestion = reorderSuggestionRepository.save(suggestion);
            
            // If accepted, update product stock
            if (newStatus == Status.ACCEPTED) {
                Product product = suggestion.getProduct();
                int newStockLevel = product.getStockLevel() + suggestion.getRecommendedQuantity();
                productService.updateStockLevel(product.getId(), newStockLevel);
                
                // Update product status back to ACTIVE if it was OUT_OF_STOCK
                if (product.getStatus() == ProductStatus.OUT_OF_STOCK && newStockLevel > 0) {
                    productService.updateProductStatus(product.getId(), ProductStatus.ACTIVE);
                }
            }
            
            return savedSuggestion;
        }
        return null;
    }
}