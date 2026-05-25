## Trace ID: test-oom-trace-001
**Timestamp:** 2026-05-18T10:00:00.123456Z
**Duration:** 312ms
**Status:** Error - HTTP 500

### Request Details
- Endpoint: /api/orders/process
- Method: POST
- Services involved: order-service
- Span count: 3

### Issue Summary
HTTP 500 error caused by OutOfMemoryError during order processing — JVM heap at 96% capacity triggered garbage collection thrashing and eventual allocation failure.

### Trace Analysis
- Root cause: The span `processOrder` threw `java.lang.OutOfMemoryError: Java heap space` after 280ms of execution. The request was processing a large batch of 500 order items.
- Slowest operations:
  - `processOrder`: 280ms (terminated with exception)
  - `validateItems`: 22ms
  - `GET /api/orders/process` (root span): 312ms total
- Error details: `java.lang.OutOfMemoryError: Java heap space` in span event with type `exception`. Status code: `STATUS_CODE_ERROR`.

### Log Insights
- Key messages:
  - `"Processing batch of 500 order items"` (INFO, order-service)
  - `"java.lang.OutOfMemoryError: Java heap space"` (ERROR, order-service)
  - `"Request failed with status 500 for POST /api/orders/process"` (ERROR, order-service)
- Error context: The OutOfMemoryError occurred during batch processing, indicating the 500-item batch exceeded available heap space.
- Business logic: The order processing endpoint accepts variable batch sizes. Large batches (>200 items) require significantly more heap space for in-memory processing.

### Three-Way Correlation
**System state at 2026-05-18T10:00:00Z:**
- CPU: system_cpu_usage = 12% | process_cpu_usage = 8%
- Memory: jvm_memory_used_bytes = 720MB (96% of 750MB max)
- GC: jvm_gc_overhead = 14.2%
- Active requests: http_server_active_requests = 3
- Worker pool: active=4, idle=16, queue=0

**Causal chain (trace + logs + metrics):**
jvm_memory_used_bytes at 720MB/750MB (96%) (metric) → jvm_gc_overhead at 14.2% (metric) → batch processing allocated large collections for 500 items (log: "Processing batch of 500 order items") → OutOfMemoryError thrown in `processOrder` span after 280ms (trace) → log: "java.lang.OutOfMemoryError: Java heap space" → HTTP 500 returned to client

**What metrics RULE OUT:**
- CPU at 12% system / 8% process — not CPU-bound. The issue is purely memory-related.
- Worker pool idle=16, queue=0 — not thread-starved. Plenty of worker threads available.
- http_server_active_requests=3 — not a traffic spike. Normal request volume.
- http_client metrics show no downstream calls — the failure is internal to order-service.

### Severity
CRITICAL - Application cannot process large order batches due to heap exhaustion. The 96% memory utilization with 14.2% GC overhead indicates the JVM is operating dangerously close to its memory limit. Any batch larger than ~200 items risks triggering OutOfMemoryError.

### Recommendations
1. Increase JVM heap size from 750MB to at least 1.5GB (`-Xmx1536m`) — the current 750MB max is insufficient for batch processing of 500+ items
2. Investigate potential memory leak using heap dump analysis (`-XX:+HeapDumpOnOutOfMemoryError`). If memory usage stays at 96% even without large batches, a leak is likely.
3. Implement streaming/chunked processing for large batches instead of loading all 500 items into memory simultaneously
4. Set up alerting: trigger warning when `jvm_memory_used_bytes > 85%` of max and `jvm_gc_overhead > 10%` to catch memory pressure before it causes failures

---

## Cross-Trace Summary
- **Healthy vs unhealthy comparison:** Only one trace analyzed in this batch. The error is specific to large-batch order processing.
- **Trend:** Single data point — cannot determine trend. Recommend analyzing additional traces over a longer time window.
- **Root cause:** Memory exhaustion — JVM heap at 96% with 14.2% GC overhead causing OutOfMemoryError during batch processing.
- **Priority actions:**
  1. Increase heap size immediately (operational fix)
  2. Implement batch chunking (architectural fix)
  3. Add memory alerting at 85% threshold
