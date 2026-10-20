package com.controlresell.sdk;

import com.controlresell.sdk.generated.ApiClient;
import com.controlresell.sdk.generated.ApiException;
import com.controlresell.sdk.generated.api.AccountsApi;
import com.controlresell.sdk.generated.api.ConversationsApi;
import com.controlresell.sdk.generated.api.ItemsApi;
import com.controlresell.sdk.generated.api.MeApi;
import com.controlresell.sdk.generated.model.Account;
import com.controlresell.sdk.generated.model.AccountPage;
import com.controlresell.sdk.generated.model.Conversation;
import com.controlresell.sdk.generated.model.ConversationPage;
import com.controlresell.sdk.generated.model.Item;
import com.controlresell.sdk.generated.model.ItemPage;
import com.controlresell.sdk.generated.model.ListingPage;
import com.controlresell.sdk.generated.model.Me;
import com.controlresell.sdk.generated.model.Message;
import com.controlresell.sdk.generated.model.MessagePage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
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

  /** The seller the API key acts for. */
  public MeResource me() { return new MeResource(); }

  /** The seller's marketplace accounts. */
  public Accounts accounts() { return new Accounts(); }

  /** The items of the seller's stock. */
  public Items items() { return new Items(); }

  /** The conversations with buyers. */
  public Conversations conversations() { return new Conversations(); }

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

    /** Every item, page after page, with the same filters. */
    public Iterable<Item> listAll(Integer limit, Boolean sold, UUID accountId) {
      return new Paginator<>(cursor -> list(limit, cursor, sold, accountId), p -> page(p.getData(), p.getNextCursor()));
    }
  }

  /** The conversations with buyers. */
  public final class Conversations {
    private final ConversationsApi api = new ConversationsApi(apiClient);

    private Conversations() {}

    /** One page of conversations; every argument may be {@code null}. */
    public ConversationPage list(Integer limit, String cursor, Boolean unread, UUID accountId) {
      return call(false, h -> api.listConversations(limit, cursor, unread, accountId, h));
    }

    public Conversation get(UUID id) { return call(false, h -> api.getConversation(id, h)); }

    /** One page of the messages of a conversation, oldest first. */
    public MessagePage messages(UUID id, Integer limit, String cursor) {
      return call(false, h -> api.listConversationMessages(id, limit, cursor, h));
    }

    /** Every conversation, page after page, with the same filters. */
    public Iterable<Conversation> listAll(Integer limit, Boolean unread, UUID accountId) {
      return new Paginator<>(cursor -> list(limit, cursor, unread, accountId), p -> page(p.getData(), p.getNextCursor()));
    }

    /** Every message of a conversation, page after page. */
    public Iterable<Message> listAllMessages(UUID id, Integer limit) {
      return new Paginator<>(cursor -> messages(id, limit, cursor), p -> page(p.getData(), p.getNextCursor()));
    }
  }

  private static <T> Paginator.Page<T> page(List<T> data, String nextCursor) {
    return new Paginator.Page<>() {
      @Override public List<T> data() { return data; }

      @Override public String nextCursor() { return nextCursor; }
    };
  }
}
