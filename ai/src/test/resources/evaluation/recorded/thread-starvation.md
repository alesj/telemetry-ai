## Trace ID: test-thread-starvation-002
**Timestamp:** 2026-05-18T10:15:00.654321Z
**Duration:** 2412ms
**Status:** Success - HTTP 200

### Request Details
- Endpoint: /api/users/search
- Method: GET
- Services involved: user-service, database-proxy
- Span count: 4

### Issue Summary
HTTP 200 returned but with 2412ms latency (expected <200ms) due to worker thread pool exhaustion — request was queued for 812ms before a worker thread became available.

### Trace Analysis
- Root cause: The root span `GET /api/users/search` took 2412ms total. Of this, 812ms was spent queued waiting for a worker thread (gap between request arrival and first child span start). The actual processing took 1600ms, which is also elevated due to database contention.
- Slowest operations:
  - `GET /api/users/search` (root span): 2412ms total (812ms queued + 1600ms processing)
  - `db.query` (child span): 1580ms — database query executing full table scan
  - `serialize-response`: 18ms
  - `validate-params`: 2ms
- Error details: No errors — HTTP 200 returned, but SLA of 500ms breached by 4.8x.

### Log Insights
- Key messages:
  - `"Request queued for 812ms waiting for worker thread"` (WARN, user-service)
  - `"Worker pool exhausted: active=20/20, queued=12"` (WARN, user-service)
  - `"Slow query detected: SELECT * FROM users WHERE name LIKE '%smith%' took 1580ms"` (WARN, database-proxy)
- Error context: The worker pool was fully saturated with all 20 threads active and 12 requests queued. The slow database queries are holding threads longer than expected, causing the queue to build up.
- Business logic: The user search endpoint performs a LIKE query which, for common surnames, triggers a full table scan on the 2M-row users table.

### Three-Way Correlation
**System state at 2026-05-18T10:15:00Z:**
- CPU: system_cpu_usage = 15% | process_cpu_usage = 11%
- Memory: jvm_memory_used_bytes = 350MB (47% of 750MB max)
- GC: jvm_gc_overhead = 1.2%
- Active requests: http_server_active_requests = 22
- Worker pool: active=20, idle=0, queue=12

**Causal chain (trace + logs + metrics):**
Slow database query (1580ms for LIKE '%smith%' full table scan) (trace: `db.query` span) → worker threads held for 1.5s each (trace: long spans) → worker_pool_idle=0, worker_pool_active=20/20, queue_size=12 (metrics) → log: "Worker pool exhausted: active=20/20, queued=12" → new request queued 812ms (trace: gap in root span) → total latency 2412ms (trace) → SLA breach

**What metrics RULE OUT:**
- CPU at 15% system / 11% process — not CPU-bound. Threads are blocked on I/O (database), not computing.
- Memory at 350MB/750MB (47%) — not memory pressure. GC overhead at 1.2% is negligible.
- The issue is thread starvation caused by slow database queries holding worker threads too long.

### Severity
HIGH - Request latency 4.8x above SLA (2412ms vs 500ms target). Worker pool completely saturated with 12 requests queued. While requests eventually complete (HTTP 200), sustained load at this level risks cascading timeouts and client-side errors.

### Recommendations
1. Optimize the user search query: add an index on `users.name` or switch from `LIKE '%smith%'` to full-text search to reduce query time from 1580ms to <50ms
2. Increase worker pool size from 20 to 40 threads (`quarkus.thread-pool.max-threads=40`) as a short-term mitigation while query optimization is in progress
3. Add a query timeout of 500ms to prevent single slow queries from monopolizing worker threads
4. Set up alerting: trigger warning when `worker_pool_queue_size > 5` or `worker_pool_idle = 0` for more than 30 seconds

---

## Cross-Trace Summary
- **Healthy vs unhealthy comparison:** Only one trace analyzed. Compare with baseline traces for this endpoint to confirm normal latency is <200ms.
- **Trend:** Single data point — queue_size=12 suggests this is an ongoing condition, not a one-off spike. Monitor over next hour.
- **Root cause:** Slow database queries (full table scan on LIKE queries) holding worker threads, causing pool exhaustion and request queuing.
- **Priority actions:**
  1. Add database index on users.name (fixes root cause)
  2. Increase thread pool size (short-term mitigation)
  3. Add worker pool queue alerting
