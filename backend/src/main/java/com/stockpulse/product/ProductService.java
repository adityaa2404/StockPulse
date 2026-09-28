package com.stockpulse.product;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockpulse.event.InventoryChangedEvent;
import com.stockpulse.suggestion.TriggerReason;

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

    public List<Product> getProductsByStatusAndCategory(
            ProductStatus status,
            Category category) {

        return productRepository.findByStatusAndCategory(status, category);
    }

    public Optional<Product> getProductById(Long id) {
        return productRepository.findById(id);
    }

    public Product createProduct(Product product) {
        return productRepository.save(product);
    }

    /**
     * Update the status of a product.
     *
     * Used when a pricing suggestion is accepted/rejected
     * and by SuggestionService.
     */
    public Product updateProductStatus(
            Long productId,
            ProductStatus status) {

        Optional<Product> optionalProduct =
                productRepository.findById(productId);

        if (optionalProduct.isPresent()) {

            Product product = optionalProduct.get();

            product.setStatus(status);

            return productRepository.save(product);
        }

        return null;
    }

    /**
     * Update product stock.
     *
     * This also publishes inventory-low and demand-spike events
     * after the stock change.
     */
    @Transactional
    public Product updateStockLevel(
            Long productId,
            Integer newStockLevel) {

        Optional<Product> optionalProduct =
                productRepository.findById(productId);

        if (optionalProduct.isPresent()) {

            Product product = optionalProduct.get();

            int originalStockLevel =
                    product.getStockLevel();

            int originalDemandVelocity =
                    product.getDemandVelocity();

            product.setStockLevel(newStockLevel);

            /*
             * If stock reaches zero, mark product OUT_OF_STOCK.
             */
            if (newStockLevel == 0) {

                product.setStatus(
                        ProductStatus.OUT_OF_STOCK
                );

            }
            /*
             * If an OUT_OF_STOCK product gets stock again,
             * move it back to ACTIVE.
             */
            else if (
                    product.getStatus() == ProductStatus.OUT_OF_STOCK
                            && newStockLevel > 0
            ) {

                product.setStatus(
                        ProductStatus.ACTIVE
                );
            }

            Product savedProduct =
                    productRepository.save(product);

            /*
             * Publish reactive events after saving.
             */
            publishInventoryEvents(
                    savedProduct,
                    originalStockLevel,
                    originalDemandVelocity
            );

            return savedProduct;
        }

        return null;
    }

    /**
     * Process an order/sale.
     *
     * Stock decreases by the ordered quantity.
     * Demand velocity increases by the same quantity.
     */
    @Transactional
    public boolean processOrder(
            Long productId,
            Integer quantity) {

        Optional<Product> optionalProduct =
                productRepository.findById(productId);

        if (optionalProduct.isPresent()) {

            Product product =
                    optionalProduct.get();

            /*
             * Prevent selling more stock than available.
             */
            if (product.getStockLevel() < quantity) {
                return false;
            }

            int originalStockLevel =
                    product.getStockLevel();

            int originalDemandVelocity =
                    product.getDemandVelocity();

            /*
             * Decrease inventory.
             */
            product.setStockLevel(
                    product.getStockLevel() - quantity
            );

            /*
             * Increase demand velocity.
             */
            product.setDemandVelocity(
                    product.getDemandVelocity() + quantity
            );

            /*
             * If inventory becomes zero,
             * mark the product as OUT_OF_STOCK.
             */
            if (product.getStockLevel() == 0) {

                product.setStatus(
                        ProductStatus.OUT_OF_STOCK
                );
            }

            Product savedProduct =
                    productRepository.save(product);

            /*
             * Check whether the order caused
             * an inventory-low or demand-spike event.
             */
            publishInventoryEvents(
                    savedProduct,
                    originalStockLevel,
                    originalDemandVelocity
            );

            return true;
        }

        return false;
    }

    /**
     * Determine which reactive events should be published.
     */
    private void publishInventoryEvents(
            Product product,
            int originalStockLevel,
            int originalDemandVelocity) {

        /*
         * =========================================================
         * TRIGGER A: INVENTORY LOW
         * =========================================================
         *
         * If stock is below the reorder threshold,
         * generate an INVENTORY_LOW event.
         */
        if (
                product.getStockLevel()
                        < product.getReorderThreshold()
        ) {

            eventPublisher.publishEvent(
                    new InventoryChangedEvent(
                            product.getId(),
                            TriggerReason.INVENTORY_LOW
                    )
            );
        }

        /*
         * =========================================================
         * TRIGGER B: DEMAND SPIKE
         * =========================================================
         *
         * Compare the current product's demand velocity
         * against the average demand velocity of PEER PRODUCTS
         * in the same category.
         *
         * IMPORTANT:
         * The current product is excluded from the average.
         */
        double categoryAverage =
                getCategoryAverageDemand(
                        product.getCategory(),
                        product.getId()
                );

        /*
         * Example:
         *
         * Peer velocities:
         * 12 and 5
         *
         * Average = 8.5
         *
         * Multiplier = 3
         *
         * Threshold = 25.5
         *
         * Current product velocity = 35
         *
         * 35 > 25.5
         *
         * Therefore DEMAND_SPIKE fires.
         */
        if (
                categoryAverage > 0
                        && product.getDemandVelocity()
                        > demandSpikeMultiplier * categoryAverage
        ) {

            eventPublisher.publishEvent(
                    new InventoryChangedEvent(
                            product.getId(),
                            TriggerReason.DEMAND_SPIKE
                    )
            );
        }
    }

    /**
     * Calculate the average demand velocity of peer products
     * in the same category.
     *
     * The current product is excluded.
     */
    private double getCategoryAverageDemand(
            Category category,
            Long excludedProductId) {

        List<Product> products =
                productRepository.findAll();

        int totalDemand = 0;
        int categoryCount = 0;

        for (Product product : products) {

            /*
             * Same category AND not the current product.
             */
            if (
                    product.getCategory() == category
                            && !product.getId().equals(excludedProductId)
            ) {

                totalDemand +=
                        product.getDemandVelocity();

                categoryCount++;
            }
        }

        /*
         * Avoid division by zero.
         */
        if (categoryCount == 0) {
            return 0;
        }

        return (double) totalDemand / categoryCount;
    }
}