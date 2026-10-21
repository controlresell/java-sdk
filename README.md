# ControlResell Java SDK

Java client for the [ControlResell](https://controlresell.com) public API (Java 11+).

```java
ControlResell client = new ControlResell("cr_live_..."); // or ControlResell.builder().build() with CONTROLRESELL_API_KEY
Me me = client.me().get();
for (Item item : client.items().listAll(null, false, null)) {
  System.out.println(item.getName());
}

// Writes (API key with items:write / conversations:write); the last argument, optional, is the Idempotency-Key.
client.items().publish(42, new PublishRequest().accountIds(List.of(accountId)));
client.conversations().sendMessage(conversationId, new SendMessageRequest().text("Hello!"));
```

Retries (rate limits, 502/503/504, network errors), `Idempotency-Key` on every `POST`, cursor pagination with plain iterables and typed errors (`ControlResellError.NotFound`, `ControlResellError.RateLimit`...) are built in. The client is generated from the API's OpenAPI document (`scripts/generate.sh`, code in `src/main/java/com/controlresell/sdk/generated`).

```sh
mvn test
```

Maven coordinates, once published: `com.controlresell:java-sdk`. Not published yet. Licensed under the Apache License 2.0.
