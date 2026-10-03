# ShopSphere: Enterprise E-Commerce Microservices Platform

ShopSphere is a production-grade, event-driven, microservices-based e-commerce platform built with Java 21 and Spring Boot 3. It serves as a comprehensive demonstration of enterprise backend architecture, focusing on distributed systems communication, resiliency, concurrency, and eventual consistency.

---

## 📖 Table of Contents
1. [Business Problem](#business-problem)
2. [System Architecture](#system-architecture)
3. [End-to-End Service Execution & Hop-by-Hop Flow](#end-to-end-service-execution--hop-by-hop-flow)
4. [Service Responsibilities](#service-responsibilities)
5. [Database-per-Service Architecture](#database-per-service-architecture)
6. [Synchronous Communication Patterns](#synchronous-communication-patterns)
7. [Asynchronous Event-Driven Processing (Kafka)](#asynchronous-event-driven-processing-kafka)
8. [Distributed Transactions (Saga & Outbox)](#distributed-transactions-saga--outbox)
9. [Multithreading & Concurrency](#multithreading--concurrency)
10. [Idempotency](#idempotency)
11. [Caching (Redis)](#caching-redis)
12. [Resiliency (Resilience4j)](#resiliency-resilience4j)
13. [Observability & Tracing](#observability--tracing)
14. [Local Development & Docker Setup](#how-to-run-the-application-local--docker-setup)
15. [API Examples](#api-examples)
16. [Testing & Failure Scenarios](#testing--failure-scenarios)

---

## 🏢 Business Problem

Modern e-commerce platforms must handle massive scale, intermittent network failures, and complex workflows that span multiple domains (inventory, payments, shipping). A monolithic application quickly becomes a bottleneck for scaling and development. ShopSphere solves this by utilizing a microservices architecture that decouples domains, allowing independent scaling, deployment, and technology choices, while addressing the inherent challenges of distributed data consistency.

---

## 🏛️ System Architecture

ShopSphere follows a loosely coupled, event-driven microservices architecture. 

```mermaid
flowchart TD
    Client[Client (Web/Mobile)]
    Gateway[API Gateway]
    
    %% Services
    CustSvc[Customer Service]
    ProdSvc[Product Service]
    CartSvc[Cart Service]
    OrderSvc[Order Service]
    InvSvc[Inventory Service]
    PaySvc[Payment Service]
    NotifSvc[Notification Service]
    RecSvc[Recommendation Service]
    AnalyticsSvc[Analytics Service]
    
    %% Databases & Cache
    CustDB[(Customer DB)]
    ProdDB[(Product DB)]
    CartDB[(Cart DB)]
    OrderDB[(Order DB + Outbox)]
    InvDB[(Inventory DB)]
    Redis[(Redis Cache)]
    Kafka[[Apache Kafka]]
    
    Client --> Gateway
    Gateway --> CustSvc
    Gateway --> ProdSvc
    Gateway --> CartSvc
    Gateway --> OrderSvc
    
    ProdSvc -. "Sync Independent (CompletableFuture)" .-> InvSvc
    ProdSvc -. "Sync Independent (CompletableFuture)" .-> RecSvc
    ProdSvc --> Redis
    Redis -. "Cache Miss" .-> ProdDB
    
    OrderSvc ==>|"1. Reserve (Sync Dependent)"| InvSvc
    OrderSvc ==>|"2. Pay (Sync Dependent)"| PaySvc
    OrderSvc -. "3. Release (Saga Compensation)" .-> InvSvc
    
    OrderSvc --> OrderDB
    OrderDB -. "Outbox Poller" .-> Kafka
    Kafka -. "order.created" .-> NotifSvc
    Kafka -. "order.created" .-> AnalyticsSvc
    Kafka -. "order.created" .-> RecSvc
```

---

## 🛤️ End-to-End Service Execution & Hop-by-Hop Flow

To understand the exact lifecycle of a request, here is a granular, hop-by-hop breakdown of the two primary workflows, explicitly detailing which service talks to which, thread execution, and message broker interactions.

### Flow 1: Product Page Load (Concurrent Independent Calls)
*Goal: Load a product page with its details, stock availability, and recommendations.*

1. **Client / Frontend** ➡️ **API Gateway**
   * *Action*: Client sends `GET /api/products/PROD-100/details`.
   * *Inside Gateway*: The Gateway receives the request on an I/O thread, skips JWT validation (public route), injects a unique `X-Correlation-ID`, and routes the request to the Product Service.

2. **API Gateway** ➡️ **Product Service** (Main Tomcat Thread)
   * *Inside Product Service*: 
     * The service intercepts the request and checks the **Redis Cache** (`@Cacheable`).
     * **Cache Miss**: If not in Redis, it executes a `SELECT` against the `Product DB` (PostgreSQL), retrieves the product, and saves it to Redis.
     * **Thread Spawning**: The service then delegates to `ProductEnrichmentService`. Instead of blocking the main Tomcat thread, it submits two parallel tasks to the custom bounded `ThreadPoolTaskExecutor` (named `prod-enrich-N`).

3. **Product Service** (Thread A) ➡️ **Inventory Service**
   * *Inside Thread A*: Executes a synchronous `RestClient` HTTP call to `GET /api/inventory/PROD-100`.
   * *Inside Inventory Service*: Queries the `Inventory DB`. Returns `availableQuantity`.
   * *Failure Handling*: If Inventory Service is down, Thread A catches the `ResourceAccessException` and instantly returns a fallback object (Quantity = -1 / "Unknown"), allowing graceful degradation.

4. **Product Service** (Thread B) ➡️ **Recommendation Service**
   * *Inside Thread B*: Executes a synchronous HTTP call to `GET /api/recommendations/PROD-100`.
   * *Inside Recommendation Service*: Computes "customers also bought" logic and returns a JSON array.

5. **Aggregation** (Main Tomcat Thread) ➡️ **Client**
   * *Action*: `CompletableFuture.allOf().join()` blocks until Thread A and Thread B complete. It combines the Product, Inventory, and Recommendation data into a single JSON payload and returns it via the Gateway to the Client.
   * *Performance*: Total latency is dictated by the *slowest* downstream service, not the sum of them.

---

### Flow 2: Order Checkout (Dependent Calls, Sagas & Kafka)
*Goal: Place an order, safely deduct inventory, charge the card, and notify downstream systems without data loss.*

1. **Client / Frontend** ➡️ **API Gateway**
   * *Action*: Client sends `POST /api/orders` with cart contents and payment info.
   * *Inside Gateway*: `JwtAuthFilter` intercepts. Validates the JWT signature. Extracts `customerId` and injects `X-Correlation-ID`. Routes to Order Service.

2. **API Gateway** ➡️ **Order Service** (Saga Orchestrator)
   * *Inside Order Service*: Receives request. Begins the Orchestration workflow.

3. **Order Service** ➡️ **Inventory Service** (Synchronous)
   * *Action*: Order Service sends `POST /api/inventory/.../reserve`.
   * *Inside Inventory Service*: Fetches stock from DB. Checks if `available >= requested`.
     * **Concurrency Control**: Updates row using `UPDATE ... SET available = available - 1, version = version + 1 WHERE version = current_version`.
     * If 100 threads hit this, 99 fail with `ObjectOptimisticLockingFailureException`. The successful one returns `200 OK`.
   * *Circuit Breaker*: If Inventory is down, Resilience4j trips the circuit in Order Service, failing the request immediately.

4. **Order Service** ➡️ **Payment Service** (Synchronous)
   * *Action*: Order Service sends `POST /api/payments/process`.
   * *Inside Payment Service*: Checks the `Idempotency Key` (Order ID). If unseen, processes the Stripe/Credit Card API. Returns `200 OK`.
   * *Saga Compensation*: If Payment **FAILS** (Timeout or 500), the Order Service catches the exception. It executes a **rollback HTTP call** back to the Inventory Service (`POST /api/inventory/.../release`) to give the stock back. The Order request is aborted.

5. **Order Service** ➡️ **PostgreSQL** (Outbox Pattern)
   * *Action*: Both downstream calls succeeded. Order Service opens a local `@Transactional` block.
   * *Inside Database*: It `INSERT`s the Order row (`status = CREATED`). In the *exact same transaction*, it `INSERT`s a row into `outbox_events` containing the JSON payload `{"orderId": "..."}`. Commits transaction. Returns `200 OK` to Gateway/Client.

6. **Order Service** (Scheduled Thread) ➡️ **Apache Kafka**
   * *Action*: The `OutboxPublisher` thread wakes up every 5 seconds.
   * *Inside Publisher*: Queries `SELECT * FROM outbox_events WHERE status = PENDING`.
   * *Kafka Producer*: Iterates over results and calls `kafkaTemplate.send("order.created", orderId, jsonPayload)`. Marks row as `PUBLISHED` in DB.

7. **Apache Kafka** ➡️ **Notification & Analytics Services** (Asynchronous Consumers)
   * *Action*: Kafka routes the message to the partition determined by the `orderId` hash.
   * *Inside Notification Service*: A background Kafka listener thread (Consumer Group: `notification-group`) pulls the event, parses the JSON, and dispatches an email to the customer.
   * *Inside Analytics Service*: A background Kafka listener thread (Consumer Group: `analytics-group`) pulls the *exact same event* simultaneously, parses it, and updates the daily revenue metrics in its local database.

---

## 🧩 Service Responsibilities

*   **API Gateway**: Central entry point. Handles routing, JWT authentication, and injects Correlation IDs for observability.
*   **Customer Service**: Manages user profiles and addresses.
*   **Product Service**: Catalog management. Implements Product Enrichment (fetching inventory and recommendations concurrently).
*   **Cart Service**: Manages shopping cart state.
*   **Order Service**: The core Saga Orchestrator. Handles order creation and coordinates with Inventory and Payment.
*   **Inventory Service**: Manages stock levels. Uses Optimistic Locking to prevent overselling.
*   **Payment Service**: Processes transactions. Must be highly available and idempotent.
*   **Notification Service**: Sends emails/SMS. Driven entirely by Kafka events.
*   **Analytics Service**: Updates sales dashboards. Driven entirely by Kafka events.
*   **Recommendation Service**: Suggests products based on purchasing history.

---

## 🗄️ Database-per-Service Architecture

**Pattern**: Each microservice owns its own database schema. 
**Why**: This prevents tight coupling. If the Order team changes their database schema, it will not break the Customer service. 
**Trade-off**: We lose the ability to perform ACID transactions and SQL `JOIN`s across domains. We solve cross-domain data querying via API Composition (Synchronous) and data consistency via Sagas and Eventual Consistency (Asynchronous).

---

## 🔄 Synchronous Communication Patterns

We use Spring `RestClient` for synchronous HTTP communication where a response is immediately required by the business flow.

### 1. Independent Calls (Concurrent)
**Scenario**: When fetching Product Details, we also need Inventory Status and Recommendations.
**Implementation**: Because Inventory and Recommendations do not depend on each other, they are executed **concurrently** using `CompletableFuture` combined with a bounded `ThreadPoolTaskExecutor`.
**Benefit**: Reduces overall latency from `T(Inv) + T(Rec)` to `max(T(Inv), T(Rec))`.

### 2. Dependent Calls (Sequential)
**Scenario**: Order Placement.
**Implementation**: We *must* reserve inventory before charging the customer's card. Therefore, the Order Service executes these calls sequentially. If Payment fails, we explicitly execute a rollback call (Saga Compensation) to release the reserved inventory.

---

## ⚡ Asynchronous Event-Driven Processing (Kafka)

**Architecture**: We use Apache Kafka (in KRaft mode) as the central nervous system.
**Flow**: When an order is successfully created, an `OrderCreatedEvent` is published to the `order.created` topic.
**Independent Consumers**: The `Notification Service`, `Analytics Service`, and `Recommendation Service` all listen to this topic using distinct **Consumer Groups** (`notification-group`, `analytics-group`, etc.). 
**Benefit**: Loose coupling. If the Notification Service crashes, the Analytics Service continues processing seamlessly. Order creation is never blocked by slow email servers.

---

## ⛓️ Distributed Transactions (Saga & Outbox)

### The Saga Pattern
Because we use Database-per-Service, we cannot use `@Transactional` across Order, Inventory, and Payment.
We use an **Orchestration-based Saga**. The Order Service dictates the flow:
1. Reserve Inventory (Success)
2. Process Payment (Failed)
3. **Compensation**: Release Inventory.
This ensures we do not permanently strand inventory if a downstream process fails.

### The Transactional Outbox Pattern
**The Problem**: Dual-writes. We cannot atomically save an Order to PostgreSQL *and* publish a message to Kafka. If Kafka goes down mid-transaction, the system state is inconsistent.
**The Solution**: We save the Order *and* an `OutboxEvent` to PostgreSQL in a single ACID transaction. A separate scheduled publisher polls the `outbox_events` table and reliably publishes them to Kafka, guaranteeing **at-least-once delivery**.

---

## 🔒 Idempotency

Because Kafka guarantees at-least-once delivery (meaning duplicates can happen), and because HTTP requests might be retried due to timeouts, all mutating endpoints are **idempotent**.
*   **Payment**: Uses an `orderId` or `paymentRequestId` as an idempotency key. If a retry hits the Payment Service, it detects the duplicate key and returns the cached successful response without charging the card twice.

---

## 🧵 Multithreading & Concurrency

We do **not** use `ForkJoinPool.commonPool()` for I/O bound REST calls, as a slow downstream service would exhaust the JVM's shared pool.
Instead, we define bounded thread pools:
```java
ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
executor.setCorePoolSize(10);
executor.setMaxPoolSize(50);
executor.setQueueCapacity(100);
executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
```
**Backpressure**: The `CallerRunsPolicy` ensures that if the queue fills up during a traffic spike, the main Tomcat thread handles the execution, naturally slowing down the intake of new requests and preventing `OutOfMemoryError`s.

---

## 🚀 Caching (Redis)

**Scenario**: Product catalog details are read-heavy and change infrequently.
**Pattern**: Cache-Aside.
**Implementation**: We use Spring Cache (`@Cacheable`) with Redis. Requests hit Redis first. On a cache miss, it queries PostgreSQL, caches the result, and returns. Product updates trigger `@CachePut` to maintain eventual consistency.

---

## 🛡️ Resiliency (Resilience4j)

Distributed systems will fail. We use Resilience4j to prevent cascading failures.
*   **Circuit Breaker**: If the Inventory Service crashes, the Order Service's HTTP calls will hang, eventually exhausting the Order Service's connection pool. The Circuit Breaker monitors failure rates. If the failure rate exceeds a threshold, it trips to `OPEN`, immediately failing-fast (or returning a fallback) without attempting the network call.
*   **Retry**: Wraps intermittent network glitches.

---

## 🔭 Observability & Tracing

*   **Correlation IDs**: The API Gateway generates an `X-Correlation-ID` (`traceId`) for every request.
*   **Micrometer Tracing**: Injects this `traceId` into the Logback MDC (Mapped Diagnostic Context) and passes it through HTTP headers and Kafka records.
*   **Logs**: `[order-service, traceId=abc-123]` allows us to trace a single user's request across the Gateway, Order, Inventory, Payment, and Kafka consumers in a centralized logging system.

---

## 🐳 How to Run the Application (Local & Docker Setup)

To run ShopSphere locally, you need **Java 21**, **Maven**, and **Docker Desktop**. We use Docker to spin up all the required infrastructure (Databases, Cache, and Message Broker) without polluting your local machine.

### Step 1: Start the Infrastructure via Docker
We have provided a `docker-compose.yml` file at the root of the project.
1. Ensure **Docker Desktop** is running.
2. Open a terminal at the root of the `ShopSphere` directory.
3. Run the following command to start PostgreSQL, Redis, Apache Kafka (KRaft mode), and Kafka UI in the background:
   ```bash
   docker-compose up -d
   ```
4. **Verify Infrastructure**:
   * **Kafka UI**: Open your browser and navigate to `http://localhost:8080` to see the Kafka cluster and topics.
   * **PostgreSQL**: Running on port `5432`.
   * **Redis**: Running on port `6379`.

### Step 2: Build the Microservices
Since this is a multi-module Maven project, you can build all services at once from the root directory.
1. Run the Maven clean install command:
   ```bash
   mvn clean install -DskipTests
   ```
   *(Note: We skip tests here for a faster startup, but you can run them later to see the concurrency tests in action).*

### Step 3: Run the Microservices
You can run the microservices using your IDE (IntelliJ IDEA / Eclipse) or via the command line. Since they run on different ports, they can all run simultaneously on your localhost.

**Order of Startup (Recommended):**
1. **API Gateway** (`ApiGatewayApplication` - Port `8080` - Note: conflicts with Kafka UI by default, you may want to change Kafka UI port to `8081` in docker-compose if running gateway locally)
2. **Inventory Service** (`InventoryApplication` - Port `8083`)
3. **Payment Service** (`PaymentApplication` - Port `8086`)
4. **Product Service** (`ProductApplication` - Port `8082`)
5. **Order Service** (`OrderApplication` - Port `8085`)
6. **Notification Service** (`NotificationApplication` - Port `8087`)

**To run via command line (Example for Order Service):**
```bash
cd order-service
mvn spring-boot:run
```

### Step 4: Verify the Application
Once the services are running, you can test the End-to-End flow:
1. **Trigger an Order**: Use Postman or cURL to hit the Order Service (`POST http://localhost:8085/api/orders`).
2. **Observe the Logs**: Watch the Order Service logs to see the Outbox Publisher pick up the event.
3. **Observe Kafka UI**: Go to `http://localhost:8080` and view the `order.created` topic to see the JSON payload.
4. **Observe Consumers**: Watch the terminal running the Notification and Analytics services to see them consume the Kafka event concurrently.

### Teardown
When you are done, you can stop the infrastructure and wipe the data volumes by running:
```bash
docker-compose down -v
```

---

## 📡 API Examples

### 1. Place an Order
```http
POST /api/orders
Content-Type: application/json

{
    "customerId": "cust-999",
    "productId": "PROD-100",
    "quantity": 2,
    "paymentMethod": "CARD"
}
```

### 2. Get Enriched Product Details
```http
GET /api/products/PROD-100/details
```
*(Executes independent calls to DB, Inventory, and Recommendations concurrently)*

---

## 🧪 Testing & Failure Scenarios

We utilize JUnit 5, Mockito, and Testcontainers.

### Preventing Overselling (Concurrency Test)
**Problem**: 100 users try to buy the last 1 item simultaneously.
**Solution**: We use JPA Optimistic Locking (`@Version`). 
**Test (`InventoryConcurrencyTest`)**: We spawn 10 threads using `ExecutorService` hitting the reserve endpoint simultaneously. The test asserts that exactly 1 thread succeeds and 9 threads throw `ObjectOptimisticLockingFailureException`, proving our database integrity is maintained under concurrent load.

### Simulated Failure Scenarios
1.  **Inventory Unavailable**: Circuit Breaker trips to OPEN. The Order request fails fast with a graceful error message.
2.  **Payment Fails**: The Order Service catches the failure and executes a Saga Compensation HTTP call to release the reserved inventory.
3.  **Kafka Broker Down**: The Outbox Publisher catches the exception. The event remains `PENDING` in the database. When Kafka recovers, the publisher picks it up, ensuring zero data loss.

---
*Developed as a demonstration of Senior Java Backend architecture principles.*
