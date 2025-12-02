# Error Handling Guide for CompletableFuture Operations

## Common Errors and Solutions

### 1. ConversionMaterializationException: "Server returned a presigned URL"

**Full Error:**
```
ConversionMaterializationException: Server returned a presigned URL for task result; cannot materialize markdown
```

**What's Happening:**

The Docling server returns a **presigned URL** (like S3 or Azure Blob Storage URL) instead of inline content. This happens when:
- Files are large
- Server uses external storage (cloud storage)
- Server has memory/bandwidth limitations
- Async conversions with long-running tasks

**Solution 1: Catch and Fallback to Async (Recommended)**

```java
ObjectMapper mapper = client.getApiClient().getObjectMapper();

try {
    // Try direct sync conversion first
    ResponseProcessFileV1ConvertFilePost response =
        client.convertFile(file, TargetName.INBODY, OutputFormat.MD);
    ConversionResults.write(response, destination, ConversionOutputType.MARKDOWN, mapper);

} catch (ConversionMaterializationException e) {
    // Server returned presigned URL - use async API
    System.out.println("Falling back to async: " + e.getMessage());

    TaskStatusResponse task = client.convertFileAsync(file, TargetName.INBODY, OutputFormat.MD);
    client.waitForTaskResult(task.getTaskId());

    // This downloads from presigned URL automatically
    ConversionResults.writeFromTask(client, task.getTaskId(), destination,
        ConversionOutputType.MARKDOWN, mapper);
}
```

**Solution 2: Use Async API from the Start**

```java
// Traditional blocking async
TaskStatusResponse task = client.convertFileAsync(file);
ResponseTaskResultV1ResultTaskIdGet result = client.waitForTaskResult(task.getTaskId());
ConversionResults.writeFromTask(client, task.getTaskId(), destination,
    ConversionOutputType.MARKDOWN, client.getApiClient().getObjectMapper());

// Or with CompletableFuture
client.convertFileAsyncFuture(file)
    .thenAccept(result -> {
        String taskId = /* get task ID from somewhere */;
        ConversionResults.writeFromTask(client, taskId, destination,
            ConversionOutputType.MARKDOWN, client.getApiClient().getObjectMapper());
    });
```

**Why writeFromTask() Works:**

The `writeFromTask()` method automatically handles presigned URLs by:
1. Calling `client.downloadTaskResultPayload(taskId)`
2. This internally downloads from the presigned URL
3. Returns actual file content (ZIP or JSON)
4. Extracts the requested format

**Key Takeaway:** The async APIs are **designed for both inline content and presigned URLs**, making them the most robust choice.

---

### 2. DoclingClientException: "Failed to buffer multipart body"

**Full Error:**
```
DoclingClientException: Failed to buffer multipart body
Caused by: FileNotFoundException: nonexistent.pdf (No such file or directory)
```

**What's Happening:**

This is a **synchronous error** that occurs when constructing the multipart request body, **before** the CompletableFuture is created. The Apache HTTP client tries to read the file immediately when building the multipart entity.

**Problem with Naive Error Handling:**

```java
// ❌ WRONG: Exception happens before CompletableFuture is created
client.convertFileAsyncFuture(nonExistentFile)
    .exceptionally(ex -> {
        // This will NEVER be called!
        return null;
    });
```

**Solution 1: Wrap in try-catch**

```java
// ✅ CORRECT: Catch synchronous exception
try {
    client.convertFileAsyncFuture(nonExistentFile)
        .exceptionally(ex -> {
            // Handles async errors
            return null;
        })
        .get();
} catch (Exception ex) {
    // Handles synchronous errors (file not found, etc.)
    System.err.println("Synchronous error: " + ex.getCause());
}
```

**Solution 2: Check File Exists First**

```java
File file = new File("document.pdf");

if (!file.exists()) {
    System.err.println("File not found: " + file);
    return;
}

// Now safe to call
client.convertFileAsyncFuture(file)
    .thenAccept(result -> { /* ... */ });
```

**Solution 3: Use CompletableFuture.supplyAsync() for Full Async**

```java
CompletableFuture<ResponseTaskResultV1ResultTaskIdGet> future =
    CompletableFuture.supplyAsync(() -> {
        try {
            return client.convertFileAsyncFuture(file).join();
        } catch (Exception e) {
            throw new CompletionException(e);
        }
    })
    .exceptionally(ex -> {
        // Now catches BOTH sync and async errors
        System.err.println("Error: " + ex.getMessage());
        return null;
    });
```

---

## Updated AsyncDoclingClient Error Handling

The `AsyncDoclingClient.convertAndSaveParallel()` method has been fixed to handle both synchronous and asynchronous errors:

```java
private static CompletableFuture<ConversionResult> convertAndSave(
        DoclingClient client,
        File file,
        Path outputDir,
        ConversionOutputType outputType) {

    try {
        // Try to start the conversion
        return client.convertFileAsyncFuture(file, TargetName.INBODY, outputType.getOutputFormat())
                .thenApply(result -> {
                    try {
                        Path outputPath = outputDir.resolve(file.getName() + outputType.getFileExtension());
                        ConversionResults.write(result, outputPath, outputType,
                                client.getApiClient().getObjectMapper());
                        return new ConversionResult(file, outputPath, null);
                    } catch (IOException e) {
                        return new ConversionResult(file, null, e);
                    }
                })
                .exceptionally(ex -> {
                    return new ConversionResult(file, null, ex);
                });
    } catch (Exception e) {
        // Handle synchronous errors (file not found, etc.)
        return CompletableFuture.completedFuture(new ConversionResult(file, null, e));
    }
}
```

**Key Points:**
- Wraps the initial call in try-catch
- Returns `CompletableFuture.completedFuture()` for synchronous errors
- `.exceptionally()` handles async errors
- All errors are captured in `ConversionResult`

---

## Best Practices Summary

### ✅ DO:

1. **Use async APIs for large files or production systems**
   ```java
   client.convertFileAsyncFuture(file)
   ```

2. **Handle both sync and async errors**
   ```java
   try {
       client.convertFileAsyncFuture(file)
           .exceptionally(ex -> { /* async errors */ })
           .get();
   } catch (Exception ex) {
       // sync errors
   }
   ```

3. **Check file existence for better UX**
   ```java
   if (!file.exists()) {
       return CompletableFuture.failedFuture(
           new FileNotFoundException(file.toString())
       );
   }
   ```

4. **Use writeFromTask() for robustness**
   ```java
   ConversionResults.writeFromTask(client, taskId, destination, outputType, mapper);
   ```

5. **Let AsyncDoclingClient handle batch errors**
   ```java
   AsyncDoclingClient.convertAndSaveParallel(client, files, outputDir, outputType)
       .thenAccept(results -> {
           long failures = results.stream().filter(ConversionResult::isFailure).count();
           System.out.println("Failures: " + failures);
       });
   ```

### ❌ DON'T:

1. **Don't rely only on .exceptionally() for file I/O errors**
   ```java
   // ❌ Won't catch FileNotFoundException
   client.convertFileAsyncFuture(nonExistentFile)
       .exceptionally(ex -> null);
   ```

2. **Don't use sync API for presigned URL scenarios**
   ```java
   // ❌ May throw ConversionMaterializationException
   ConversionResults.write(syncResponse, dest, outputType, mapper);
   ```

3. **Don't ignore error causes**
   ```java
   // ❌ Loses error context
   .exceptionally(ex -> null);

   // ✅ Better
   .exceptionally(ex -> {
       log.error("Conversion failed", ex);
       return fallbackValue;
   });
   ```

---

## Testing Error Handling

### Updated Example 6 (from UsageCompletableFuture.java)

```java
private static void example6ErrorHandling(DoclingClient client) throws Exception {
    System.out.println("Example 6: Error handling");

    // Scenario 1: File doesn't exist - caught synchronously
    File nonExistent = new File("nonexistent.pdf");
    try {
        client.convertFileAsyncFuture(nonExistent).get();
        System.out.println("  ✗ Should have thrown exception");
    } catch (Exception ex) {
        System.out.println("  ✓ Caught file not found error: " +
            ex.getCause().getClass().getSimpleName());
    }

    // Scenario 2: Invalid URL - handled asynchronously
    String invalidUrl = "https://invalid-domain-12345.com/doc.pdf";
    client.convertUrlAsyncFuture(invalidUrl)
            .exceptionally(ex -> {
                System.out.println("  ✓ Caught async error: " +
                    ex.getCause().getClass().getSimpleName());
                return null;
            })
            .get();

    // Scenario 3: Chaining with error recovery
    client.convertFileAsyncFuture(SAMPLE_FILE)
            .thenApply(result -> {
                // Simulate processing error
                throw new RuntimeException("Simulated error");
            })
            .exceptionally(ex -> {
                System.out.println("  ✓ Recovered from error: " + ex.getMessage());
                return null; // Fallback
            })
            .thenAccept(result -> {
                System.out.println("  ✓ Pipeline completed with error recovery");
            })
            .get();
}
```

---

## Quick Reference

| Error Type | When It Happens | How to Handle |
|------------|----------------|---------------|
| `FileNotFoundException` | Before CompletableFuture created | Wrap in try-catch or check `file.exists()` |
| `ConversionMaterializationException` | Server returns presigned URL | Use async API + `writeFromTask()` |
| `DoclingHttpException` | HTTP 4xx/5xx errors | `.exceptionally()` or try-catch |
| `DoclingNetworkException` | Network failures | `.exceptionally()` - automatically retried |
| `DoclingTimeoutException` | Task timeout | `.exceptionally()` or `.orTimeout()` |
| `DoclingTaskFailureException` | Task processing failed | `.exceptionally()` |

---

## Automatic Retry for Transient Network Errors

**NEW:** The library now automatically retries transient network errors during task polling!

### What Gets Retried

When waiting for async tasks to complete (`waitForTaskResult`), the client now automatically retries:
- Network connection drops (`HTTP/1.1 header parser received no bytes`)
- I/O errors (`EOFException`)
- Temporary connection failures

### Retry Strategy

- **Up to 3 attempts** for each poll operation
- **2-second wait** between retry attempts
- **Consecutive error tracking** - resets on successful poll
- **Intelligent exception filtering** - doesn't retry task failures or HTTP errors

### Example Log Output

```
INFO  Task abc123 pending status=started after 10s
WARN  Task abc123 poll failed (attempt 1/3), will retry: HTTP/1.1 header parser received no bytes
WARN  Task abc123 poll failed (attempt 2/3), will retry: HTTP/1.1 header parser received no bytes
INFO  Task abc123 pending status=started after 12s  # Success after retry!
```

### What Doesn't Get Retried

The following errors fail immediately without retry:
- `DoclingTaskFailureException` - Task processing failed
- `DoclingTimeoutException` - Task timeout exceeded
- `DoclingHttpException` - HTTP 4xx/5xx errors

### Impact on Your Code

**No code changes needed!** The retry logic is transparent:

```java
// This now handles transient network errors automatically
client.convertFileAsyncFuture(file)
    .thenAccept(result -> System.out.println("Success!"))
    .exceptionally(ex -> {
        // Only called if retries exhausted or non-retryable error
        System.err.println("Failed: " + ex.getMessage());
        return null;
    });
```

### Disable Retries

If you need to disable retries for testing:

```java
// Set very low timeout to avoid retries
System.setProperty("POLL_MAX_SECONDS", "5");
System.setProperty("POLL_WAIT_SECONDS", "1");
```

---

## Conclusion

The key to robust error handling with CompletableFuture:

1. **Understand the difference** between synchronous (before future creation) and asynchronous (during execution) errors
2. **Use try-catch** for synchronous errors (file I/O, validation)
3. **Use .exceptionally()** for asynchronous errors (network, conversion)
4. **Use async APIs** (`*AsyncFuture()` methods) for production robustness
5. **Use helper methods** (`AsyncDoclingClient`) that handle both error types
6. **Trust the automatic retry** - transient network errors are handled transparently

The library is designed to make error handling straightforward - just remember that CompletableFuture can't catch errors that happen before it's created!
