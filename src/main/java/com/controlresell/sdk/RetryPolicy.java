package com.controlresell.sdk;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/** How a call is retried. */
public final class RetryPolicy {
  private final int maxRetries;
  private final Duration baseDelay;
  private final Duration maxDelay;

  /**
   * @param maxRetries retries after the first attempt (2 by default); 0 turns them off
   * @param baseDelay the first delay, doubled at each retry, with a random part; a {@code Retry-After} takes precedence
   * @param maxDelay the longest delay between two attempts
   */
  public RetryPolicy(int maxRetries, Duration baseDelay, Duration maxDelay) {
    this.maxRetries = maxRetries;
    this.baseDelay = baseDelay;
    this.maxDelay = maxDelay;
  }

  /** Two retries, 500 ms doubled each time, 30 s at most. */
  public static RetryPolicy defaults() {
    return new RetryPolicy(2, Duration.ofMillis(500), Duration.ofSeconds(30));
  }

  /** No retry. */
  public static RetryPolicy none() {
    return new RetryPolicy(0, Duration.ZERO, Duration.ZERO);
  }

  public int maxRetries() {
    return maxRetries;
  }

  Duration backoff(int attempt) {
    long base = baseDelay.toMillis();
    long millis = base * (1L << attempt) + (base > 1 ? ThreadLocalRandom.current().nextLong(base / 2 + 1) : 0);
    return Duration.ofMillis(Math.min(millis, maxDelay.toMillis()));
  }

  Duration capped(Duration delay) {
    return delay.compareTo(maxDelay) > 0 ? maxDelay : delay;
  }
}
