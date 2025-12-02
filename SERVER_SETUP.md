# Docling Server Setup Guide

## Overview

The Docling Java wrapper requires a running Docling server to function. The server handles the actual document conversion, while this library provides a convenient Java API.

## Error: "Cannot connect to Docling server"

If you see this error:
```
java.net.ConnectException
ERROR: Cannot connect to Docling server at http://127.0.0.1:5001
```

It means no Docling server is running. Follow the setup instructions below.

---

## Quick Start: Docker (Recommended)

The easiest way to run a Docling server is using Docker:

```bash
# Pull and run the Docling server
docker run -p 5001:5001 docling/docling-serve:latest

# Or with custom configuration
docker run -p 5001:5001 \
  -e DOCLING_LOG_LEVEL=INFO \
  docling/docling-serve:latest
```

**Note:** Replace `docling/docling-serve` with the actual Docker image name from the Docling project. Check https://github.com/DS4SD/docling for the latest image details.

---

## Alternative Setup Methods

### 1. Python Installation (Local Development)

If you prefer running the server directly:

```bash
# Install Docling (requires Python 3.9+)
pip install docling

# Start the server
docling serve --port 5001
```

Check the [official Docling documentation](https://github.com/DS4SD/docling) for detailed installation instructions.

### 2. Kubernetes Deployment

For production deployments:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: docling-server
spec:
  replicas: 3
  selector:
    matchLabels:
      app: docling-server
  template:
    metadata:
      labels:
        app: docling-server
    spec:
      containers:
      - name: docling
        image: docling/docling-serve:latest
        ports:
        - containerPort: 5001
        resources:
          requests:
            memory: "2Gi"
            cpu: "1000m"
          limits:
            memory: "4Gi"
            cpu: "2000m"
---
apiVersion: v1
kind: Service
metadata:
  name: docling-service
spec:
  selector:
    app: docling-server
  ports:
  - port: 5001
    targetPort: 5001
  type: LoadBalancer
```

### 3. Remote Server

If you have access to a remote Docling server:

```bash
# Set the base URL
export DOCLING_BASE_URL=https://docling.example.com

# Run your Java application
./gradlew run -PmainClass=com.docling.client.UsageCompletableFuture
```

Or in Java code:

```java
DoclingClient client = new DoclingClient(
    "https://docling.example.com",
    "your-api-key",  // if authentication is required
    Duration.ofMinutes(10)
);
```

---

## Configuration

### Environment Variables

```bash
# Server URL (default: http://127.0.0.1:5001)
export DOCLING_BASE_URL=http://localhost:5001

# API Key (if server requires authentication)
export DOCLING_API_KEY=your-secret-key

# Logging level
export DOCLING_LOG_LEVEL=INFO

# HTTP tracing (for debugging)
export DOCLING_TRACE_HTTP=true

# Task polling settings
export POLL_MAX_SECONDS=900    # 15 minutes timeout
export POLL_WAIT_SECONDS=10    # Poll every 10 seconds
```

### Server Requirements

**Minimum:**
- 2 GB RAM
- 2 CPU cores
- 10 GB disk space

**Recommended (for production):**
- 4+ GB RAM
- 4+ CPU cores
- 50+ GB disk space
- SSD storage for better performance

---

## Verifying Server Connection

### Using the Health Check

```bash
# From command line
curl http://localhost:5001/health

# Expected response:
{"status":"ok"}
```

### In Java Code

```java
DoclingClient client = DoclingClient.fromEnv();

try {
    HealthCheckResponse health = client.health();
    System.out.println("Server status: " + health.getStatus());
    System.out.println("✓ Connected successfully!");
} catch (Exception e) {
    System.err.println("✗ Cannot connect: " + e.getMessage());
}
```

---

## Running the Examples

Once the server is running, you can execute the examples:

```bash
# CompletableFuture examples
./gradlew run -PmainClass=com.docling.client.UsageCompletableFuture

# Synchronous examples
./gradlew run -PmainClass=com.docling.client.UsageSync

# Async examples
./gradlew run -PmainClass=com.docling.client.UsageAsync

# Async streaming examples
./gradlew run -PmainClass=com.docling.client.UsageAsyncStreaming

# OCR options examples
./gradlew run -PmainClass=com.docling.client.UsageOcrOptions
```

---

## Common Issues

### 1. "Connection refused"

**Symptom:** `java.net.ConnectException: Connection refused`

**Solution:**
- Ensure Docling server is running: `docker ps | grep docling`
- Check the port: `netstat -an | grep 5001` or `lsof -i :5001`
- Verify firewall rules allow connections to port 5001

### 2. "Server returned 404"

**Symptom:** `DoclingHttpException: 404 Not Found`

**Solution:**
- Wrong base URL or API endpoints changed
- Check server version compatibility
- Verify API paths in the server documentation

### 3. "Timeout waiting for task"

**Symptom:** `DoclingTimeoutException: Task did not complete within timeout`

**Solution:**
- Increase timeout: `export POLL_MAX_SECONDS=1800` (30 minutes)
- Check server resources (CPU, memory)
- Verify document isn't too large/complex
- Check server logs for processing errors

### 4. "Server is slow"

**Symptoms:** Long conversion times, frequent timeouts

**Solutions:**
- Increase server resources (CPU, RAM)
- Use async APIs for better concurrency
- Enable streaming for large files: `BufferedDoclingClient`
- Scale horizontally (multiple server instances)
- Consider caching converted documents

---

## Development vs Production

### Development Setup

```bash
# Simple Docker container
docker run -p 5001:5001 docling/docling-serve:latest

# Java client
DoclingClient client = DoclingClient.fromEnv();
```

**Characteristics:**
- Single instance
- No authentication
- Default timeouts
- Local filesystem storage

### Production Setup

```bash
# Docker with resource limits and health checks
docker run -p 5001:5001 \
  --memory=4g \
  --cpus=2 \
  --restart=unless-stopped \
  --health-cmd="curl -f http://localhost:5001/health || exit 1" \
  --health-interval=30s \
  --health-timeout=10s \
  --health-retries=3 \
  -e DOCLING_LOG_LEVEL=INFO \
  -e DOCLING_API_KEY=secret-key \
  -v /data/docling:/app/data \
  docling/docling-serve:latest
```

**Java client configuration:**

```java
// Custom executor for better concurrency
ExecutorService executor = Executors.newFixedThreadPool(20,
    new ThreadFactoryBuilder()
        .setNameFormat("docling-async-%d")
        .setDaemon(true)
        .setPriority(Thread.NORM_PRIORITY)
        .build());

// Production-ready client
DoclingClient client = new DoclingClient(
    System.getenv("DOCLING_BASE_URL"),
    System.getenv("DOCLING_API_KEY"),
    Duration.ofMinutes(15)  // Longer timeout for large docs
);

client.setAsyncExecutor(executor);
client.setRetryPolicy(RetryPolicy.builder()
    .maxRetries(5)
    .initialDelayMs(1000)
    .maxDelayMs(30000)
    .build());
client.setMetricsEnabled(true);
client.setLogLevel("INFO");

// Use in application...

// Clean shutdown
executor.shutdown();
executor.awaitTermination(30, TimeUnit.SECONDS);
```

**Characteristics:**
- Multiple instances behind load balancer
- Authentication enabled
- Extended timeouts
- External storage (S3, Azure Blob)
- Monitoring and metrics
- Retry policies
- Connection pooling

---

## Monitoring

### Server Metrics

Monitor these metrics in production:

- **Request rate** - conversions per second
- **Success rate** - % of successful conversions
- **Average latency** - time per conversion
- **Queue depth** - pending async tasks
- **Resource usage** - CPU, memory, disk

### Client-Side Monitoring

The Java client provides built-in metrics:

```java
client.setMetricsEnabled(true);

// Access metrics after operations
System.out.println("Requests: " + client.getMetrics().getTotalRequests());
System.out.println("Failures: " + client.getMetrics().getFailedRequests());
System.out.println("Avg latency: " + client.getMetrics().getAverageLatency() + "ms");
```

---

## Getting Help

If you're still having issues:

1. **Check server logs:**
   ```bash
   docker logs <container-id>
   ```

2. **Enable HTTP tracing:**
   ```bash
   export DOCLING_TRACE_HTTP=true
   export DOCLING_LOG_LEVEL=DEBUG
   ```

3. **Test with curl:**
   ```bash
   curl -X POST http://localhost:5001/v1/convert/file \
     -F "file=@document.pdf" \
     -F "to_formats=md"
   ```

4. **Check Docling documentation:**
   - GitHub: https://github.com/DS4SD/docling
   - Issues: https://github.com/DS4SD/docling/issues

5. **Java wrapper issues:**
   - GitHub: https://github.com/yourusername/docling-java-wrapper/issues

---

## Next Steps

Once your server is running:

1. **Run the examples** to see the library in action
2. **Read CLAUDE.md** for development patterns
3. **Check ERROR_HANDLING_GUIDE.md** for robust error handling
4. **Review COMPLETABLE_FUTURE_UPGRADE.md** for modern async patterns

Happy document converting! 🚀
