# Microservices + Dynatrace (OpenTelemetry) Demo

Three Spring Boot services — `customer-service`, `payment-service`, `order-service` —
instrumented with the **OpenTelemetry Java agent** and shipping traces, metrics, and
logs straight to Dynatrace over OTLP. No manual tracing code required; `order-service`
calls the other two over HTTP so you get a real distributed trace end to end.

## Architecture

```
client -> order-service (8080) -> customer-service (8081)
                                -> payment-service (8082)
```

## 1. Get a Dynatrace API token

1. In Dynatrace, go to **Access Tokens** (Settings → Access Tokens, or search "Access Tokens").
2. Create a new token with these scopes:
   - `openTelemetryTrace.ingest` (traces)
   - `metrics.ingest` (metrics)
   - `logs.ingest` (logs)
3. Copy the token (starts with `dt0c01.`) and your environment ID (the subdomain in
   your Dynatrace URL, e.g. `abc12345` in `https://abc12345.live.dynatrace.com`).

## 2. Configure environment

```bash
cp .env.example .env
```

Edit `.env`:

```
DT_OTLP_ENDPOINT=https://abc12345.live.dynatrace.com/api/v2/otlp
DT_API_TOKEN=dt0c01.XXXXXXXXXXXXXXXXXXXXXXXX
```

The OpenTelemetry SDK automatically appends `/v1/traces`, `/v1/metrics`, `/v1/logs`
to this base endpoint — you don't add those yourself.

## 3. Build and run

```bash
docker compose up --build
```

Each Dockerfile downloads `opentelemetry-javaagent.jar` at build time and starts
the app with `-javaagent:/app/otel-javaagent.jar`. That agent auto-instruments:

- Spring MVC request handling (spans + duration/error metrics)
- `RestTemplate` calls between services (with trace-context propagation)
- JVM metrics (CPU, heap, GC, threads)
- SLF4J/Logback logs, correlated with the active trace

## 4. Generate traffic

```bash
# A successful order
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"1001","item":"Headphones","amount":49.99}'

# An order for a customer that doesn't exist (produces a 404 + error span)
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"9999","item":"Mouse","amount":19.99}'
```

Run the first command in a loop for a minute or two so you have enough data
points for charts:

```bash
for i in $(seq 1 50); do
  curl -s -X POST http://localhost:8080/orders \
    -H "Content-Type: application/json" \
    -d '{"customerId":"1001","item":"Widget","amount":25.50}' > /dev/null
  sleep 1
done
```

## 5. Find your data in Dynatrace

- **Distributed Traces**: go to the *Distributed Traces* app, filter by
  `service.name = order-service`. You'll see the full order → customer → payment
  trace tree with timings for each hop.
- **Services**: the *Services* app auto-lists `order-service`, `customer-service`,
  `payment-service` once traces arrive, each with response time, failure rate,
  and throughput already charted.
- **Logs**: the *Logs* app shows ingested log lines, correlated with the trace
  that produced them (click a trace span → "Logs" tab).
- **Metrics**: search for `payments.processed` in the *Metrics* app to see the
  custom counter from `PaymentController` (split by `result=success|failed`).

## 6. Build a dashboard

1. Go to **Dashboards → Create dashboard**.
2. Add a tile → **Services** → pick `order-service` → chart "Response time" and
   "Failure rate".
3. Add a tile → **Metric** → search `payments.processed` → split by dimension
   `result` → bar chart, to show payment success vs. failure over time.
4. Add a tile → **Logs** → DQL query, e.g.:
   ```
   fetch logs
   | filter dt.entity.service == "SERVICE-XXXX" and loglevel == "ERROR"
   ```
   (get the exact `dt.entity.service` ID from the Services app URL, or just
   filter by `k8s.container.name` / `service.name` resource attribute instead).
5. Add a tile → **Distributed Traces** → saved view filtered to `order-service`
   with failed spans only, to track error orders live.

## Notes on this demo

- Data is stored in-memory (a fixed map of 3 fake customers) — there's no
  database, to keep the demo dependency-free. Swap in a real DB and the
  javaagent will auto-instrument JDBC calls too, no extra config needed.
- Payment failures are randomized (~10%) purely to generate interesting
  error/latency data for dashboards.
- To also monitor host-level CPU/memory (not just JVM-level), install
  **Dynatrace OneAgent** on the Docker host or Kubernetes node — the
  OpenTelemetry agent only covers the JVM process itself, not the OS/host.
