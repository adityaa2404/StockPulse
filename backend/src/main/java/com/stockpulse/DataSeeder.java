package com.stockpulse;

import com.stockpulse.product.*;
import com.stockpulse.suggestion.PricingSuggestionRepository;
import com.stockpulse.suggestion.ReorderSuggestionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;

@Component
public class DataSeeder implements CommandLineRunner {
    
    @Autowired
    private ProductRepository productRepository;
    
    @Autowired
    private PricingSuggestionRepository pricingSuggestionRepository;
    
    @Autowired
    private ReorderSuggestionRepository reorderSuggestionRepository;
    
    @Override
    public void run(String... args) throws Exception {
        // Clear existing data in correct order to maintain referential integrity
        reorderSuggestionRepository.deleteAll();
        pricingSuggestionRepository.deleteAll();
        productRepository.deleteAll();
        
        // Seed data from the brief
        Product product1 = new Product();
        product1.setSku("PRD-003");
        product1.setName("Organic Cotton T-Shirt");
        product1.setCategory(Category.APPAREL);
        product1.setCurrentPrice(new BigDecimal("24.99"));
        product1.setStockLevel(8);
        product1.setReorderThreshold(15);
        product1.setDemandVelocity(12);
        product1.setStatus(ProductStatus.PRICE_REVIEW_PENDING);
        productRepository.save(product1);
        
        Product product2 = new Product();
        product2.setSku("PRD-008");
        product2.setName("Hoodie — Heather Grey");
        product2.setCategory(Category.APPAREL);
        product2.setCurrentPrice(new BigDecimal("54.99"));
        product2.setStockLevel(11);
        product2.setReorderThreshold(12);
        product2.setDemandVelocity(15);
        product2.setStatus(ProductStatus.ACTIVE);
        productRepository.save(product2);
        
        // Additional products
        Product product3 = new Product();
        product3.setSku("SKU-ELE-001");
        product3.setName("Wireless Bluetooth Headphones");
        product3.setCategory(Category.ELECTRONICS);
        product3.setCurrentPrice(new BigDecimal("89.99"));
        product3.setStockLevel(25);
        product3.setReorderThreshold(10);
        product3.setDemandVelocity(8);
        product3.setStatus(ProductStatus.ACTIVE);
        productRepository.save(product3);
        
        Product product4 = new Product();
        product4.setSku("SKU-HOM-001");
        product4.setName("Ceramic Cookware Set");
        product4.setCategory(Category.HOME);
        product4.setCurrentPrice(new BigDecimal("129.99"));
        product4.setStockLevel(5);
        product4.setReorderThreshold(8);
        product4.setDemandVelocity(3);
        product4.setStatus(ProductStatus.ACTIVE);
        productRepository.save(product4);
        
        Product product5 = new Product();
        product5.setSku("SKU-APP-002");
        product5.setName("Denim Jeans");
        product5.setCategory(Category.APPAREL);
        product5.setCurrentPrice(new BigDecimal("49.99"));
        product5.setStockLevel(0);
        product5.setReorderThreshold(20);
        product5.setDemandVelocity(5);
        product5.setStatus(ProductStatus.OUT_OF_STOCK);
        productRepository.save(product5);
        
        System.out.println("Seeded " + productRepository.count() + " products");
    }
}