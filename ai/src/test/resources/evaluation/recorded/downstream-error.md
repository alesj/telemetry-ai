## Trace ID: test-downstream-403-003
**Timestamp:** 2026-05-18T10:30:00.789012Z
**Duration:** 85ms
**Status:** Error - HTTP 500

### Request Details
- Endpoint: /api/payments/charge
- Method: POST
- Services involved: payment-proxy, payment-gateway (downstream)
- Span count: 3

### Issue Summary
HTTP 500 error at payment-proxy caused by downstream payment-gateway returning 403 Forbidden — the API credentials for the payment gateway have expired or been revoked.

### Trace Analysis
- Root cause: The client span calling payment-gateway returned HTTP 403 Forbidden. The payment-proxy translated this into an HTTP 500 to the caller because it doesn't handle 403 responses from downstream services.
- Slowest operations:
  - `POST /api/payments/charge` (root span, payment-proxy): 85ms total
  - `POST /v2/charges` (client span, payment-gateway call): 62ms — returned 403
  - `deserialize-request`: 8ms
- Error details:
  - Client span `POST /v2/charges` has `http.response.status_code=403` and `error.type=403`
  - Root span has exception event: `"Received: '403 Forbidden' when invoking REST Client method: 'io.quarkus.payment.proxy.GatewayClient#charge'"` with type `org.jboss.resteasy.reactive.ClientWebApplicationException`
  - Root span status: `STATUS_CODE_ERROR`

### Log Insights
- Key messages:
  - `"Calling payment gateway for charge of $125.50"` (INFO, payment-proxy)
  - `"Received: '403 Forbidden' when invoking REST Client method: 'io.quarkus.payment.proxy.GatewayClient#charge'"` (ERROR, payment-proxy)
  - `"Authorization failed for payment-gateway: API key rejected"` (ERROR, payment-proxy)
  - `"Payment charge failed for order order-789: downstream service returned 403"` (ERROR, payment-proxy)
- Error context: The 403 response indicates the payment gateway rejected the API credentials. This is not a transient network error — it's an authorization/authentication failure that will persist until credentials are updated.
- Business logic: The payment-proxy forwards charge requests to an external payment-gateway service. The gateway validates API keys on every request. A 403 means the key is invalid, expired, or revoked.

### Three-Way Correlation
**System state at 2026-05-18T10:30:00Z:**
- CPU: system_cpu_usage = 5% | process_cpu_usage = 3%
- Memory: jvm_memory_used_bytes = 280MB (37% of 750MB max)
- GC: jvm_gc_overhead = 0.8%
- Active requests: http_server_active_requests = 2
- Worker pool: active=2, idle=18, queue=0

**Causal chain (trace + logs + metrics):**
Payment-gateway returned 403 Forbidden (trace: client span `POST /v2/charges` with status 403) → log: "Authorization failed for payment-gateway: API key rejected" → payment-proxy caught ClientWebApplicationException (trace: root span exception event) → HTTP 500 returned to caller (trace: root span status ERROR) → All system metrics normal (metrics: CPU 5%, memory 37%, threads available) — this is an application-level error, not an infrastructure issue

**What metrics RULE OUT:**
- CPU at 5% system / 3% process — not CPU-related at all.
- Memory at 280MB/750MB (37%) — healthy, well within limits.
- Worker pool idle=18, queue=0 — no thread contention.
- http_server_active_requests=2 — normal traffic, not overloaded.
- All infrastructure metrics are healthy. This is purely an application-level authorization failure with the downstream payment gateway.

### Severity
HIGH - All payment processing is blocked. Every charge request will fail with 403 until the API credentials are updated. Revenue impact is immediate — no payments can be processed.

### Recommendations
1. Rotate/renew the payment-gateway API credentials immediately — check if the key expired or was revoked by the gateway provider
2. Add explicit handling for 403 responses in payment-proxy: return HTTP 503 (Service Unavailable) instead of 500, with a meaningful error message to callers
3. Implement credential monitoring: set up an alert when payment-gateway returns 403 more than once in 5 minutes (indicates systemic auth failure vs. single bad request)
4. Consider adding a circuit breaker to avoid hammering the payment gateway with requests that will all fail with 403

---

## Cross-Trace Summary
- **Healthy vs unhealthy comparison:** Only one trace analyzed. All infrastructure metrics are normal — the issue is isolated to payment gateway authorization.
- **Trend:** Cannot determine from single trace. If all recent payment traces show 403, the API key is definitively expired/revoked.
- **Root cause:** Downstream payment-gateway API credential failure (403 Forbidden). Not an infrastructure or resource issue.
- **Priority actions:**
  1. Renew payment-gateway API credentials (critical, blocks all payments)
  2. Add proper 403 handling in payment-proxy
  3. Add credential-failure alerting
