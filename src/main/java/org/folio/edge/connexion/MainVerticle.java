package org.folio.edge.connexion;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetServerOptions;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.edge.core.Constants;
import org.folio.edge.core.EdgeVerticleCore;
import org.folio.edge.core.cache.TokenCache;
import org.folio.edge.core.model.ClientInfo;
import org.folio.edge.core.security.SecureStore;
import org.folio.edge.core.utils.ApiKeyUtils;

public class MainVerticle extends EdgeVerticleCore {

  // ID of : https://github.com/folio-org/mod-copycat/blob/master/src/main/resources/reference-data/profiles/oclc-worldcat.json
  static final String COPYCAT_PROFILE_OCLC = "f26df83c-aa25-40b6-876e-96852c3d4fd4";
  static final String LOGIN_STRATEGY = "login_strategy";
  private static final int MAX_RECORD_SIZE = 100000;
  private static final int DEFAULT_PORT = 8081;
  private static final Logger log = LogManager.getLogger(MainVerticle.class);
  private int maxRecordSize = MAX_RECORD_SIZE;
  int port;

  enum LoginStrategyType {
    full,
    key,
  }

  void setMaxRecordSize(int sz) {
    maxRecordSize = sz;
  }

  int getMaxRecordSize() {
    return maxRecordSize;
  }

  // primarily for testing.. A called that is called when an import is completed
  private Handler<AsyncResult<Void>> completeImportHandler = x -> { };

  void setCompleteHandler(Handler<AsyncResult<Void>> handler) {
    completeImportHandler = handler;
  }

  @Override
  public void start(Promise<Void> promise) {
    LoginStrategyType loginStrategyType = LoginStrategyType.valueOf(
        config().getString(LOGIN_STRATEGY, System.getProperty(LOGIN_STRATEGY, "key")));
    Future.<Void>future(super::start).<Void>compose(res -> {
      // One webClient per Verticle
      Integer timeout = config().getInteger(Constants.SYS_REQUEST_TIMEOUT_MS);
      WebClientOptions webClientOptions = new WebClientOptions()
          .setIdleTimeoutUnit(TimeUnit.MILLISECONDS).setIdleTimeout(timeout)
          .setConnectTimeout(timeout);
      WebClient webClient = WebClient.create(vertx, webClientOptions);
      port = config().getInteger(Constants.SYS_PORT, DEFAULT_PORT);
      NetServerOptions options = new NetServerOptions()
          .setIdleTimeout(30)
          .setIdleTimeoutUnit(TimeUnit.SECONDS);
      // start server.. three cases co consider:
      // 1: buffer overrun (too large incoming request)
      // 2: HTTP GET status for health check
      // 3: OCLC Connexion incoming request
      return vertx.createNetServer(options)
          .connectHandler(socket -> {
            Buffer buffer = Buffer.buffer();
            socket.handler(chunk -> {
              buffer.appendBuffer(chunk);
              if (buffer.length() > maxRecordSize) {
                log.warn("OCLC import size exceeded {}", maxRecordSize);
                socket.endHandler(x -> {});
                socket.close();
                completeImportHandler.handle(Future.failedFuture("OCLC import size exceeded"));
                return;
              }
              // Minimal HTTP to honor health status
              for (int i = 0; i < buffer.length(); i++) {
                // look for LFCRLF or LFLF
                if (buffer.getByte(i) == '\n') {
                  int j = i + 1;
                  if (j < buffer.length() && buffer.getByte(j) == '\r') {
                    j++;
                  }
                  if (j < buffer.length() && buffer.getByte(j) == '\n') {
                    if ("GET ".equals(buffer.getString(0, 4))) {
                      // close now, ignoring keep alive (which is our right!)
                      socket.endHandler(x -> {});
                      log.debug("Got HTTP: {}", buffer.toString());
                      socket.write("HTTP/1.0 200 OK\r\n\r\n")
                          .onComplete(x -> socket.close());
                      return;
                    }
                  }
                }
              }
            });
            socket.endHandler(end -> {
              Importer importer = new Importer();
              importer.importRequest(buffer)
                  .compose(x -> callCopycat(importer, webClient, loginStrategyType))
                  .onComplete(x -> {
                    if (x.failed()) {
                      log.warn(x.cause().getMessage(), x.cause());
                    }
                    completeImportHandler.handle(x);
                  });
            });
          }).listen(port).mapEmpty();
    }).onComplete(promise);
  }

  Future<Void> callCopycat(Importer importer, WebClient webClient,
                           LoginStrategyType loginStrategyType) {
    if (importer.getRecords().size() != 1) {
      return Future.failedFuture("One record expected in OCLC Connexion request");
    }
    Buffer record = importer.getRecords().get(0);
    String okapiUrl = config().getString(Constants.SYS_OKAPI_URL);
    EdgeClient edgeClient = null;
    switch (loginStrategyType) {
      default: // key, but checkstyle insists about a default section!!
        // scenario 1: api key in localUser
        ClientInfo clientInfo;
        try {
          clientInfo = ApiKeyUtils.parseApiKey(importer.getLocalUser());
        } catch (ApiKeyUtils.MalformedApiKeyException e) {
          return Future.failedFuture("access denied");
        }
        edgeClient = new EdgeClient(okapiUrl, webClient, TokenCache.getInstance(),
            clientInfo.tenantId, clientInfo.salt, clientInfo.username, pw -> {
          try {
            pw.complete(secureStore.get(clientInfo.salt, clientInfo.tenantId, clientInfo.username));
          } catch (SecureStore.NotFoundException e) {
            log.error("Exception retrieving password", e);
            pw.fail("Error retrieving password"); // do not reveal anything
          }
        });
        break;
      case full:
        // scenario 2: localUser 'tenant user password'  (whitespace between these)
        String[] s = importer.getLocalUser().split("\\s+");
        if (s.length != 3) {
          return Future.failedFuture("Bad format of localUser");
        }
        edgeClient = new EdgeClient(okapiUrl, webClient, TokenCache.getInstance(),
            s[0], "0", s[1], pw -> pw.complete(s[2]));
        break;
    }
    JsonObject content = new JsonObject();
    content.put("profileId", COPYCAT_PROFILE_OCLC);
    content.put("record",
        new JsonObject().put("marc",
            Base64.getEncoder().encodeToString(record.getBytes())
        ));
    HttpRequest<Buffer> bufferHttpRequest = edgeClient.getClient()
        .postAbs(okapiUrl + "/copycat/imports");
    // Accept is not necessary with mod-copycat because it's based on RMB 32.2+ RMB-519
    // Content-Type is set to application/json by sendJsonObject
    return edgeClient.getToken(bufferHttpRequest)
        .compose(request -> request.sendJsonObject(content))
        .compose(response -> {
          if (response.statusCode() != 200) {
            log.warn("POST /copycat/imports returned status {}: {}",
                response.statusCode(), response.bodyAsString());
            return Future.failedFuture("/copycat/imports returned status " + response.statusCode());
          }
          return Future.succeededFuture();
        });
  }
}
