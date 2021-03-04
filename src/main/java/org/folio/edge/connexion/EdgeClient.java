package org.folio.edge.connexion;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.edge.core.cache.TokenCache;

public class EdgeClient {
  private static final Logger log = LogManager.getLogger(EdgeClient.class);

  private final TokenCache cache;
  private final WebClient client;
  private final String clientId;
  private final String okapiUrl;
  private final String tenant;
  private final String username;
  private final Handler<Promise<String>> getPasswordHandler;

  EdgeClient(String okapiUrl, WebClient client, TokenCache cache, String tenant,
             String clientId, String username, Handler<Promise<String>> getPasswordHandler) {
    this.cache = cache;
    this.client = client;
    this.clientId = clientId;
    this.okapiUrl = okapiUrl;
    this.tenant = tenant;
    this.username = username;
    this.getPasswordHandler = getPasswordHandler;
  }

  WebClient getClient() {
    return client;
  }

  Future<HttpRequest<Buffer>> getToken(HttpRequest<Buffer> request) {
    String token = null;
    try {
      token = cache.get(clientId, tenant, username);
    } catch (TokenCache.NotInitializedException e) {
      log.warn("Failed to access TokenCache", e);
    }
    if (token != null) {
      request.putHeader("X-Okapi-Token", token);
      return Future.succeededFuture(request);
    }
    Promise<String> promise = Promise.promise();
    getPasswordHandler.handle(promise);
    return promise.future().compose(password -> {
      JsonObject payload = new JsonObject();
      payload.put("username", username);
      payload.put("password", password);
      return client.postAbs(okapiUrl + "/authn/login")
          .putHeader("Accept", "*/*")
          .putHeader("Content-Type", "application/json")
          .putHeader("X-Okapi-Tenant", tenant)
          .sendJsonObject(payload)
          .compose(res -> {
            if (res.statusCode() != 201) {
              log.warn("/authn/login returned {}: {}", res.statusCode(), res.bodyAsString());
              return Future.failedFuture("/authn/login returned " + res.statusCode());
            }
            String newToken = res.getHeader("X-Okapi-Token");
            cache.put(clientId, tenant, username, newToken);
            request.putHeader("X-Okapi-Token", newToken);
            return Future.succeededFuture(request);
          });
    });
  }
}
