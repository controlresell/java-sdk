# ControlResell Java SDK

Java client for the [ControlResell](https://controlresell.com) public API (Java 11+).

## Install

From the 0.1.0 release, on Maven Central:

```xml
<dependency>
  <groupId>com.controlresell</groupId>
  <artifactId>java-sdk</artifactId>
  <version>0.1.0</version>
</dependency>
```


```java
ControlResell client = new ControlResell("cr_live_..."); // or ControlResell.builder().build() with CONTROLRESELL_API_KEY
Me me = client.me().get();
for (Item item : client.items().listAll(null, false, null)) {
  System.out.println(item.getName());
}
for (Order order : client.orders().listAll(null, "to_send", null, null, null, null)) {
  System.out.println(order.getId()); // needs the orders:read scope; also orders().get(id), orders().labels(id)
}
for (CatalogBrand brand : client.catalogs().brandsAll(null, "nik")) {
  System.out.println(brand.getId() + " " + brand.getName()); // the identifiers an item is written with (items:read): categories, category, categoryFields, brands, sizes, colors, conditions, packageSizes, materials
}

for (Favorite favorite : client.favorites().listAll(null, null, 42L, null, null)) {
  System.out.println(favorite.getBuyerId()); // the buyers who favorited a listing (items:read); also favorites().listAllItem(42, ...), list, listItem
}
client.conversations().list(null, null, null, null, null, null, "BUYER_ID", null, "PENDING"); // buyerId, itemId and offerStatus filter the conversations

// Writes (API key with items:write / conversations:write); the last argument, optional, is the Idempotency-Key.
client.items().create(new ItemCreateRequest().name("Jacket")); // items().update(42, new ItemUpdateRequest()...) patches
client.items().publish(42, new PublishRequest().accountIds(List.of(accountId)));
client.conversations().sendMessage(conversationId, new SendMessageRequest().text("Hello!"));

// Webhooks (webhooks:read / webhooks:write): endpoints, endpoint, createEndpoint, updateEndpoint, deleteEndpoint, secret,
// rotateSecret, test, deliveries, retryDelivery, recover, eventTypes; eventsAll catches up the last 7 days.
CreatedWebhookEndpoint created = client.webhooks().createEndpoint(
    new WebhookEndpointCreateRequest().url("https://example.com/hooks").eventTypes(List.of("item.sold")));
System.out.println(created.getSecret()); // the signing secret, shown once (webhooks().secret(id) reads it again)
for (WebhookEvent event : client.webhooks().eventsAll(null, null, List.of("item.sold"))) {
  System.out.println(event.getType() + " " + event.getData().get("itemId"));
}
```

On `ItemUpdateRequest`, a nullable field left unset is not sent, and one set to `null` (`.sellingPlaceId(null)`) is sent as an explicit `null`, which clears it on the API. A list is replaced as a whole: send an empty list to empty it.

Retries (rate limits, 502/503/504, network errors), `Idempotency-Key` on every `POST`, cursor pagination with plain iterables and typed errors (`ControlResellError.NotFound`, `ControlResellError.RateLimit`...) are built in. The client is generated from the API's OpenAPI document (`scripts/generate.sh`, code in `src/main/java/com/controlresell/sdk/generated`).

```sh
mvn test
```

Licensed under the Apache License 2.0.
