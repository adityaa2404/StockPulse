package com.stockpulse.product;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/products")
public class ProductController {
    
    @Autowired
    private ProductService productService;
    
    @PostMapping
    public ResponseEntity<Product> createProduct(@Valid @RequestBody Product product) {
        Product createdProduct = productService.createProduct(product);
        return new ResponseEntity<>(createdProduct, HttpStatus.CREATED);
    }
    
    @GetMapping
    public ResponseEntity<List<Product>> getProducts(
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) Category category) {
        
        List<Product> products;
        if (status != null || category != null) {
            products = productService.getProductsByStatusAndCategory(status, category);
        } else {
            products = productService.getAllProducts();
        }
        
        return new ResponseEntity<>(products, HttpStatus.OK);
    }
    
    @PatchMapping("/{id}/stock")
    public ResponseEntity<Product> updateStock(@PathVariable Long id, @RequestBody StockUpdateRequest request) {
        Product updatedProduct = productService.updateStockLevel(id, request.getStockLevel());
        if (updatedProduct != null) {
            return new ResponseEntity<>(updatedProduct, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }
    
    @PostMapping("/{id}/orders")
    public ResponseEntity<?> processOrder(@PathVariable Long id, @RequestBody OrderRequest request) {
        boolean success = productService.processOrder(id, request.getQuantity());
        if (success) {
            return new ResponseEntity<>(HttpStatus.OK);
        } else {
            return new ResponseEntity<>("Insufficient stock or product not found", HttpStatus.BAD_REQUEST);
        }
    }
    
    // Helper classes for request bodies
    public static class StockUpdateRequest {
        private Integer stockLevel;
        
        public Integer getStockLevel() {
            return stockLevel;
        }
        
        public void setStockLevel(Integer stockLevel) {
            this.stockLevel = stockLevel;
        }
    }
    
    public static class OrderRequest {
        private Integer quantity;
        
        public Integer getQuantity() {
            return quantity;
        }
        
        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }
    }
}