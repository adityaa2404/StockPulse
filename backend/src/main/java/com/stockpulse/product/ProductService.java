package com.stockpulse.product;

import com.stockpulse.event.InventoryChangedEvent;
import com.stockpulse.suggestion.TriggerReason;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
public class ProductService {
    
    @Autowired
    private ProductRepository productRepository;
    
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    
    @Value("${stockpulse.demand-spike.multiplier:3.0}")
    private double demandSpikeMultiplier;
    
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }
    
    public List<Product> getProductsByStatusAndCategory(ProductStatus status, Category category) {
        return productRepository.findByStatusAndCategory(status, category);
    }
    
    public Optional<Product> getProductById(Long id) {
        return productRepository.findById(id);
    }
    
    public Product createProduct(Product product) {
        return productRepository.save(product);
    }
    
    @Transactional
    public Product updateStockLevel(Long productId, Integer newStockLevel) {
        Optional<Product> optionalProduct = productRepository.findById(productId);
        if (optionalProduct.isPresent()) {
            Product product = optionalProduct.get();
            
            // Store original values for comparison
            int originalStockLevel = product.getStockLevel();
            int originalDemandVelocity = product.getDemandVelocity();
            
            product.setStockLevel(newStockLevel);
            
            // Update status if stock level is 0
            if (newStockLevel == 0) {
                product.setStatus(ProductStatus.OUT_OF_STOCK);
            } else if (product.getStatus() == ProductStatus.OUT_OF_STOCK && newStockLevel > 0) {
                product.setStatus(ProductStatus.ACTIVE);
            }
            
            Product savedProduct = productRepository.save(product);
            
            // Publish events after successful save
            publishInventoryEvents(savedProduct, originalStockLevel, originalDemandVelocity);
            
            return savedProduct;
        }
        return null;
    }
    
    @Transactional
    public boolean processOrder(Long productId, Integer quantity) {
        Optional<Product> optionalProduct = productRepository.findById(productId);
        if (optionalProduct.isPresent()) {
            Product product = optionalProduct.get();
            
            // Check if enough stock is available
            if (product.getStockLevel() < quantity) {
                return false;
            }
            
            // Store original values for comparison
            int originalStockLevel = product.getStockLevel();
            int originalDemandVelocity = product.getDemandVelocity();
            
            // Decrement stock level
            product.setStockLevel(product.getStockLevel() - quantity);
            
            // Increment demand velocity
            product.setDemandVelocity(product.getDemandVelocity() + quantity);
            
            // Update status if stock level is 0
            if (product.getStockLevel() == 0) {
                product.setStatus(ProductStatus.OUT_OF_STOCK);
            }
            
            Product savedProduct = productRepository.save(product);
            
            // Publish events after successful save
            publishInventoryEvents(savedProduct, originalStockLevel, originalDemandVelocity);
            
            return true;
        }
        return false;
    }
    
    private void publishInventoryEvents(Product product, int originalStockLevel, int originalDemandVelocity) {
        boolean inventoryLowEventPublished = false;
        boolean demandSpikeEventPublished = false;
        
        // Check for inventory low condition
        if (product.getStockLevel() < product.getReorderThreshold()) {
            eventPublisher.publishEvent(new InventoryChangedEvent(product.getId(), TriggerReason.INVENTORY_LOW));
            inventoryLowEventPublished = true;
        }
        
        // Check for demand spike condition
        double categoryAverage = getCategoryAverageDemand(product.getCategory());
        if (categoryAverage > 0 && product.getDemandVelocity() > demandSpikeMultiplier * categoryAverage) {
            eventPublisher.publishEvent(new InventoryChangedEvent(product.getId(), TriggerReason.DEMAND_SPIKE));
            demandSpikeEventPublished = true;
        }
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
    
    @Transactional
    public Product updateProductStatus(Long productId, ProductStatus newStatus) {
        Optional<Product> optionalProduct = productRepository.findById(productId);
        if (optionalProduct.isPresent()) {
            Product product = optionalProduct.get();
            
            // Validate state transitions
            if (!isValidTransition(product.getStatus(), newStatus)) {
                throw new IllegalStateException("Invalid status transition from " + 
                    product.getStatus() + " to " + newStatus);
            }
            
            product.setStatus(newStatus);
            return productRepository.save(product);
        }
        return null;
    }
    
    private boolean isValidTransition(ProductStatus currentStatus, ProductStatus newStatus) {
        // Define valid transitions
        switch (currentStatus) {
            case ACTIVE:
                return newStatus == ProductStatus.PRICE_REVIEW_PENDING || 
                       newStatus == ProductStatus.OUT_OF_STOCK;
            case PRICE_REVIEW_PENDING:
                return newStatus == ProductStatus.ACTIVE;
            case OUT_OF_STOCK:
                return newStatus == ProductStatus.ACTIVE;
            default:
                return false;
        }
    }
}