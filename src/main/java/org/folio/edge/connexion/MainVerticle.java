package org.folio.edge.connexion;

import static org.folio.edge.core.Constants.FOLIO_CLIENT_TLS_ENABLED;
import static org.folio.edge.core.Constants.FOLIO_CLIENT_TLS_TRUSTSTOREPASSWORD;
import static org.folio.edge.core.Constants.FOLIO_CLIENT_TLS_TRUSTSTOREPATH;
import static org.folio.edge.core.Constants.FOLIO_CLIENT_TLS_TRUSTSTORETYPE;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.KeyStoreOptions;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.TrustOptions;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.edge.core.Constants;
import org.folio.edge.core.EdgeVerticleCore;
import org.folio.edge.core.model.ClientInfo;
import org.folio.edge.core.security.SecureStore;
import org.folio.edge.core.utils.ApiKeyUtils;
import org.folio.edge.core.utils.SslConfigurationUtil;
import org.folio.okapi.common.ChattyHttpResponseExpectation;
import org.folio.okapi.common.refreshtoken.client.Client;
import org.folio.okapi.common.refreshtoken.client.ClientOptions;
import org.folio.okapi.common.refreshtoken.tokencache.TenantUserCache;

public class MainVerticle extends EdgeVerticleCore {

  // ID of : https://github.com/folio-org/mod-copycat/blob/master/src/main/resources/reference-data/profiles/oclc-worldcat.json
  static final String COPYCAT_PROFILE_OCLC = "f26df83c-aa25-40b6-876e-96852c3d4fd4";
  static final String LOGIN_STRATEGY = "login_strategy";
  private static final int MAX_RECORD_SIZE = 100000;
  private static final int DEFAULT_PORT = 8081;
  private static final Logger log = LogManager.getLogger(MainVerticle.class);
  private final TenantUserCache tenantUserCache = new TenantUserCache(100);
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

  private Future<Void> handleRequest(ConnexionRequest connexionRequest, NetSocket socket,
                                     WebClient webClient,
                                     LoginStrategyType loginStrategyType) {
    log.info("Import from {}", socket.remoteAddress().hostAddress());
    return callCopycat(connexionRequest, webClient, loginStrategyType)
        .compose(
            x -> {
              socket.write("Import ok\n\0");
              log.info("Import ok");
              return Future.succeededFuture();
            },
            cause -> {
              socket.write("Error: " + cause.getMessage() + "\n\0");
              log.error("{}", cause.getMessage());
              return Future.failedFuture(cause);
            }
        );
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
      configureTrustOptions(webClientOptions);

      WebClient webClient = WebClient.create(vertx, webClientOptions);
      port = config().getInteger(Constants.SYS_PORT, DEFAULT_PORT);

      NetServerOptions options = new NetServerOptions()
          .setIdleTimeout(30)
          .setIdleTimeoutUnit(TimeUnit.SECONDS);

      // initialize tls/ssl configuration for web server
      SslConfigurationUtil.configureSslServerOptionsIfEnabled(config(), options);

      // start server. three cases co consider:
      // 1: buffer overrun (too large incoming request)
      // 2: HTTP GET status for health check
      // 3: OCLC Connexion incoming request
      return vertx.createNetServer(options)
          .connectHandler(socket -> {
            ConnexionRequest connexionRequest = new ConnexionRequest();
            Promise<Void> connexionPromise = Promise.promise();
            log.debug("Request from {}", socket.remoteAddress().hostAddress());
            socket.handler(chunk -> {
              // OCLC Connexion request is ended by either the connection being
              // closed or if nul-byte is met
              int i;
              for (i = 0; i < chunk.length(); i++) {
                if (chunk.getByte(i) == (byte) 0) {
                  break;
                }
              }
              connexionRequest.handle(chunk.slice(0, i));

              if (!connexionRequest.getRecords().isEmpty()) {
                handleRequest(connexionRequest, socket, webClient, loginStrategyType)
                    .onComplete(connexionPromise);
              }

              Buffer buffer = connexionRequest.getBuffer();
              if (buffer.length() > maxRecordSize) {
                log.warn("OCLC import size exceeded {}", maxRecordSize);
                socket.endHandler(x -> {});
                socket.close();
                completeImportHandler.handle(Future.failedFuture("OCLC import size exceeded"));
                return;
              }
              // Minimal HTTP to honor health status
              for (i = 0; i < buffer.length(); i++) {
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
              Future<Void> f = connexionPromise.future();
              if (!f.isComplete()) {
                connexionPromise.fail("No records provided");
              }
              completeImportHandler.handle(f);
            });
          }).listen(port).mapEmpty();
    }).onComplete(promise);
  }

  static void parseLocalUserFull(String localUser, StringBuilder tenant, StringBuilder user,
                                 StringBuilder password) {
    int i = 0;
    while (i < localUser.length() && !Character.isWhitespace(localUser.charAt(i))) {
      i++;
    }
    tenant.append(localUser, 0, i);
    while (i < localUser.length() && Character.isWhitespace(localUser.charAt(i))) {
      i++;
    }
    int j = i;
    while (i < localUser.length() && !Character.isWhitespace(localUser.charAt(i))) {
      i++;
    }
    user.append(localUser, j, i);
    if (i < localUser.length()) {
      i++; // only one blank between user and password
    }
    password.append(localUser, i, localUser.length());
  }

  Future<Void> callCopycat(ConnexionRequest connexionRequest, WebClient webClient,
                           LoginStrategyType loginStrategyType) {
    if (connexionRequest.getRecords().size() != 1) {
      return Future.failedFuture("One record expected in OCLC Connexion request");
    }
    final Buffer record = connexionRequest.getRecords().get(0);
    connexionRequest.getRecords().clear();
    String okapiUrl = config().getString(Constants.SYS_OKAPI_URL);
    ClientOptions clientOptions = new ClientOptions().webClient(webClient).okapiUrl(okapiUrl);
    org.folio.okapi.common.refreshtoken.client.Client client;

    switch (loginStrategyType) {
      default: // key, but checkstyle insists about a default section!!
        // scenario 1: api key in localUser
        ClientInfo clientInfo;
        try {
          clientInfo = ApiKeyUtils.parseApiKey(connexionRequest.getLocalUser().strip());
        } catch (ApiKeyUtils.MalformedApiKeyException e) {
          return Future.failedFuture("access denied");
        }
        log.info("Login strategy {} and using tenant {}", loginStrategyType, clientInfo.tenantId);
        client = Client.createLoginClient(clientOptions, tenantUserCache,
            clientInfo.tenantId, clientInfo.username, () -> {
              try {
                return Future.succeededFuture(
                    secureStore.get(clientInfo.salt, clientInfo.tenantId, clientInfo.username));
              } catch (SecureStore.NotFoundException e) {
                log.error("Exception retrieving password", e);
                return Future.failedFuture("Error retrieving password"); // do not reveal anything
              }
            });
        break;
      case full:
        // scenario 2: localUser 'tenant user password'  (whitespace between these)
        StringBuilder tenant = new StringBuilder();
        StringBuilder user = new StringBuilder();
        StringBuilder password = new StringBuilder();
        log.info("Login strategy {} and using tenant {}", loginStrategyType, tenant);
        parseLocalUserFull(connexionRequest.getLocalUser().stripLeading(), tenant, user, password);
        if (password.length() == 0) {
          return Future.failedFuture("Bad format of localUser");
        }
        client = Client.createLoginClient(clientOptions, tenantUserCache, tenant.toString(),
            user.toString(), () -> Future.succeededFuture(password.toString()));
        break;
    }
    JsonObject content = new JsonObject();
    content.put("profileId", COPYCAT_PROFILE_OCLC);
    content.put("record",
        new JsonObject().put("marc",
            Base64.getEncoder().encodeToString(record.getBytes())
        ));
    HttpRequest<Buffer> bufferHttpRequest = webClient
        .postAbs(okapiUrl + "/copycat/imports");

    // Accept is not necessary with mod-copycat because it's based on RMB 32.2+ RMB-519
    // Content-Type is set to application/json by sendJsonObject
    return client.getToken(bufferHttpRequest)
        .compose(request -> request.sendJsonObject(content))
        .expecting(ChattyHttpResponseExpectation.SC_OK)
        .compose(response -> {
          log.info("Record imported via copycat");
          return Future.succeededFuture();
        });
  }

  private void configureTrustOptions(WebClientOptions webClientOptions) {
    boolean isSslEnabled = config().getBoolean(FOLIO_CLIENT_TLS_ENABLED);
    if (isSslEnabled) {
      log.info("Creating Web client with Enhance HTTP Endpoint Security and TLS mode enabled");
      webClientOptions.setSsl(true);
      String truststoreType = config().getString(FOLIO_CLIENT_TLS_TRUSTSTORETYPE);
      String truststorePath = config().getString(FOLIO_CLIENT_TLS_TRUSTSTOREPATH);
      String truststorePassword = config().getString(FOLIO_CLIENT_TLS_TRUSTSTOREPASSWORD);
      if (StringUtils.isNotEmpty(truststoreType)
          && StringUtils.isNotEmpty(truststorePath)
          && StringUtils.isNotEmpty(truststorePassword)) {

        log.info("Web client truststore options for type: {} are set, "
            + "configuring Web Client with them", truststoreType);
        TrustOptions trustOptions = new KeyStoreOptions()
            .setType(truststoreType)
            .setPath(truststorePath)
            .setPassword(truststorePassword);

        webClientOptions
            .setTrustOptions(trustOptions)
            .setVerifyHost(false);  //Hardcoded now. Later it could be configurable using env vars.
      } else {
        log.info("Web client truststore options are not set");
      }
    }
  }
}
