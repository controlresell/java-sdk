package com.controlresell.sdk;

import com.controlresell.sdk.generated.ApiClient;
import com.controlresell.sdk.generated.ApiException;
import com.controlresell.sdk.generated.api.AccountsApi;
import com.controlresell.sdk.generated.api.CatalogsApi;
import com.controlresell.sdk.generated.api.ConversationsApi;
import com.controlresell.sdk.generated.api.CustomersApi;
import com.controlresell.sdk.generated.api.FavoritesApi;
import com.controlresell.sdk.generated.api.ItemsApi;
import com.controlresell.sdk.generated.api.MeApi;
import com.controlresell.sdk.generated.api.OrdersApi;
import com.controlresell.sdk.generated.api.WebhooksApi;
import com.controlresell.sdk.generated.model.Account;
import com.controlresell.sdk.generated.model.AccountPage;
import com.controlresell.sdk.generated.model.CatalogBrand;
import com.controlresell.sdk.generated.model.CatalogBrandPage;
import com.controlresell.sdk.generated.model.CatalogCategory;
import com.controlresell.sdk.generated.model.CatalogCategoryFields;
import com.controlresell.sdk.generated.model.CatalogCategoryPage;
import com.controlresell.sdk.generated.model.CatalogSize;
import com.controlresell.sdk.generated.model.CatalogSizePage;
import com.controlresell.sdk.generated.model.CatalogValue;
import com.controlresell.sdk.generated.model.CatalogValuePage;
import com.controlresell.sdk.generated.model.CreatedWebhookEndpoint;
import com.controlresell.sdk.generated.model.Conversation;
import com.controlresell.sdk.generated.model.ConversationPage;
import com.controlresell.sdk.generated.model.Customer;
import com.controlresell.sdk.generated.model.CustomerPage;
import com.controlresell.sdk.generated.model.Favorite;
import com.controlresell.sdk.generated.model.FavoritePage;
import com.controlresell.sdk.generated.model.Item;
import com.controlresell.sdk.generated.model.ItemCreateRequest;
import com.controlresell.sdk.generated.model.ItemPage;
import com.controlresell.sdk.generated.model.ItemUpdateRequest;
import com.controlresell.sdk.generated.model.ListingPage;
import com.controlresell.sdk.generated.model.Me;
import com.controlresell.sdk.generated.model.Message;
import com.controlresell.sdk.generated.model.MessagePage;
import com.controlresell.sdk.generated.model.Order;
import com.controlresell.sdk.generated.model.OrderLabel;
import com.controlresell.sdk.generated.model.OrderLabelPage;
import com.controlresell.sdk.generated.model.OrderPage;
import com.controlresell.sdk.generated.model.PriceDropRequest;
import com.controlresell.sdk.generated.model.PublishRequest;
import com.controlresell.sdk.generated.model.RepublishRequest;
import com.controlresell.sdk.generated.model.ScheduledAction;
import com.controlresell.sdk.generated.model.SendMessageRequest;
import com.controlresell.sdk.generated.model.SendOfferRequest;
import com.controlresell.sdk.generated.model.WebhookDelivery;
import com.controlresell.sdk.generated.model.WebhookDeliveryPage;
import com.controlresell.sdk.generated.model.WebhookEndpoint;
import com.controlresell.sdk.generated.model.WebhookEndpointCreateRequest;
import com.controlresell.sdk.generated.model.WebhookEndpointPage;
import com.controlresell.sdk.generated.model.WebhookEndpointUpdateRequest;
import com.controlresell.sdk.generated.model.WebhookEvent;
import com.controlresell.sdk.generated.model.WebhookEventPage;
import com.controlresell.sdk.generated.model.WebhookEventTypeList;
import com.controlresell.sdk.generated.model.WebhookRecoverRequest;
import com.controlresell.sdk.generated.model.WebhookRecovery;
import com.controlresell.sdk.generated.model.WebhookSecret;
import com.controlresell.sdk.generated.model.WebhookSecretRotateRequest;
import com.controlresell.sdk.generated.model.WebhookTestResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A client of the ControlResell public API, for one API key.
 *
 * <pre>{@code
 * ControlResell client = new ControlResell("cr_live_..."); // or ControlResell.builder().build() with CONTROLRESELL_API_KEY
 * Me me = client.me().get();
 * for (Item item : client.items().listAll(null, false, null)) System.out.println(item.getName());
 * }</pre>
 *
 * Calls are retried on a rate limit (after {@code Retry-After}), on a gateway or availability error (502, 503, 504),
 * on a 409 {@code idempotency_in_progress} and on a network error; a {@code POST} always carries the same
 * {@code Idempotency-Key} across its retries. Any other error is thrown as a {@link ControlResellError}. The client
 * holds no global state: create one per API key.
 */
public final class ControlResell {
  /** The version of this SDK. */
  public static final String VERSION = "0.1.0";

  /** The production address of the API. */
  public static final String DEFAULT_BASE_URL = "https://api.controlresell.com";

  /** The environment variable read when no API key is given. */
  public static final String API_KEY_VARIABLE = "CONTROLRESELL_API_KEY";

  /** A call of the generated client; {@code headers} are the ones to add (the idempotency key of a write). */
  @FunctionalInterface
  public interface HttpCall<T> {
    T call(Map<String, String> headers) throws ApiException;
  }

  private final ApiClient apiClient;
  private final Retrier retrier;

  /** A client for {@code apiKey}, on the production API. */
  public ControlResell(String apiKey) {
    this(builder().apiKey(apiKey));
  }

  private ControlResell(Builder b) {
    String key = b.apiKey != null ? b.apiKey : System.getenv(API_KEY_VARIABLE);
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException("No API key: pass apiKey or set " + API_KEY_VARIABLE + ".");
    }
    ObjectMapper mapper = ApiClient.createDefaultObjectMapper();
    HttpClient.Builder http = b.httpClient != null ? b.httpClient : ApiClient.createDefaultHttpClientBuilder();
    this.apiClient = new ApiClient(http, mapper, b.baseUrl);
    this.apiClient.setReadTimeout(b.timeout);
    this.apiClient.setRequestInterceptor(request -> request
        .header("Authorization", "Bearer " + key)
        .header("User-Agent", "controlresell-java/" + VERSION));
    this.retrier = new Retrier(b.retry, mapper);
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Settings of a client. */
  public static final class Builder {
    private String apiKey;
    private String baseUrl = DEFAULT_BASE_URL;
    private RetryPolicy retry = RetryPolicy.defaults();
    private Duration timeout = Duration.ofSeconds(60);
    private HttpClient.Builder httpClient;

    /** The API key; absent, the {@code CONTROLRESELL_API_KEY} environment variable. */
    public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }

    /** The API's address (staging, a test environment). */
    public Builder baseUrl(String baseUrl) { this.baseUrl = baseUrl; return this; }

    /** How calls are retried. */
    public Builder retry(RetryPolicy retry) { this.retry = retry; return this; }

    /** The read timeout of a call. */
    public Builder timeout(Duration timeout) { this.timeout = timeout; return this; }

    /** The {@link HttpClient} builder to use (proxy, executor, TLS...). */
    public Builder httpClient(HttpClient.Builder httpClient) { this.httpClient = httpClient; return this; }

    public ControlResell build() { return new ControlResell(this); }
  }

  /**
   * Runs a call of the generated client with the retries, for a route this SDK does not wrap yet. A {@code write}
   * call gets an {@code Idempotency-Key} in {@code headers}, the same at each attempt.
   */
  public <T> T call(boolean write, HttpCall<T> call) {
    return retrier.execute(write, call);
  }

  /** Like {@link #call} for a write the API does not deduplicate: see {@link Retrier#execute(boolean, boolean, HttpCall)}. */
  private <T> T callOnce(HttpCall<T> call) {
    return retrier.execute(true, false, call);
  }

  /** The seller the API key acts for. */
  public MeResource me() { return new MeResource(); }

  /** The seller's marketplace accounts. */
  public Accounts accounts() { return new Accounts(); }

  /** The items of the seller's stock. */
  public Items items() { return new Items(); }

  /** The conversations with buyers. */
  public Conversations conversations() { return new Conversations(); }

  /** The seller's sales and purchases. */
  public Orders orders() { return new Orders(); }

  /** The other parties of the seller's sales and purchases (needs the {@code customers:read} scope). */
  public Customers customers() { return new Customers(); }

  /** The buyers who put the seller's listings in their favorites (needs the {@code items:read} scope). */
  public Favorites favorites() { return new Favorites(); }

  /** The catalog an item is written with: categories, brands, sizes, colors... (needs the {@code items:read} scope). */
  public Catalogs catalogs() { return new Catalogs(); }

  /** The seller's webhook endpoints, their secrets and deliveries, and the recent events (webhooks:read, webhooks:write). */
  public Webhooks webhooks() { return new Webhooks(); }

  /** The seller the API key acts for. */
  public final class MeResource {
    private final MeApi api = new MeApi(apiClient);

    private MeResource() {}

    public Me get() { return call(false, h -> api.getMe(h)); }
  }

  /** The seller's marketplace accounts. */
  public final class Accounts {
    private final AccountsApi api = new AccountsApi(apiClient);

    private Accounts() {}

    /** One page of accounts; {@code limit} and {@code cursor} may be {@code null}. */
    public AccountPage list(Integer limit, String cursor) { return call(false, h -> api.listAccounts(limit, cursor, h)); }

    public Account get(UUID id) { return call(false, h -> api.getAccount(id, h)); }

    /** Every account, page after page. */
    public Iterable<Account> listAll(Integer limit) {
      return new Paginator<>(cursor -> list(limit, cursor), p -> page(p.getData(), p.getNextCursor()));
    }
  }

  /** The items of the seller's stock. */
  public final class Items {
    private final ItemsApi api = new ItemsApi(apiClient);

    private Items() {}

    /** One page of items; every argument may be {@code null}. */
    public ItemPage list(Integer limit, String cursor, Boolean sold, UUID accountId) {
      return call(false, h -> api.listItems(limit, cursor, sold, accountId, h));
    }

    public Item get(long id) { return call(false, h -> api.getItem(id, h)); }

    /** The item's listings on the seller's marketplace accounts. */
    public ListingPage listings(long id) { return call(false, h -> api.listItemListings(id, h)); }

    /** Creates an item in the stock, without publishing it, with a random {@code Idempotency-Key}. */
    public Item create(ItemCreateRequest request) { return create(request, null); }

    /** Creates an item in the stock; {@code idempotencyKey} may be {@code null}. */
    public Item create(ItemCreateRequest request, String idempotencyKey) {
      String key = key(idempotencyKey);
      return call(false, h -> api.createItem(key, request));
    }

    /** Updates the given fields of an item (a {@code PATCH}: the fields left out are kept). */
    public Item update(long id, ItemUpdateRequest request) { return call(false, h -> api.updateItem(id, request, h)); }

    /** Deletes an item of the stock; an item still online is refused (409 {@code item_still_listed}). */
    public void delete(long id) {
      call(false, h -> {
        api.deleteItem(id, h);
        return null;
      });
    }

    /** Schedules the publication of an item on the given accounts, with a random {@code Idempotency-Key}. */
    public ScheduledAction publish(long id, PublishRequest request) { return publish(id, request, null); }

    /** Schedules the publication of an item on the given accounts; {@code idempotencyKey} may be {@code null}. */
    public ScheduledAction publish(long id, PublishRequest request, String idempotencyKey) {
      String key = key(idempotencyKey);
      return call(false, h -> api.publishItem(id, key, request));
    }

    /** Schedules a price drop of the item's listings, with a random {@code Idempotency-Key}. */
    public ScheduledAction priceDrop(long id, PriceDropRequest request) { return priceDrop(id, request, null); }

    /** Schedules a price drop of the item's listings; {@code idempotencyKey} may be {@code null}. */
    public ScheduledAction priceDrop(long id, PriceDropRequest request, String idempotencyKey) {
      String key = key(idempotencyKey);
      return call(false, h -> api.dropItemPrice(id, key, request));
    }

    /** Schedules the republication of an item, with a random {@code Idempotency-Key}; {@code request} may be {@code null} (every account the item is listed on). */
    public ScheduledAction republish(long id, RepublishRequest request) { return republish(id, request, null); }

    /** Schedules the republication of an item; {@code request} and {@code idempotencyKey} may be {@code null}. */
    public ScheduledAction republish(long id, RepublishRequest request, String idempotencyKey) {
      String key = key(idempotencyKey);
      RepublishRequest body = request != null ? request : new RepublishRequest();
      return call(false, h -> api.republishItem(id, key, body));
    }

    /** Every item, page after page, with the same filters. */
    public Iterable<Item> listAll(Integer limit, Boolean sold, UUID accountId) {
      return new Paginator<>(cursor -> list(limit, cursor, sold, accountId), p -> page(p.getData(), p.getNextCursor()));
    }
  }

  /** The conversations with buyers. */
  public final class Conversations {
    private final ConversationsApi api = new ConversationsApi(apiClient);

    private Conversations() {}

    /**
     * One page of conversations; every argument may be {@code null}. {@code from} (inclusive) and {@code to}
     * (exclusive) bound the last message.
     */
    public ConversationPage list(Integer limit, String cursor, Boolean unread, UUID accountId, OffsetDateTime from,
        OffsetDateTime to) {
      return list(limit, cursor, unread, accountId, from, to, null, null, null);
    }

    /**
     * One page of conversations, filtered further: {@code buyerId} keeps the conversations with one buyer, {@code itemId}
     * those about one item, {@code offerStatus} those with an offer at this status ({@code PENDING}, {@code ACCEPTED},
     * {@code REJECTED} or {@code CANCELED}). Every argument may be {@code null}.
     */
    public ConversationPage list(Integer limit, String cursor, Boolean unread, UUID accountId, OffsetDateTime from,
        OffsetDateTime to, String buyerId, Long itemId, String offerStatus) {
      return call(false, h -> api.listConversations(limit, cursor, unread, accountId, from, to, buyerId, itemId,
          offerStatus, h));
    }

    public Conversation get(UUID id) { return call(false, h -> api.getConversation(id, h)); }

    /**
     * One page of the messages of a conversation, oldest first; {@code from} (inclusive) and {@code to} (exclusive)
     * bound the period and may be {@code null}.
     */
    public MessagePage messages(UUID id, Integer limit, String cursor, OffsetDateTime from, OffsetDateTime to) {
      return call(false, h -> api.listConversationMessages(id, limit, cursor, from, to, h));
    }

    /** Sends a message to the buyer, with a random {@code Idempotency-Key}. */
    public Message sendMessage(UUID id, SendMessageRequest request) { return sendMessage(id, request, null); }

    /** Sends a message to the buyer; {@code idempotencyKey} may be {@code null}. */
    public Message sendMessage(UUID id, SendMessageRequest request, String idempotencyKey) {
      String key = key(idempotencyKey);
      return call(false, h -> api.sendMessage(id, key, request));
    }

    /** Sends the buyer an offer, with a random {@code Idempotency-Key}. */
    public Message sendOffer(UUID id, SendOfferRequest request) { return sendOffer(id, request, null); }

    /** Sends the buyer an offer; {@code idempotencyKey} may be {@code null}. */
    public Message sendOffer(UUID id, SendOfferRequest request, String idempotencyKey) {
      String key = key(idempotencyKey);
      return call(false, h -> api.sendOffer(id, key, request));
    }

    /** Every conversation, page after page, with the same filters. */
    public Iterable<Conversation> listAll(Integer limit, Boolean unread, UUID accountId, OffsetDateTime from,
        OffsetDateTime to) {
      return listAll(limit, unread, accountId, from, to, null, null, null);
    }

    /** Every conversation, page after page, with the same filters, among them the buyer, item and offer status. */
    public Iterable<Conversation> listAll(Integer limit, Boolean unread, UUID accountId, OffsetDateTime from,
        OffsetDateTime to, String buyerId, Long itemId, String offerStatus) {
      return new Paginator<>(cursor -> list(limit, cursor, unread, accountId, from, to, buyerId, itemId, offerStatus),
          p -> page(p.getData(), p.getNextCursor()));
    }

    /** Every message of a conversation, page after page, with the same period. */
    public Iterable<Message> listAllMessages(UUID id, Integer limit, OffsetDateTime from, OffsetDateTime to) {
      return new Paginator<>(cursor -> messages(id, limit, cursor, from, to), p -> page(p.getData(), p.getNextCursor()));
    }
  }

  /** The seller's sales and purchases. */
  public final class Orders {
    private final OrdersApi api = new OrdersApi(apiClient);

    private Orders() {}

    /**
     * One page of orders, the most recent first; every argument may be {@code null}. {@code bucket} is
     * {@code to_send} or {@code to_pickup}; {@code from} is inclusive and {@code to} exclusive.
     */
    public OrderPage list(Integer limit, String cursor, String bucket, String platform, UUID accountId,
        OffsetDateTime from, OffsetDateTime to) {
      return call(false, h -> api.listOrders(limit, cursor, bucket, platform, accountId, from, to, h));
    }

    public Order get(UUID id) { return call(false, h -> api.getOrder(id, h)); }

    /** The shipping labels of an order. */
    public OrderLabelPage labels(UUID id) { return call(false, h -> api.listOrderLabels(id, h)); }

    /**
     * One page of the shipping labels still to ship over all the orders, the oldest order first; each label names its
     * order ({@code getOrderId()}). Both arguments may be {@code null}.
     */
    public OrderLabelPage labelsToShip(Integer limit, String cursor) {
      return call(false, h -> api.listLabelsToShip(limit, cursor, h));
    }

    /** Every label still to ship, page after page. */
    public Iterable<OrderLabel> listAllLabelsToShip(Integer limit) {
      return new Paginator<>(cursor -> labelsToShip(limit, cursor), p -> page(p.getData(), p.getNextCursor()));
    }

    /** Every order, page after page, with the same filters. */
    public Iterable<Order> listAll(Integer limit, String bucket, String platform, UUID accountId, OffsetDateTime from,
        OffsetDateTime to) {
      return new Paginator<>(cursor -> list(limit, cursor, bucket, platform, accountId, from, to),
          p -> page(p.getData(), p.getNextCursor()));
    }
  }

  /** The other parties of the seller's sales and purchases. */
  public final class Customers {
    private final CustomersApi api = new CustomersApi(apiClient);

    private Customers() {}

    /**
     * One page of customers, the most recently created first; every argument may be {@code null}. {@code from} is
     * inclusive and {@code to} exclusive, on the creation date.
     */
    public CustomerPage list(Integer limit, String cursor, UUID accountId, OffsetDateTime from, OffsetDateTime to) {
      return call(false, h -> api.listCustomers(limit, cursor, from, to, accountId, h));
    }

    public Customer get(UUID id) { return call(false, h -> api.getCustomer(id, h)); }

    /** Every customer, page after page, with the same filters. */
    public Iterable<Customer> listAll(Integer limit, UUID accountId, OffsetDateTime from, OffsetDateTime to) {
      return new Paginator<>(cursor -> list(limit, cursor, accountId, from, to),
          p -> page(p.getData(), p.getNextCursor()));
    }
  }

  /** The buyers who put the seller's listings in their favorites. */
  public final class Favorites {
    private final FavoritesApi api = new FavoritesApi(apiClient);

    private Favorites() {}

    /**
     * One page of favorites, the most recent first; every argument may be {@code null}. {@code from} is inclusive and
     * {@code to} exclusive, on the date of the favorite; {@code accountId} and {@code itemId} keep the favorites of one
     * marketplace account or one item.
     */
    public FavoritePage list(Integer limit, String cursor, UUID accountId, Long itemId, OffsetDateTime from,
        OffsetDateTime to) {
      return call(false, h -> api.listFavorites(limit, cursor, from, to, accountId, itemId, h));
    }

    /** One page of the favorites of an item, on every account it is listed on, the most recent first. */
    public FavoritePage listItem(long id, Integer limit, String cursor, OffsetDateTime from, OffsetDateTime to) {
      return call(false, h -> api.listItemFavorites(id, limit, cursor, from, to, h));
    }

    /** Every favorite, page after page, with the same filters. */
    public Iterable<Favorite> listAll(Integer limit, UUID accountId, Long itemId, OffsetDateTime from,
        OffsetDateTime to) {
      return new Paginator<>(cursor -> list(limit, cursor, accountId, itemId, from, to),
          p -> page(p.getData(), p.getNextCursor()));
    }

    /** Every favorite of an item, page after page, with the same period. */
    public Iterable<Favorite> listAllItem(long id, Integer limit, OffsetDateTime from, OffsetDateTime to) {
      return new Paginator<>(cursor -> listItem(id, limit, cursor, from, to), p -> page(p.getData(), p.getNextCursor()));
    }
  }

  /** The catalog an item is written with. Every list argument may be {@code null}; {@code categoryId} keeps what applies to one category. */
  public final class Catalogs {
    private final CatalogsApi api = new CatalogsApi(apiClient);

    private Catalogs() {}

    /** One page of categories, flat, by identifier; {@code parentId} keeps the children of one, {@code platform} one marketplace. */
    public CatalogCategoryPage categories(Integer limit, String cursor, Long parentId, String platform) {
      return call(false, h -> api.listCatalogCategories(limit, cursor, parentId, platform, h));
    }

    /** Every category, page after page, with the same filters. */
    public Iterable<CatalogCategory> categoriesAll(Integer limit, Long parentId, String platform) {
      return new Paginator<>(cursor -> categories(limit, cursor, parentId, platform),
          p -> page(p.getData(), p.getNextCursor()));
    }

    public CatalogCategory category(long id) { return call(false, h -> api.getCatalogCategory(id, h)); }

    /** What an item of a category is filled with: its size groups, enumerated and text fields, measurements. */
    public CatalogCategoryFields categoryFields(long id) { return call(false, h -> api.getCatalogCategoryFields(id, h)); }

    /** One page of brands, by identifier or, with {@code q}, those whose name starts with it. */
    public CatalogBrandPage brands(Integer limit, String cursor, String q) {
      return call(false, h -> api.listCatalogBrands(limit, cursor, q, h));
    }

    /** Every brand, page after page, with the same filters. */
    public Iterable<CatalogBrand> brandsAll(Integer limit, String q) {
      return new Paginator<>(cursor -> brands(limit, cursor, q), p -> page(p.getData(), p.getNextCursor()));
    }

    /** One page of sizes, each with its size group. */
    public CatalogSizePage sizes(Integer limit, String cursor, Long categoryId) {
      return call(false, h -> api.listCatalogSizes(limit, cursor, categoryId, h));
    }

    /** Every size, page after page, with the same filters. */
    public Iterable<CatalogSize> sizesAll(Integer limit, Long categoryId) {
      return new Paginator<>(cursor -> sizes(limit, cursor, categoryId), p -> page(p.getData(), p.getNextCursor()));
    }

    /** One page of colors. */
    public CatalogValuePage colors(Integer limit, String cursor, Long categoryId) {
      return call(false, h -> api.listCatalogColors(limit, cursor, categoryId, h));
    }

    /** Every one of the colors, page after page, with the same filters. */
    public Iterable<CatalogValue> colorsAll(Integer limit, Long categoryId) {
      return new Paginator<>(cursor -> colors(limit, cursor, categoryId), p -> page(p.getData(), p.getNextCursor()));
    }

    /** One page of conditions. */
    public CatalogValuePage conditions(Integer limit, String cursor, Long categoryId) {
      return call(false, h -> api.listCatalogConditions(limit, cursor, categoryId, h));
    }

    /** Every one of the conditions, page after page, with the same filters. */
    public Iterable<CatalogValue> conditionsAll(Integer limit, Long categoryId) {
      return new Paginator<>(cursor -> conditions(limit, cursor, categoryId), p -> page(p.getData(), p.getNextCursor()));
    }

    /** One page of package sizes. */
    public CatalogValuePage packageSizes(Integer limit, String cursor, Long categoryId) {
      return call(false, h -> api.listCatalogPackageSizes(limit, cursor, categoryId, h));
    }

    /** Every one of the package sizes, page after page, with the same filters. */
    public Iterable<CatalogValue> packageSizesAll(Integer limit, Long categoryId) {
      return new Paginator<>(cursor -> packageSizes(limit, cursor, categoryId), p -> page(p.getData(), p.getNextCursor()));
    }

    /** One page of materials. */
    public CatalogValuePage materials(Integer limit, String cursor, Long categoryId) {
      return call(false, h -> api.listCatalogMaterials(limit, cursor, categoryId, h));
    }

    /** Every one of the materials, page after page, with the same filters. */
    public Iterable<CatalogValue> materialsAll(Integer limit, Long categoryId) {
      return new Paginator<>(cursor -> materials(limit, cursor, categoryId), p -> page(p.getData(), p.getNextCursor()));
    }
  }

  /** The seller's webhook endpoints, their secrets and deliveries, and the events of the last 7 days. */
  public final class Webhooks {
    private final WebhooksApi api = new WebhooksApi(apiClient);

    private Webhooks() {}

    /** The seller's endpoints, without their secrets (at most 5: a single page). */
    public WebhookEndpointPage endpoints() { return call(false, h -> api.listWebhookEndpoints(h)); }

    /** One endpoint by its identifier. */
    public WebhookEndpoint endpoint(UUID id) { return call(false, h -> api.getWebhookEndpoint(id, h)); }

    /** Creates an endpoint with a random {@code Idempotency-Key}; the answer carries its signing secret. */
    public CreatedWebhookEndpoint createEndpoint(WebhookEndpointCreateRequest request) { return createEndpoint(request, null); }

    /** Creates an endpoint; {@code idempotencyKey} may be {@code null}. The answer carries its signing secret. */
    public CreatedWebhookEndpoint createEndpoint(WebhookEndpointCreateRequest request, String idempotencyKey) {
      String key = key(idempotencyKey);
      return call(false, h -> api.createWebhookEndpoint(key, request));
    }

    /**
     * Updates the given fields of an endpoint (a JSON merge patch: the fields left out are kept;
     * {@code setDescription(null)} clears the description). Status {@code enabled} re-enables a disabled endpoint.
     */
    public WebhookEndpoint updateEndpoint(UUID id, WebhookEndpointUpdateRequest request) {
      return call(false, h -> api.updateWebhookEndpoint(id, request, h));
    }

    /** Deletes an endpoint; its pending deliveries are dropped. */
    public void deleteEndpoint(UUID id) {
      call(false, h -> {
        api.deleteWebhookEndpoint(id, h);
        return null;
      });
    }

    /** The current signing secret of an endpoint. */
    public WebhookSecret secret(UUID id) { return call(false, h -> api.getWebhookSecret(id, h)); }

    /**
     * A new signing secret, with a random {@code Idempotency-Key}; the previous one stays valid
     * {@code expiresInHours} ({@code null}: 24, {@code 0}: stopped at once).
     */
    public WebhookSecret rotateSecret(UUID id, Long expiresInHours) { return rotateSecret(id, expiresInHours, null); }

    /** A new signing secret; {@code expiresInHours} and {@code idempotencyKey} may be {@code null}. */
    public WebhookSecret rotateSecret(UUID id, Long expiresInHours, String idempotencyKey) {
      String key = key(idempotencyKey);
      WebhookSecretRotateRequest body = new WebhookSecretRotateRequest();
      if (expiresInHours != null) body.setExpiresInHours(expiresInHours);
      return call(false, h -> api.rotateWebhookSecret(id, key, body));
    }

    /**
     * Sends a {@code webhook.test} event to the endpoint now and returns how it answered. The API ignores the
     * {@code Idempotency-Key} of a test, so it is never replayed after a 5xx or a lost connection (only on a 429 or a
     * failed connect): a replay would send a second event.
     */
    public WebhookTestResult test(UUID id) { return callOnce(h -> api.testWebhookEndpoint(id, h)); }

    /** One page of the deliveries of an endpoint over the last 7 days, the most recent first; filters may be {@code null}. */
    public WebhookDeliveryPage deliveries(UUID id, Integer limit, String cursor, String status, String type) {
      return call(false, h -> api.listWebhookDeliveries(id, status, type, limit, cursor, h));
    }

    /** Every delivery of an endpoint, page after page, with the same filters. */
    public Iterable<WebhookDelivery> deliveriesAll(UUID id, Integer limit, String status, String type) {
      return new Paginator<>(cursor -> deliveries(id, limit, cursor, status, type), p -> page(p.getData(), p.getNextCursor()));
    }

    /** Sends one event to the endpoint again, with the same {@code webhook-id} and a random {@code Idempotency-Key}. */
    public WebhookDelivery retryDelivery(UUID id, UUID deliveryId) { return retryDelivery(id, deliveryId, null); }

    /** Sends one event to the endpoint again; {@code idempotencyKey} may be {@code null}. */
    public WebhookDelivery retryDelivery(UUID id, UUID deliveryId, String idempotencyKey) {
      String key = key(idempotencyKey);
      return call(false, h -> api.retryWebhookDelivery(id, deliveryId, key));
    }

    /** Sends again the failed deliveries since {@code since} (7 days at most, 1,000 per call: call again while {@code hasMore}). */
    public WebhookRecovery recover(UUID id, OffsetDateTime since) { return recover(id, since, null); }

    /** Sends again the failed deliveries since {@code since}; {@code idempotencyKey} may be {@code null}. */
    public WebhookRecovery recover(UUID id, OffsetDateTime since, String idempotencyKey) {
      String key = key(idempotencyKey);
      WebhookRecoverRequest body = new WebhookRecoverRequest().since(since);
      return call(false, h -> api.recoverWebhookDeliveries(id, key, body));
    }

    /**
     * One page of the events of the last 7 days, the oldest first: what a client that was offline catches up.
     * {@code types} (may be {@code null}) are joined by commas.
     */
    public WebhookEventPage events(Integer limit, String cursor, OffsetDateTime since, List<String> types) {
      String type = types == null || types.isEmpty() ? null : String.join(",", types);
      return call(false, h -> api.listWebhookEvents(since, type, limit, cursor, h));
    }

    /** Every event, page after page, with the same filters. */
    public Iterable<WebhookEvent> eventsAll(Integer limit, OffsetDateTime since, List<String> types) {
      return new Paginator<>(cursor -> events(limit, cursor, since, types), p -> page(p.getData(), p.getNextCursor()));
    }

    /** The catalog of event types and the scope each one needs. */
    public WebhookEventTypeList eventTypes() { return call(false, h -> api.listWebhookEventTypes(h)); }
  }

  /**
   * The {@code Idempotency-Key} of a write: the caller's, else a random one. It is chosen once, outside the retries, so
   * every attempt of the call sends the same key (the typed methods take the key as an argument, not through the retrier).
   */
  private static String key(String idempotencyKey) {
    return idempotencyKey != null ? idempotencyKey : UUID.randomUUID().toString();
  }

  private static <T> Paginator.Page<T> page(List<T> data, String nextCursor) {
    return new Paginator.Page<>() {
      @Override public List<T> data() { return data; }

      @Override public String nextCursor() { return nextCursor; }
    };
  }
}
