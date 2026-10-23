package com.controlresell.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.controlresell.sdk.generated.model.Item;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The hand-written layer, against a local stub server. */
class ControlResellTest {
  private static final String KEY = "cr_test_secret";
  private static final String ME = "{\"id\":1,\"username\":\"seller\",\"currency\":\"EUR\",\"unknownField\":true}";

  static final class Reply {
    final int status;
    final String body;
    final String[] headers;

    Reply(int status, String body, String... headers) {
      this.status = status;
      this.body = body;
      this.headers = headers;
    }
  }

  private HttpServer server;
  private final List<HttpExchange> exchanges = new CopyOnWriteArrayList<>();
  private final List<String> keys = new CopyOnWriteArrayList<>();
  private final List<String> queries = new CopyOnWriteArrayList<>();
  private final List<String> userAgents = new CopyOnWriteArrayList<>();
  private final List<String> authorizations = new CopyOnWriteArrayList<>();
  private final List<String> bodies = new CopyOnWriteArrayList<>();

  @AfterEach
  void stop() {
    if (server != null) server.stop(0);
  }

  private ControlResell client(Function<Integer, Reply> handler) throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      int n = exchanges.size() + 1;
      exchanges.add(exchange);
      keys.add(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
      queries.add(String.valueOf(exchange.getRequestURI().getQuery()));
      userAgents.add(exchange.getRequestHeaders().getFirst("User-Agent"));
      authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
      bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      Reply reply = handler.apply(n);
      byte[] body = reply.body.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      for (int i = 0; i + 1 < reply.headers.length; i += 2) {
        exchange.getResponseHeaders().add(reply.headers[i], reply.headers[i + 1]);
      }
      exchange.sendResponseHeaders(reply.status, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    server.start();
    return ControlResell.builder()
        .apiKey(KEY)
        .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
        .retry(new RetryPolicy(2, Duration.ofMillis(1), Duration.ofMillis(5)))
        .build();
  }

  private static String envelope(String code) {
    return "{\"error\":{\"code\":\"" + code + "\",\"message\":\"m\",\"params\":{},\"requestId\":\"req-1\"}}";
  }

  private static String item(long id, String sex) {
    return "{\"id\":" + id + ",\"name\":\"n\",\"sku\":null,\"description\":null,\"photos\":[],\"categoryId\":null,"
        + "\"brandId\":null,\"sizeId\":null,\"colorIds\":[],\"state\":null,\"sex\":\"" + sex + "\",\"labels\":[],"
        + "\"sold\":false,\"estimatedPrice\":{\"amount\":\"12.50\",\"currency\":\"EUR\"},\"purchasePrice\":null,"
        + "\"purchaseDate\":null,\"sellingPrice\":null,\"sellingDate\":null,\"createdAt\":\"2026-10-20T10:00:00Z\","
        + "\"updatedAt\":\"2026-10-20T10:00:00Z\",\"unknownField\":1}";
  }

  @Test
  void aCallSendsTheKeyAndTheUserAgent() throws Exception {
    ControlResell sdk = client(n -> new Reply(200, ME));
    assertEquals("seller", sdk.me().get().getUsername());
    assertEquals("Bearer " + KEY, authorizations.get(0));
    assertEquals("controlresell-java/" + ControlResell.VERSION, userAgents.get(0));
  }

  @Test
  void anErrorIsTypedByItsStatusWithItsCodeAndRequestId() throws Exception {
    ControlResell sdk = client(n -> new Reply(404, envelope("item_not_found")));
    ControlResellError.NotFound e = assertThrows(ControlResellError.NotFound.class, () -> sdk.items().get(1));
    assertEquals("item_not_found", e.code());
    assertEquals("req-1", e.requestId());
    assertFalse(e.getMessage().contains(KEY), "the key never appears in an error");
  }

  @Test
  void aRateLimitIsRetriedAfterRetryAfterThenSucceeds() throws Exception {
    ControlResell sdk = client(n -> n == 1
        ? new Reply(429, envelope("rate_limited"), "Retry-After", "0")
        : new Reply(200, "{\"data\":[],\"nextCursor\":null}"));
    sdk.accounts().list(null, null);
    assertEquals(2, exchanges.size());
  }

  @Test
  void aRateLimitAfterTheRetriesIsARateLimitError() throws Exception {
    ControlResell sdk = client(n -> new Reply(429, envelope("rate_limited"), "Retry-After", "0"));
    ControlResellError.RateLimit e =
        assertThrows(ControlResellError.RateLimit.class, () -> sdk.accounts().list(null, null));
    assertEquals(0L, e.retryAfterSeconds());
    assertEquals(3, exchanges.size(), "the first attempt and two retries");
  }

  @Test
  void aPostKeepsTheSameIdempotencyKeyAcrossRetries() throws Exception {
    ControlResell sdk = client(n -> n < 3 ? new Reply(503, envelope("idempotency_unavailable")) : new Reply(200, ME));
    // a write call of a route the SDK does not wrap yet: only the key header is of interest here
    sdk.call(true, headers -> {
      java.net.http.HttpRequest.Builder r = java.net.http.HttpRequest.newBuilder(
          java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/anything"));
      headers.forEach(r::header);
      try {
        java.net.http.HttpResponse<String> response = java.net.http.HttpClient.newHttpClient()
            .send(r.POST(java.net.http.HttpRequest.BodyPublishers.ofString("{}")).build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
          throw new com.controlresell.sdk.generated.ApiException(response.statusCode(), "x", response.headers(), response.body());
        }
        return response.body();
      } catch (IOException | InterruptedException e) {
        throw new com.controlresell.sdk.generated.ApiException(e);
      }
    });
    assertEquals(3, keys.size());
    assertTrue(keys.get(0) != null && keys.stream().allMatch(k -> k.equals(keys.get(0))), keys.toString());
  }

  @Test
  void a500IsNotRetried() throws Exception {
    ControlResell sdk = client(n -> new Reply(500, envelope("internal_error")));
    assertThrows(ControlResellError.Server.class, () -> sdk.me().get());
    assertEquals(1, exchanges.size());
  }

  @Test
  void aGetIsNotGivenAnIdempotencyKey() throws Exception {
    ControlResell sdk = client(n -> new Reply(200, ME));
    sdk.me().get();
    assertNull(keys.get(0));
  }

  @Test
  void listAllReadsEveryPageWithTheSameFilters() throws Exception {
    ControlResell sdk = client(n -> n == 1
        ? new Reply(200, "{\"data\":[" + item(1, "WOMEN") + "],\"nextCursor\":\"c2\"}")
        : new Reply(200, "{\"data\":[" + item(2, "WOMEN") + "],\"nextCursor\":null}"));
    List<Long> ids = new ArrayList<>();
    for (Item it : sdk.items().listAll(null, false, null)) ids.add(it.getId());
    assertEquals(List.of(1L, 2L), ids);
    assertTrue(queries.get(0).contains("sold=false") && !queries.get(0).contains("cursor"), queries.get(0));
    assertTrue(queries.get(1).contains("sold=false") && queries.get(1).contains("cursor=c2"), queries.get(1));
  }

  @Test
  void anAmountKeepsItsDecimalStringAndAnUnknownValueIsTolerated() throws Exception {
    ControlResell sdk = client(n -> new Reply(200, item(9007199254740993L, "A_NEW_VALUE")));
    Item it = sdk.items().get(9007199254740993L);
    assertEquals(9007199254740993L, it.getId());
    assertEquals("12.50", it.getEstimatedPrice().getAmount());
    assertEquals("A_NEW_VALUE", it.getSex());
    assertNull(it.getPurchasePrice());
  }

  @Test
  void onlyTheConflictIdempotencyInProgressIsRetried() throws Exception {
    ControlResell sdk = client(n -> new Reply(409, envelope("idempotency_key_reused")));
    assertThrows(ControlResellError.Conflict.class, () -> sdk.me().get());
    assertEquals(1, exchanges.size(), "another 409 is not retried");
    server.stop(0);
    exchanges.clear();
    ControlResell again = client(n -> n == 1 ? new Reply(409, envelope("idempotency_in_progress")) : new Reply(200, ME));
    again.me().get();
    assertEquals(2, exchanges.size());
  }

  private static final String ACTION =
      "{\"itemId\":7,\"action\":\"PUBLISH\",\"accountIds\":[],\"scheduledAt\":\"2026-10-21T10:00:00Z\"}";
  private static final String ACCOUNT = "3f0c2a52-0b0c-4d65-9f40-2d2d3b1f4a10";

  @Test
  void publishPostsTheAccountsWithOneIdempotencyKey() throws Exception {
    ControlResell sdk = client(n -> n < 2 ? new Reply(503, envelope("idempotency_unavailable")) : new Reply(202, ACTION));
    var request = new com.controlresell.sdk.generated.model.PublishRequest()
        .accountIds(List.of(java.util.UUID.fromString(ACCOUNT)));
    assertEquals(7L, sdk.items().publish(7, request).getItemId());
    assertEquals(2, exchanges.size());
    assertEquals("/api/public/v1/items/7/publish", exchanges.get(1).getRequestURI().getPath());
    assertTrue(bodies.get(1).contains(ACCOUNT), bodies.get(1));
    assertTrue(keys.get(0) != null && keys.get(0).equals(keys.get(1)), keys.toString());
    assertEquals(1, exchanges.get(1).getRequestHeaders().get("Idempotency-Key").size(), "the key is sent once");
  }

  @Test
  void aWriteUsesTheIdempotencyKeyItIsGiven() throws Exception {
    ControlResell sdk = client(n -> new Reply(202, ACTION));
    sdk.items().republish(7, null, "my-key");
    assertEquals("/api/public/v1/items/7/republish", exchanges.get(0).getRequestURI().getPath());
    assertEquals("my-key", keys.get(0));
    assertEquals("{}", bodies.get(0), "a null request is sent as an empty object, not as the JSON null");
  }

  @Test
  void priceDropPostsToThePriceDropRoute() throws Exception {
    ControlResell sdk = client(n -> new Reply(202, ACTION));
    sdk.items().priceDrop(7, new com.controlresell.sdk.generated.model.PriceDropRequest().type("PERCENT").value("10"));
    assertEquals("/api/public/v1/items/7/price-drop", exchanges.get(0).getRequestURI().getPath());
    assertTrue(bodies.get(0).contains("\"PERCENT\""), bodies.get(0));
  }

  @Test
  void deleteResolvesOn204AndThrowsATypedErrorOn409() throws Exception {
    ControlResell sdk = client(n -> n == 1 ? new Reply(204, "") : new Reply(409, envelope("item_still_listed")));
    sdk.items().delete(7);
    assertEquals("DELETE", exchanges.get(0).getRequestMethod());
    ControlResellError e = assertThrows(ControlResellError.class, () -> sdk.items().delete(7));
    assertEquals("item_still_listed", e.code());
  }

  @Test
  void sendMessageAndSendOfferPostToTheConversation() throws Exception {
    String message = "{\"id\":\"" + ACCOUNT + "\",\"conversationId\":\"" + ACCOUNT
        + "\",\"author\":\"SELLER\",\"type\":\"TEXT\",\"status\":\"SENT\",\"body\":\"hi\",\"createdAt\":\"2026-10-21T10:00:00Z\"}";
    ControlResell sdk = client(n -> new Reply(201, message));
    java.util.UUID id = java.util.UUID.fromString(ACCOUNT);
    sdk.conversations().sendMessage(id, new com.controlresell.sdk.generated.model.SendMessageRequest().text("hi"));
    sdk.conversations().sendOffer(id, new com.controlresell.sdk.generated.model.SendOfferRequest()
        .price(new com.controlresell.sdk.generated.model.Money().amount("12.50").currency("EUR")));
    assertEquals("/api/public/v1/conversations/" + ACCOUNT + "/messages", exchanges.get(0).getRequestURI().getPath());
    assertEquals("/api/public/v1/conversations/" + ACCOUNT + "/offers", exchanges.get(1).getRequestURI().getPath());
    assertTrue(bodies.get(1).contains("12.50"), bodies.get(1));
  }

  @Test
  void createPostsTheItemWithAKeyAndUpdatePatchesItWithoutOne() throws Exception {
    ControlResell sdk = client(n -> new Reply(n == 1 ? 201 : 200, item(7, "WOMEN")));
    var created = sdk.items().create(new com.controlresell.sdk.generated.model.ItemCreateRequest().name("n"), "k1");
    var updated = sdk.items().update(7, new com.controlresell.sdk.generated.model.ItemUpdateRequest().name("m"));
    assertEquals(7L, created.getId());
    assertEquals(7L, updated.getId());
    assertEquals("POST", exchanges.get(0).getRequestMethod());
    assertEquals("/api/public/v1/items", exchanges.get(0).getRequestURI().getPath());
    assertEquals("k1", keys.get(0));
    assertEquals("PATCH", exchanges.get(1).getRequestMethod());
    assertEquals("/api/public/v1/items/7", exchanges.get(1).getRequestURI().getPath());
    assertTrue(bodies.get(1).contains("\"m\""), bodies.get(1));
    assertNull(keys.get(1));
  }

  @Test
  void updateSendsAnExplicitNullToClearAFieldAndOmitsAnUnsetOne() throws Exception {
    ControlResell sdk = client(n -> new Reply(200, item(7, "WOMEN")));
    sdk.items().update(7, new com.controlresell.sdk.generated.model.ItemUpdateRequest()
        .packageSizeId(null).sellingPlaceId(12L).materialIds(List.of(3L)));
    String body = bodies.get(0);
    assertTrue(body.contains("\"packageSizeId\":null"), body);
    assertTrue(body.contains("\"sellingPlaceId\":12"), body);
    assertTrue(body.contains("\"materialIds\":[3]"), body);
    assertTrue(!body.contains("measurementLength") && !body.contains("purchasePlaceId"), body);
  }

  private static String order(String platformOrderId) {
    return "{\"id\":\"" + ACCOUNT + "\",\"accountId\":\"" + ACCOUNT + "\",\"type\":\"SALE\",\"status\":\"PAID\","
        + "\"platformOrderId\":\"" + platformOrderId + "\",\"price\":{\"amount\":\"12.50\",\"currency\":\"EUR\"},"
        + "\"prepared\":false,\"date\":\"2026-10-21T10:00:00Z\",\"items\":[]}";
  }

  @Test
  void getAndLabelsReadTheOrderAndItsLabels() throws Exception {
    ControlResell sdk = client(n -> n == 1 ? new Reply(200, order("p1")) : new Reply(200, "{\"data\":[],\"nextCursor\":null}"));
    java.util.UUID id = java.util.UUID.fromString(ACCOUNT);
    assertEquals("p1", sdk.orders().get(id).getPlatformOrderId());
    assertTrue(sdk.orders().labels(id).getData().isEmpty());
    assertEquals("/api/public/v1/orders/" + ACCOUNT, exchanges.get(0).getRequestURI().getPath());
    assertEquals("/api/public/v1/orders/" + ACCOUNT + "/labels", exchanges.get(1).getRequestURI().getPath());
  }

  @Test
  void ordersListAllFollowsTheCursorWithTheSameFilters() throws Exception {
    ControlResell sdk = client(n -> n == 1
        ? new Reply(200, "{\"data\":[" + order("a") + "],\"nextCursor\":\"c2\"}")
        : new Reply(200, "{\"data\":[" + order("b") + "],\"nextCursor\":null}"));
    List<String> ids = new ArrayList<>();
    var from = java.time.OffsetDateTime.parse("2026-10-01T00:00:00Z");
    for (var o : sdk.orders().listAll(1, "to_send", "VINTED", null, from, null)) ids.add(o.getPlatformOrderId());
    assertEquals(List.of("a", "b"), ids);
    for (String q : queries) assertTrue(q.contains("bucket=to_send") && q.contains("platform=VINTED") && q.contains("from="), q);
    assertTrue(!queries.get(0).contains("cursor") && queries.get(1).contains("cursor=c2"), queries.toString());
  }

  @Test
  void ordersListAllLabelsToShipFollowsTheCursorOnOrdersLabels() throws Exception {
    ControlResell sdk = client(n -> n == 1
        ? new Reply(200, "{\"data\":[{\"id\":\"" + ACCOUNT + "\",\"orderId\":\"" + ACCOUNT + "\"}],\"nextCursor\":\"c2\"}")
        : new Reply(200, "{\"data\":[{\"id\":\"" + ACCOUNT + "\",\"orderId\":\"" + ACCOUNT + "\"}],\"nextCursor\":null}"));
    List<String> ids = new ArrayList<>();
    for (var l : sdk.orders().listAllLabelsToShip(1)) ids.add(l.getOrderId().toString());
    assertEquals(List.of(ACCOUNT, ACCOUNT), ids);
    assertEquals("/api/public/v1/orders/labels", exchanges.get(0).getRequestURI().getPath());
    assertTrue(queries.get(1).contains("cursor=c2"), queries.toString());
  }

  @Test
  void conversationMessagesSendTheFromAndToWindow() throws Exception {
    ControlResell sdk = client(n -> new Reply(200, "{\"data\":[],\"nextCursor\":null}"));
    java.util.UUID id = java.util.UUID.fromString(ACCOUNT);
    sdk.conversations().messages(id, null, null, java.time.OffsetDateTime.parse("2026-10-01T00:00:00Z"),
        java.time.OffsetDateTime.parse("2026-10-02T00:00:00Z"));
    assertTrue(queries.get(0).contains("from=2026-10-01") && queries.get(0).contains("to=2026-10-02"), queries.toString());
  }

  private static String customer(String firstName) {
    return "{\"id\":\"" + ACCOUNT + "\",\"firstName\":\"" + firstName + "\",\"lastName\":\"Doe\",\"email\":null,"
        + "\"address\":{\"country\":\"FR\"},\"createdAt\":\"2026-10-21T10:00:00Z\"}";
  }

  @Test
  void customersListAllFollowsTheCursorWithTheSameFilters() throws Exception {
    ControlResell sdk = client(n -> n == 1
        ? new Reply(200, "{\"data\":[" + customer("Ann") + "],\"nextCursor\":\"c2\"}")
        : new Reply(200, "{\"data\":[" + customer("Bob") + "],\"nextCursor\":null}"));
    List<String> names = new ArrayList<>();
    var from = java.time.OffsetDateTime.parse("2026-10-01T00:00:00Z");
    for (var c : sdk.customers().listAll(1, java.util.UUID.fromString(ACCOUNT), from, null)) names.add(c.getFirstName());
    assertEquals(List.of("Ann", "Bob"), names);
    assertEquals("/api/public/v1/customers", exchanges.get(0).getRequestURI().getPath());
    for (String q : queries) assertTrue(q.contains("accountId=" + ACCOUNT) && q.contains("from=2026-10-01"), q);
    assertTrue(!queries.get(0).contains("cursor") && queries.get(1).contains("cursor=c2"), queries.toString());
  }

  @Test
  void customersGetReadsOneCustomer() throws Exception {
    ControlResell sdk = client(n -> new Reply(200, customer("Ann")));
    assertEquals("Ann", sdk.customers().get(java.util.UUID.fromString(ACCOUNT)).getFirstName());
    assertEquals("/api/public/v1/customers/" + ACCOUNT, exchanges.get(0).getRequestURI().getPath());
  }

  @Test
  void catalogsBrandsAllFollowsTheCursorWithTheSameQuery() throws Exception {
    ControlResell sdk = client(n -> n == 1
        ? new Reply(200, "{\"data\":[{\"id\":1,\"name\":\"Nike\",\"custom\":false}],\"nextCursor\":\"c2\"}")
        : new Reply(200, "{\"data\":[{\"id\":2,\"name\":\"Nikon\",\"custom\":true}],\"nextCursor\":null}"));
    List<String> names = new ArrayList<>();
    for (var b : sdk.catalogs().brandsAll(null, "nik")) names.add(b.getName());
    assertEquals(List.of("Nike", "Nikon"), names);
    assertEquals("/api/public/v1/catalogs/brands", exchanges.get(0).getRequestURI().getPath());
    for (String q : queries) assertTrue(q.contains("q=nik"), q);
    assertTrue(!queries.get(0).contains("cursor") && queries.get(1).contains("cursor=c2"), queries.toString());
  }

  @Test
  void catalogsColorsSendTheCategoryAndReadTheValues() throws Exception {
    ControlResell sdk = client(n -> new Reply(200, "{\"data\":[{\"id\":3,\"key\":\"red\",\"title\":\"Red\"}],\"nextCursor\":null}"));
    assertEquals("Red", sdk.catalogs().colors(null, null, 12L).getData().get(0).getTitle());
    assertEquals("/api/public/v1/catalogs/colors", exchanges.get(0).getRequestURI().getPath());
    assertTrue(queries.get(0).contains("categoryId=12"), queries.toString());
  }

  @Test
  void catalogsCategoryFieldsReadsTheFieldsOfOneCategory() throws Exception {
    ControlResell sdk = client(n -> new Reply(200,
        "{\"categoryId\":12,\"sizeGroupIds\":[1],\"enumFields\":[],\"textFields\":[],\"measurements\":[]}"));
    assertEquals(12L, sdk.catalogs().categoryFields(12).getCategoryId());
    assertEquals("/api/public/v1/catalogs/categories/12/fields", exchanges.get(0).getRequestURI().getPath());
  }
}
