package com.stockpulse.product;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

@Entity
@Table(name = "products")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Product {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank(message = "SKU is required")
    @Column(unique = true)
    private String sku;
    
    @NotBlank(message = "Name is required")
    private String name;
    
    @Enumerated(EnumType.STRING)
    @NotNull(message = "Category is required")
    private Category category;
    
    @Positive(message = "Price must be positive")
    @Column(precision = 10, scale = 2)
    private BigDecimal currentPrice;
    
    @Min(value = 0, message = "Stock level cannot be negative")
    private Integer stockLevel;
    
    @Min(value = 0, message = "Reorder threshold cannot be negative")
    private Integer reorderThreshold;
    
    @Min(value = 0, message = "Demand velocity cannot be negative")
    private Integer demandVelocity = 0;
    
    @Enumerated(EnumType.STRING)
    @NotNull(message = "Status is required")
    private ProductStatus status;
    
    // Sprint 2 extension points
    @Column(precision = 10, scale = 2)
    private BigDecimal costPrice;
    
    private Long supplierId;
    
    // Constructors
    public Product() {}
    
    public Product(String sku, String name, Category category, BigDecimal currentPrice, 
                   Integer stockLevel, Integer reorderThreshold, ProductStatus status) {
        this.sku = sku;
        this.name = name;
        this.category = category;
        this.currentPrice = currentPrice;
        this.stockLevel = stockLevel;
        this.reorderThreshold = reorderThreshold;
        this.status = status;
        this.demandVelocity = 0;
    }
    
    // Getters and setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getSku() {
        return sku;
    }
    
    public void setSku(String sku) {
        this.sku = sku;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public Category getCategory() {
        return category;
    }
    
    public void setCategory(Category category) {
        this.category = category;
    }
    
    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }
    
    public void setCurrentPrice(BigDecimal currentPrice) {
        this.currentPrice = currentPrice;
    }
    
    public Integer getStockLevel() {
        return stockLevel;
    }
    
    public void setStockLevel(Integer stockLevel) {
        this.stockLevel = stockLevel;
    }
    
    public Integer getReorderThreshold() {
        return reorderThreshold;
    }
    
    public void setReorderThreshold(Integer reorderThreshold) {
        this.reorderThreshold = reorderThreshold;
    }
    
    public Integer getDemandVelocity() {
        return demandVelocity;
    }
    
    public void setDemandVelocity(Integer demandVelocity) {
        this.demandVelocity = demandVelocity;
    }
    
    public ProductStatus getStatus() {
        return status;
    }
    
    public void setStatus(ProductStatus status) {
        this.status = status;
    }
    
    public BigDecimal getCostPrice() {
        return costPrice;
    }
    
    public void setCostPrice(BigDecimal costPrice) {
        this.costPrice = costPrice;
    }
    
    public Long getSupplierId() {
        return supplierId;
    }
    
    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
    }
}