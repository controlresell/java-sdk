package com.controlresell.sdk;

import com.controlresell.sdk.generated.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Runs a call with its retries: a rate limit, a gateway or availability error (502, 503, 504), a 409
 * {@code idempotency_in_progress} and a network error are retried; a 500 never is. A {@code POST} carries an
 * {@code Idempotency-Key}, the same one at each attempt.
 */
final class Retrier {
  static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
  private static final String IDEMPOTENCY_IN_PROGRESS = "idempotency_in_progress";
  private static final Set<Integer> RETRIED_STATUSES = Set.of(429, 502, 503, 504);

  private final RetryPolicy policy;
  private final ObjectMapper mapper;

  Retrier(RetryPolicy policy, ObjectMapper mapper) {
    this.policy = policy;
    this.mapper = mapper;
  }

  <T> T execute(boolean write, ControlResell.HttpCall<T> call) {
    Map<String, String> headers = new HashMap<>();
    if (write) headers.put(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString());
    for (int attempt = 0; ; attempt++) {
      boolean last = attempt >= policy.maxRetries();
      try {
        return call.call(headers);
      } catch (ApiException e) {
        if (e.getCode() == 0) {
          if (last) throw new ControlResellError.Connection(e.getCause() != null ? e.getCause() : e);
          pause(policy.backoff(attempt));
          continue;
        }
        if (last || !shouldRetry(e)) throw Errors.of(e, mapper);
        Long retryAfter = Errors.retryAfter(e);
        pause(retryAfter != null ? policy.capped(Duration.ofSeconds(retryAfter)) : policy.backoff(attempt));
      }
    }
  }

  private boolean shouldRetry(ApiException e) {
    if (RETRIED_STATUSES.contains(e.getCode())) return true;
    return e.getCode() == 409 && IDEMPOTENCY_IN_PROGRESS.equals(Errors.of(e, mapper).code());
  }

  private static void pause(Duration delay) {
    try {
      Thread.sleep(delay.toMillis());
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new ControlResellError.Connection(interrupted);
    }
  }
}
