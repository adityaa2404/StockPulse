# StockPulse Architecture Decision Record

> **Implementation status:** The reactive commerce backend and rule-based advisor are implemented and runnable. The Gemini advisor is now implemented with environment-key detection, structured JSON parsing, output validation, timeout/error fallback to the rule-based advisor, and separate trigger context for inventory-low, demand-spike, and manual requests. The frontend is implemented as a React 18/Vite application.
>
> **Verified scope for this branch:** product/catalog APIs, stock/order updates, persisted pricing and reorder suggestions, rule-based pricing/reorder logic, asynchronous inventory-triggered recommendations, duplicate pending-suggestion prevention, human accept/reject checkpoint, runtime strategy selection, Gemini integration with deterministic fallback, and React dashboard.
>
> **Not included:** payments, cart/checkout, competitor scraping, supplier APIs/purchase orders, authentication, microservices, Redis/Kafka, RAG, and SSE.

# Architecture Decision Record

## StockPulse — AI Inventory & Dynamic Pricing Engine

\*\*Status:\** Accepted  

\*\*Date:\** 2026-09-28

---

## 1. Context

StockPulse is a reactive commerce advisor for ShopStream, an online store with hundreds of SKUs.

The system needs to detect inventory and demand changes and generate recommendations for:

1\. Dynamic pricing.

2\. Inventory replenishment.

The system should not automatically modify prices or inventory. Recommendations must first be presented to a human merchandiser for approval.

The architecture must also support multiple recommendation strategies, including a deterministic rule-based strategy and an AI/LLM-based strategy.

---

# 2. Decision

We chose a modular Spring Boot architecture with a common `CommerceAdvisor` contract.

The main flow is:

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

        /         \\

       /           \\

RuleBased       Gemini AI

Advisor         Advisor

       \           /

        \         /

         Suggestions

              |

              v

       Human Approval

              |

        +-----+-----+

        |           |

      Accept      Reject

        |

        v

 Product / Inventory Update

The active strategy is selected through configuration rather than requiring source-code changes.

**---**

# 3. Commerce Logic Placement

## Decision

Commerce recommendation logic is placed behind the `CommerceAdvisor` interface.

The initial deterministic implementation is `RuleBasedCommerceAdvisor`.

An AI implementation can implement the same contract.

```

```

```

CommerceAdvisor

       |

       +-- RuleBasedCommerceAdvisor

       |

       +-- GeminiCommerceAdvisor

```

## Why

This keeps business logic independent from the specific recommendation technology.

The application can therefore switch between:

```

```

```

stockpulse.commerce.strategy=RULE

```

and:

```

```

```

stockpulse.commerce.strategy=AI

```

without changing the controller or product-management code.

## Trade-off

The abstraction introduces additional classes compared with putting all logic directly inside the controller or service.

However, it provides a cleaner separation between:

-  Product management. 

-  Commerce rules. 

-  AI integration. 

-  Recommendation persistence. 

-  Human approval. 

**---**

# 4. Rule-Based Strategy

The deterministic strategy provides a predictable fallback and baseline recommendation mechanism.

## Pricing Rules

### Low inventory

If:

```

```

```

stock < reorder threshold

```

then:

```

```

```

recommended price = current price × 1.10

```

### Demand spike

If:

```

```

```

demand velocity > 2 × category average

```

then:

```

```

```

recommended price = current price × 1.05

```

### Otherwise

```

```

```

direction = HOLD

```

The rule-based strategy is deterministic and does not depend on an external AI provider.

**---**

# 5. Reorder Logic

The reorder recommendation uses:

```

```

```

(reorder threshold × 3) − current stock

```

with a minimum recommendation of:

```

```

```

1

```

This provides a simple inventory target while keeping the recommendation deterministic and explainable.

**---**

# 6. AI Integration

The AI strategy is separated from the rest of the commerce system through an `LLMGateway` abstraction.

```

```

```

GeminiCommerceAdvisor

          |

          v

      LLMGateway

          |

          v

    Gemini Provider

```

The advisor prepares structured product and demand context before sending it to the LLM.

The context includes:

-  Product name. 

-  Category. 

-  Current price. 

-  Current stock. 

-  Reorder threshold. 

-  Demand velocity. 

-  Category demand information. 

-  Trigger reason. 

The trigger context is also provided so that an inventory shortage and a demand spike can result in different reasoning.

**---**

# 7. LLM Failure Handling

The LLM is treated as an external dependency and therefore cannot be assumed to always be available.

Potential failures include:

-  Missing API key. 

-  Network errors. 

-  Timeout. 

-  Rate limits. 

-  Quota exhaustion. 

-  Invalid JSON. 

-  Missing response fields. 

-  Invalid recommendation values. 

When an AI recommendation cannot be safely produced, the system falls back to the deterministic `RuleBasedCommerceAdvisor`.

Therefore:

```

```

```

AI recommendation

       |

       +---- success ----> AI suggestion

       |

       +---- failure ----> Rule-based suggestion

```

This prevents an external AI failure from completely stopping the commerce recommendation workflow.

**---**

# 8. Recommendation Validation

AI-generated recommendations must be validated before being persisted.

For pricing:

-  Recommended price must be positive. 

-  Direction must be valid. 

-  Confidence must be between 0 and 1. 

-  The recommended price should remain within a reasonable range relative to the current price. 

For reorder:

-  Recommended quantity must be a positive integer. 

-  Lead time must be valid. 

-  Confidence must be between 0 and 1. 

Invalid AI output is treated as an AI failure and handled through the rule-based fallback.

**---**

# 9. Agentic Event Loop

The system uses an event-driven approach for reactive recommendations.

When an inventory or order operation changes relevant product state, an event is published.

Conceptually:

```

```

```

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

The recommendation generation is decoupled from the immediate HTTP request wherever possible.

This allows the stock/order API to respond without waiting for the complete recommendation process.

**---**

# 10. Trigger Types

Two important automatic triggers are supported.

## Inventory Low

When stock falls below the configured reorder threshold:

```

```

```

INVENTORY_LOW

```

the system generates pricing and reorder recommendations.

## Demand Spike

When demand velocity crosses the configured demand-spike threshold relative to the category average:

```

```

```

DEMAND_SPIKE

```

the system generates pricing and reorder recommendations.

Manual recommendation requests use:

```

```

```

MANUAL

```

**---**

# 11. Duplicate Prevention

The system avoids creating duplicate pending recommendations for the same:

```

```

```

Product + Trigger Reason + Suggestion Type

```

This prevents repeated events from filling the system with identical pending suggestions.

**---**

# 12. Human-in-the-Loop Approval

The system does not automatically apply AI or rule-based recommendations.

Instead:

```

```

```

Recommendation

      |

      v

PENDING

      |

   Human Review

      |

   +--+--+

   |     |

ACCEPT REJECT

```

For an accepted pricing suggestion, the product price can be updated.

For an accepted reorder suggestion, the inventory can be updated according to the recommendation.

This provides a human checkpoint before business-impacting changes are applied.

**---**

# 13. Unified Advisor vs Separate Pricing/Reorder Engines

Two architectural approaches were considered.

### Option A — Separate pricing and reorder engines

```

```

```

PricingAdvisor

ReorderAdvisor

```

### Option B — Unified CommerceAdvisor

```

```

```

CommerceAdvisor

       |

       +-- Pricing Recommendation

       +-- Reorder Recommendation

```

We chose the unified `CommerceAdvisor`.

The two recommendations are closely related and are triggered by the same commerce context.

A unified contract also makes it easier for both the rule-based and AI strategies to consume the same product and demand information.

**---**

# 14. Synchronous vs Asynchronous Processing

The system separates immediate commerce operations from recommendation generation.

For example:

```

```

```

PATCH stock

     |

     +---- update stock immediately

     |

     +---- publish event

              |

              v

        async recommendation

```

This prevents recommendation generation from unnecessarily blocking the stock update request.

The same recommendation contracts can still be used for on-demand/manual requests.

**---**

# 15. Persistence

The application uses Spring Data JPA for persistence.

The local development configuration uses H2.

The primary persisted concepts are:

```

```

```

Product

PricingSuggestion

ReorderSuggestion

```

Suggestions retain their status and trigger reason so that the UI and business workflow can distinguish pending, accepted, and rejected recommendations.

**---**

# 16. API Design

The backend exposes REST APIs for:

### Products

```

```

```

POST   /products

GET    /products

PATCH  /products/{id}/stock

POST   /products/{id}/orders

```

### Recommendations

```

```

```

POST  /products/{id}/suggest-pricing

POST  /products/{id}/suggest-reorder

GET   /pricing-suggestions

GET   /reorder-suggestions

```

### Human approval

```

```

```

PATCH /pricing-suggestions/{id}

PATCH /reorder-suggestions/{id}

```

This keeps the recommendation lifecycle explicit and allows the frontend to poll for newly generated suggestions.

**---**

# 17. Technology Choices

The backend uses:

-  Java 17. 

-  Spring Boot. 

-  Spring Data JPA. 

-  H2 for local persistence. 

-  REST APIs. 

-  Spring asynchronous event processing. 

-  Gemini through an `LLMGateway` abstraction for the AI strategy. 

The architecture intentionally avoids unnecessary infrastructure such as:

-  Microservices. 

-  Kafka. 

-  Redis. 

-  Complex event brokers. 

-  Supplier integrations. 

-  Automated purchase orders. 

These are outside the core scope of the prototype.

**---**

# 18. Alternatives Considered

## Direct AI calls from controllers

Rejected because it would couple HTTP/API code directly to the AI provider.

## Hard-coded AI provider throughout the application

Rejected because it would make changing the provider or testing fallback behavior more difficult.

## Fully automatic price changes

Rejected because merchandising approval is required before recommendations affect the product.

## Synchronous AI processing inside stock updates

Rejected because an external LLM request can introduce latency or failure into the critical stock-update path.

## Microservices

Not selected for this prototype because the required functionality can be implemented cleanly within a modular Spring Boot application.

**---**

# 19. Trade-offs

### Benefits

-  Clear separation of responsibilities. 

-  Easy switching between rule-based and AI strategies. 

-  Deterministic fallback when AI is unavailable. 

-  Human approval before business-impacting changes. 

-  Event-driven recommendation generation. 

-  Simple local development and demonstration. 

-  Easy extension to additional recommendation strategies. 

### Costs

-  More interfaces and classes than a simple monolithic implementation. 

-  AI responses require parsing and validation. 

-  Asynchronous processing introduces eventual consistency between an inventory update and its recommendation. 

-  External AI services introduce latency, quota, and availability concerns. 

**---**

# 20. Scope Exclusions

The following were intentionally excluded from the prototype:

-  Full e-commerce storefront. 

-  Cart and payment processing. 

-  Competitor price scraping. 

-  Automated purchase orders. 

-  Supplier APIs. 

-  Authentication and authorization. 

-  Complex analytics dashboards. 

-  Distributed messaging infrastructure. 

-  Real-time SSE infrastructure unless required later. 

The focus is the reactive commerce-advisor workflow:

```

```

```

Detect

  ↓

Reason

  ↓

Recommend

  ↓

Present

  ↓

Human Approval

```

**---**

# 21. Final Architecture

The resulting architecture is:

```

```

```

                  REST API

                     |

                     v

              Product Services

                     |

          +----------+----------+

          |                     |

          v                     v

    Stock / Orders          Manual Request

          |

          v

    Event Publisher

          |

          v

 Async Recommendation Service

          |

          v

   CommerceAdvisorService

          |

       Strategy

       /      \\

      /        \\

    RULE       AI

     |          |

     v          v

 RuleBased   Gemini

 Advisor     Advisor

                |

                v

           LLMGateway

                |

                v

             Gemini

          Both produce

              |

      +-------+-------+

      |               |

      v               v

 Pricing Suggestion  Reorder Suggestion

      |               |

      +-------+-------+

              |

              v

        Human Approval

              |

        +-----+-----+

        |           |

      Accept      Reject

```
