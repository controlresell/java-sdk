package com.controlresell.sdk;

import com.controlresell.sdk.generated.ApiException;
import com.controlresell.sdk.generated.model.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

/** Reads the API's error envelope into a typed {@link ControlResellError}. */
final class Errors {
  private Errors() {}

  static ControlResellError of(ApiException e, ObjectMapper mapper) {
    int status = e.getCode();
    String code = "http_" + status;
    String message = "HTTP " + status;
    Map<String, String> params = Map.of();
    String requestId = null;
    try {
      ErrorResponse envelope = mapper.readValue(e.getResponseBody(), ErrorResponse.class);
      if (envelope != null && envelope.getError() != null) {
        if (envelope.getError().getCode() != null) code = envelope.getError().getCode();
        if (envelope.getError().getMessage() != null) message = envelope.getError().getMessage();
        if (envelope.getError().getParams() != null) params = envelope.getError().getParams();
        requestId = envelope.getError().getRequestId();
      }
    } catch (Exception ignored) {
      // not the API's envelope (a gateway page, an empty body): keep the status
    }
    switch (status) {
      case 400: return new ControlResellError.BadRequest(message, status, code, params, requestId);
      case 401: return new ControlResellError.Authentication(message, status, code, params, requestId);
      case 402: return new ControlResellError.PaymentRequired(message, status, code, params, requestId);
      case 403: return new ControlResellError.PermissionDenied(message, status, code, params, requestId);
      case 404: return new ControlResellError.NotFound(message, status, code, params, requestId);
      case 409: return new ControlResellError.Conflict(message, status, code, params, requestId);
      case 422: return new ControlResellError.UnprocessableEntity(message, status, code, params, requestId);
      case 429: return new ControlResellError.RateLimit(message, status, code, params, requestId, retryAfter(e));
      default:
        return status >= 500
            ? new ControlResellError.Server(message, status, code, params, requestId)
            : new ControlResellError(message, status, code, params, requestId);
    }
  }

  static Long retryAfter(ApiException e) {
    if (e.getResponseHeaders() == null) return null;
    return e.getResponseHeaders().firstValue("Retry-After").map(v -> {
      try {
        return Long.valueOf(v.trim());
      } catch (NumberFormatException ignored) {
        return null;
      }
    }).orElse(null);
  }
}
