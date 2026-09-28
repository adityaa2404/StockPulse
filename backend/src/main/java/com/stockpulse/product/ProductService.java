package com.stockpulse.product;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
public class ProductService {
    
    @Autowired
    private ProductRepository productRepository;
    
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
            product.setStockLevel(newStockLevel);
            
            // Update status if stock level is 0
            if (newStockLevel == 0) {
                product.setStatus(ProductStatus.OUT_OF_STOCK);
            } else if (product.getStatus() == ProductStatus.OUT_OF_STOCK && newStockLevel > 0) {
                product.setStatus(ProductStatus.ACTIVE);
            }
            
            return productRepository.save(product);
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
            
            // Decrement stock level
            product.setStockLevel(product.getStockLevel() - quantity);
            
            // Increment demand velocity
            product.setDemandVelocity(product.getDemandVelocity() + quantity);
            
            // Update status if stock level is 0
            if (product.getStockLevel() == 0) {
                product.setStatus(ProductStatus.OUT_OF_STOCK);
            }
            
            productRepository.save(product);
            return true;
        }
        return false;
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