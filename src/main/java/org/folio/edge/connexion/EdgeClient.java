package org.folio.edge.connexion;

import io.netty.handler.codec.http.cookie.ClientCookieDecoder;
import io.netty.handler.codec.http.cookie.Cookie;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.XOkapiHeaders;

public class EdgeClient {
  private static final Logger log = LogManager.getLogger(EdgeClient.class);

  private final TokenCache cache;
  private final WebClient client;
  private final String okapiUrl;
  private final String tenant;
  private final String username;
  private final Supplier<Future<String>> getPasswordSupplier;
  private final boolean expiry;

  EdgeClient(String okapiUrl, WebClient client, TokenCache cache, String tenant,
             String username, Supplier<Future<String>> getPasswordSupplier) {
    this.cache = cache;
    this.client = client;
    this.okapiUrl = okapiUrl;
    this.tenant = tenant;
    this.username = username;
    this.getPasswordSupplier = getPasswordSupplier;
    this.expiry = false; // true of using "with-expiry"
  }

  Future<HttpRequest<Buffer>> getToken(HttpRequest<Buffer> request) {
    String cacheValue;
    try {
      cacheValue = cache.get(tenant, username);
    } catch (Exception e) {
      log.warn("Failed to access TokenCache {}", e.getMessage(), e);
      return Future.failedFuture("Failed to access TokenCache");
    }
    if (cacheValue != null) {
      request.putHeader(XOkapiHeaders.TOKEN, cacheValue);
      return Future.succeededFuture(request);
    }
    final String loginPath = expiry ? "/authn/login-with-expiry" : "/authn/login";
    return getPasswordSupplier.get().compose(password -> {
      JsonObject payload = new JsonObject()
          .put("username", username)
          .put("password", password);
      return client.postAbs(okapiUrl + loginPath)
          .expect(ResponsePredicate.SC_CREATED)
          .putHeader("Accept", "*/*") // to be safe
          .putHeader(XOkapiHeaders.TENANT, tenant)
          .sendJsonObject(payload); // also sets Content-Type to application/json
    }).map(res -> {
      if (!expiry) {
        String newToken = res.getHeader(XOkapiHeaders.TOKEN);
        cache.put(tenant, username, newToken, System.currentTimeMillis() + 604800000L); // 1 week
        request.putHeader(XOkapiHeaders.TOKEN, newToken);
        return request;
      }
      res.headers().forEach(n -> {
        if ("Set-Cookie".equals(n.getKey())) {
          Cookie cookie = ClientCookieDecoder.STRICT.decode(n.getValue());
          // TODO use COOKIE_ACCESS_TOKEN from Okapi (not released yet)
          if ("folioAccessToken".equals(cookie.name())) {
            request.putHeader(XOkapiHeaders.TOKEN, cookie.value());
            // 1 minute less than max-age
            long expire = System.currentTimeMillis() + cookie.maxAge() * 1000 - 60000;
            cache.put(tenant, username, cookie.value(), expire);
          }
        }
      });
      return request;
    });
  }
}
