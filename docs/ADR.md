# Architecture Decision Record: StockPulse

**AI Inventory & Dynamic Pricing Engine**

| Field   | Value      |
| ------- | ---------- |
| Status  | Accepted   |
| Date    | 2026-09-28 |
| Project | StockPulse |

---

## Implementation Status

The reactive commerce backend, rule-based advisor, asynchronous recommendation workflow, human approval flow, and React 18/Vite frontend are implemented.

The Gemini advisor is implemented with:

- Environment-key detection
- Structured JSON parsing
- Output validation
- Timeout/error fallback to the rule-based advisor
- Separate trigger context for inventory-low, demand-spike, and manual requests

A live Gemini API call requires `GEMINI_API_KEY`.

**Verified scope for this branch:**

- Product/catalog APIs
- Stock/order updates
- Persisted pricing and reorder suggestions
- Rule-based pricing/reorder logic
- Asynchronous inventory-triggered recommendations
- Duplicate pending-suggestion prevention
- Human accept/reject checkpoint
- Runtime strategy selection
- Gemini integration with deterministic fallback
- React dashboard

**Not included:** payments, cart/checkout, competitor scraping, supplier APIs/purchase orders, authentication, microservices, Redis/Kafka, RAG, and SSE.

---

## 1. Context

StockPulse is a reactive commerce advisor for ShopStream, an online store with hundreds of SKUs.

The system detects inventory and demand changes and generates recommendations for:

1. Dynamic pricing
2. Inventory replenishment

The system must **not** automatically modify prices or inventory. Recommendations are first presented to a human merchandiser for approval.

The architecture must also support multiple recommendation strategies, including a deterministic rule-based strategy and an AI/LLM-based strategy.

---

## 2. Decision

We chose a modular Spring Boot architecture with a common `CommerceAdvisor` contract.

```text
Product / Order / Stock Update
              |
              v
       Event Detection
              |
              v
 Agentic Recommendation Service
              |
              v
       CommerceAdvisor
         /          \
        v            v
 RuleBased         Gemini AI
 Advisor           Advisor
        \            /
         v          v
          Suggestions
               |
               v
        Human Approval
          /        \
         v          v
      Accept      Reject
         |
         v
 Product / Inventory Update
```

The active strategy is selected through configuration rather than source-code changes.

---

## 3. Commerce Logic Placement

### Decision

Commerce recommendation logic is placed behind the `CommerceAdvisor` interface. The initial deterministic implementation is `RuleBasedCommerceAdvisor`; an AI implementation implements the same contract.

```text
CommerceAdvisor
      |
      +-- RuleBasedCommerceAdvisor
      |
      +-- GeminiCommerceAdvisor
```

### Why

This keeps business logic independent from the recommendation technology. The application can switch between:

```properties
stockpulse.commerce.strategy=RULE
```

and:

```properties
stockpulse.commerce.strategy=AI
```

without changing the controller or product-management code.

### Trade-off

The abstraction adds classes compared with putting all logic in the controller or service. In return it cleanly separates:

- Product management
- Commerce rules
- AI integration
- Recommendation persistence
- Human approval

---

## 4. Rule-Based Strategy

The deterministic strategy provides a predictable fallback and baseline. It does not depend on an external AI provider.

### Pricing Rules

| Condition                                    | Result                                       |
| -------------------------------------------- | -------------------------------------------- |
| `stock < reorder threshold` (low inventory)  | `recommended price = current price × 1.10`   |
| `demand velocity > 2 × category average`     | `recommended price = current price × 1.05`   |
| Otherwise                                    | `direction = HOLD`                           |

---

## 5. Reorder Logic

```text
recommended quantity = (reorder threshold × 3) − current stock
```

with a minimum recommendation of `1`.

This gives a simple inventory target while keeping the recommendation deterministic and explainable.

---

## 6. AI Integration

The AI strategy is isolated inside `GeminiCommerceAdvisor`, keeping the external Gemini HTTP call out of controllers and product-management code.

```text
GeminiCommerceAdvisor
          |
          v
      LLMGateway
          |
          v
   Gemini Provider
```

The advisor prepares structured context before calling the LLM:

- Product name
- Category
- Current price
- Current stock
- Reorder threshold
- Demand velocity
- Trigger context
- Trigger reason

The trigger reason lets the advisor distinguish inventory-low, demand-spike, and manual requests.

---

## 7. LLM Failure Handling

The LLM is an external dependency and cannot be assumed to always be available.

**Potential failures:**

- Missing API key
- Network errors
- Timeout
- Rate limits
- Quota exhaustion
- Invalid JSON
- Missing response fields
- Invalid recommendation values

When an AI recommendation cannot be safely produced, the system falls back to the deterministic `RuleBasedCommerceAdvisor`.

```text
AI recommendation
       |
       +-- success --> AI suggestion
       |
       +-- failure --> Rule-based suggestion
```

This prevents an external AI failure from stopping the recommendation workflow.

---

## 8. Recommendation Validation

AI-generated recommendations must be validated before being persisted.

**Pricing:**

- Recommended price must be positive.
- Direction must be valid.
- Confidence must be between 0 and 1.
- Recommended price should stay within a reasonable range relative to the current price.

**Reorder:**

- Recommended quantity must be a positive integer.
- Lead time must be valid.
- Confidence must be between 0 and 1.

Invalid AI output is treated as an AI failure and handled through the rule-based fallback.

---

## 9. Agentic Event Loop

The system uses an event-driven approach for reactive recommendations. When an inventory or order operation changes relevant product state, an event is published.

```text
Stock / Order Update
         |
         v
InventoryChangedEvent
         |
         v
Async Recommendation Processing
         |
         v
Pricing + Reorder Suggestions
```

Recommendation generation is decoupled from the immediate HTTP request wherever possible, so the stock/order API responds without waiting for the full recommendation process.

---

## 10. Trigger Types

| Trigger          | When it fires                                                                              |
| ---------------- | ------------------------------------------------------------------------------------------ |
| `INVENTORY_LOW`  | Stock falls below the configured reorder threshold                                         |
| `DEMAND_SPIKE`   | Demand velocity crosses the configured spike threshold relative to the category average    |
| `MANUAL`         | A user explicitly requests a recommendation                                                |

Automatic triggers generate both pricing and reorder recommendations.

---

## 11. Duplicate Prevention

The system avoids creating duplicate pending recommendations for the same:

```text
Product + Trigger Reason + Suggestion Type
```

This prevents repeated events from filling the system with identical pending suggestions.

---

## 12. Human-in-the-Loop Approval

The system does not automatically apply AI or rule-based recommendations.

```text
Recommendation
      |
      v
   PENDING
      |
Human Review
    /    \
   v      v
ACCEPT  REJECT
```

- Accepted **pricing** suggestion: the product price is updated.
- Accepted **reorder** suggestion: inventory is updated according to the recommendation.

This provides a human checkpoint before business-impacting changes are applied.

---

## 13. Unified Advisor vs Separate Engines

Two approaches were considered.

**Option A: Separate engines**

```text
PricingAdvisor
ReorderAdvisor
```

**Option B: Unified advisor**

```text
CommerceAdvisor
      |
      +-- Pricing Recommendation
      +-- Reorder Recommendation
```

**Chosen: Option B.** The two recommendations are closely related and triggered by the same commerce context. A unified contract also lets both the rule-based and AI strategies consume the same product and demand information.

---

## 14. Synchronous vs Asynchronous Processing

Immediate commerce operations are separated from recommendation generation.

```text
PATCH stock
     |
     +-- update stock immediately
     |
     +-- publish event
            |
            v
      async recommendation
```

This prevents recommendation generation from blocking the stock update request. The same recommendation contracts can still be used for on-demand/manual requests.

---

## 15. Persistence

The application uses Spring Data JPA. Local development uses H2.

**Primary persisted concepts:**

- `Product`
- `PricingSuggestion`
- `ReorderSuggestion`

Suggestions retain their status and trigger reason so the UI and workflow can distinguish pending, accepted, and rejected recommendations.

---

## 16. API Design

### Products

| Method  | Endpoint                    |
| ------- | --------------------------- |
| `POST`  | `/products`                 |
| `GET`   | `/products`                 |
| `PATCH` | `/products/{id}/stock`      |
| `POST`  | `/products/{id}/orders`     |

### Recommendations

| Method | Endpoint                          |
| ------ | --------------------------------- |
| `POST` | `/products/{id}/suggest-pricing`  |
| `POST` | `/products/{id}/suggest-reorder`  |
| `GET`  | `/pricing-suggestions`            |
| `GET`  | `/reorder-suggestions`            |

### Human Approval

| Method  | Endpoint                       |
| ------- | ------------------------------ |
| `PATCH` | `/pricing-suggestions/{id}`    |
| `PATCH` | `/reorder-suggestions/{id}`    |

This keeps the recommendation lifecycle explicit and allows the frontend to poll for newly generated suggestions.

---

## 17. Technology Choices

**Used:**

- Java 17
- Spring Boot
- Spring Data JPA
- H2 for local persistence
- REST APIs
- Spring asynchronous event processing
- Gemini through an `LLMGateway` abstraction

**Intentionally avoided:**

- Microservices
- Kafka
- Redis
- Complex event brokers
- Supplier integrations
- Automated purchase orders

These are outside the core scope of the prototype.

---

## 18. Alternatives Considered

| Alternative                                | Decision     | Reason                                                                   |
| ------------------------------------------ | ------------ | ------------------------------------------------------------------------ |
| Direct AI calls from controllers           | Rejected     | Couples HTTP/API code directly to the AI provider                        |
| Hard-coded AI provider throughout the app  | Rejected     | Makes changing providers and testing fallback behavior harder            |
| Fully automatic price changes              | Rejected     | Merchandising approval is required before recommendations take effect    |
| Synchronous AI processing in stock updates | Rejected     | LLM latency or failure would enter the critical stock-update path        |
| Microservices                              | Not selected | Functionality fits cleanly within a modular Spring Boot application      |

---

## 19. Trade-offs

### Benefits

- Clear separation of responsibilities
- Easy switching between rule-based and AI strategies
- Deterministic fallback when AI is unavailable
- Human approval before business-impacting changes
- Event-driven recommendation generation
- Simple local development and demonstration
- Easy extension to additional recommendation strategies

### Costs

- More interfaces and classes than a simple monolithic implementation
- AI responses require parsing and validation
- Async processing introduces eventual consistency between an inventory update and its recommendation
- External AI services introduce latency, quota, and availability concerns

---

## 20. Scope Exclusions

Intentionally excluded from the prototype:

- Full e-commerce storefront
- Cart and payment processing
- Competitor price scraping
- Automated purchase orders
- Supplier APIs
- Authentication and authorization
- Complex analytics dashboards
- Distributed messaging infrastructure
- Real-time SSE infrastructure (unless required later)

The focus is the reactive commerce-advisor workflow:

```text
Detect -> Reason -> Recommend -> Present -> Human Approval
```

---

## 21. Final Architecture

```text
                 REST API
                    |
                    v
             Product Services
                    |
         +----------+----------+
         |                     |
         v                     v
   Stock / Orders        Manual Request
         |                     |
         v                     |
   Event Publisher             |
         |                     |
         v                     |
 Async Recommendation Service  |
         |                     |
         +----------+----------+
                    |
                    v
        CommerceAdvisorService
                    |
                 Strategy
                /        \
              RULE       AI
               |          |
               v          v
          RuleBased    Gemini
          Advisor      Advisor
               |          |
               |          v
               |     LLMGateway
               |          |
               |          v
               |       Gemini
               |          |
               +----+-----+
                    |
        +-----------+-----------+
        |                       |
        v                       v
 Pricing Suggestion     Reorder Suggestion
        |                       |
        +-----------+-----------+
                    |
                    v
             Human Approval
               /        \
              v          v
           Accept      Reject
```

---

## 22. Verification Note

The deterministic rule-based path is the baseline recommendation path and does not require an external AI provider.

The Gemini path is environment-dependent. To exercise it, configure `GEMINI_API_KEY` and run the backend in the target environment. If the key is missing or the Gemini response fails validation, the application falls back to the rule-based advisor.