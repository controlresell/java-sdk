package com.controlresell.sdk;

import java.util.Map;

/**
 * An error answered by the API, read from its envelope {@code {"error": {code, message, params, requestId}}}.
 *
 * <p>{@link #code()} is an open string ({@code item_not_found}, {@code rate_limited}...): match the codes you handle
 * and treat any other one by its {@link #status()}.
 */
public class ControlResellError extends RuntimeException {
  private final int status;
  private final String code;
  private final Map<String, String> params;
  private final String requestId;

  public ControlResellError(String message, int status, String code, Map<String, String> params, String requestId) {
    super(message);
    this.status = status;
    this.code = code;
    this.params = params == null ? Map.of() : Map.copyOf(params);
    this.requestId = requestId;
  }

  /** The HTTP status. */
  public int status() {
    return status;
  }

  /** The error's code, an open string. */
  public String code() {
    return code;
  }

  /** The error's parameters. */
  public Map<String, String> params() {
    return params;
  }

  /** The identifier to give the support, when the API sent one. */
  public String requestId() {
    return requestId;
  }

  /** 400: the request is malformed. */
  public static class BadRequest extends ControlResellError {
    BadRequest(String m, int s, String c, Map<String, String> p, String r) { super(m, s, c, p, r); }
  }

  /** 401: no credential, or an unknown, revoked or expired one. */
  public static class Authentication extends ControlResellError {
    Authentication(String m, int s, String c, Map<String, String> p, String r) { super(m, s, c, p, r); }
  }

  /** 402: the seller's plan does not include this. */
  public static class PaymentRequired extends ControlResellError {
    PaymentRequired(String m, int s, String c, Map<String, String> p, String r) { super(m, s, c, p, r); }
  }

  /** 403: the API key does not grant the scope the route requires. */
  public static class PermissionDenied extends ControlResellError {
    PermissionDenied(String m, int s, String c, Map<String, String> p, String r) { super(m, s, c, p, r); }
  }

  /** 404: no such resource for this seller. */
  public static class NotFound extends ControlResellError {
    NotFound(String m, int s, String c, Map<String, String> p, String r) { super(m, s, c, p, r); }
  }

  /** 409: an {@code Idempotency-Key} used with another request, or still in flight. */
  public static class Conflict extends ControlResellError {
    Conflict(String m, int s, String c, Map<String, String> p, String r) { super(m, s, c, p, r); }
  }

  /** 422: the request is well formed but refused. */
  public static class UnprocessableEntity extends ControlResellError {
    UnprocessableEntity(String m, int s, String c, Map<String, String> p, String r) { super(m, s, c, p, r); }
  }

  /** 429: the rate limit of the API key is reached. */
  public static class RateLimit extends ControlResellError {
    private final Long retryAfterSeconds;

    RateLimit(String m, int s, String c, Map<String, String> p, String r, Long retryAfterSeconds) {
      super(m, s, c, p, r);
      this.retryAfterSeconds = retryAfterSeconds;
    }

    /** The {@code Retry-After} the API answered, in seconds, or {@code null}. */
    public Long retryAfterSeconds() {
      return retryAfterSeconds;
    }
  }

  /** 5xx: an error of the API; retry later. */
  public static class Server extends ControlResellError {
    Server(String m, int s, String c, Map<String, String> p, String r) { super(m, s, c, p, r); }
  }

  /** The API could not be reached (network, DNS, TLS), after the retries. */
  public static class Connection extends RuntimeException {
    public Connection(Throwable cause) {
      super("Could not reach the ControlResell API: " + cause.getMessage(), cause);
    }
  }
}
