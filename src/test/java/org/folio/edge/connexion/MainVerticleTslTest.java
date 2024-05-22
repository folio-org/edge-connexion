package org.folio.edge.connexion;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.KeyStoreOptions;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.folio.edge.core.Constants;
import org.folio.edge.core.utils.ApiKeyUtils;
import org.folio.edge.core.utils.SslConfigurationUtil;
import org.folio.edge.core.utils.test.MockOkapi;
import org.folio.edge.core.utils.test.TestUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.Security;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.spy;

@RunWith(VertxUnitRunner.class)
public class MainVerticleTslTest {

  private static final Logger logger = LogManager.getLogger(MainVerticleTest.class);
  static Vertx vertx;

  static final String KEYSTORE_TYPE = "BCFKS";
  static final String KEYSTORE_PATH = "test.keystore 1.bcfks";
  static final String TRUST_STORE_PATH = "test.truststore 1.bcfks";
  static final String KEYSTORE_PASSWORD = "SecretPassword";
  static final String RESPONSE_MESSAGE = "<OK>";

  @BeforeClass
  public static void setUpOnce(TestContext context) {
    Security.addProvider(new BouncyCastleFipsProvider());
    vertx = Vertx.vertx();
  }

  @AfterClass
  public static void tearDownOnce(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
  }

  @Test
  public void testServerClientTlsCommunication(TestContext context) {
    final JsonObject config = getCommonConfig();

    final HttpServerOptions serverOptions = new HttpServerOptions();
    serverOptions.setPort(config.getInteger(Constants.SYS_PORT));

    SslConfigurationUtil.configureSslServerOptionsIfEnabled(config, serverOptions);

    final HttpServer httpServer = vertx.createHttpServer(serverOptions);
    httpServer
        .requestHandler(req -> req.response().putHeader("content-type", "text/plain").end(RESPONSE_MESSAGE))
        .listen(config.getInteger(Constants.SYS_PORT), http -> logger.info("Server started on port {}", config.getInteger(Constants.SYS_PORT)));

    final WebClientOptions clientOptions = new WebClientOptions();
    clientOptions
        .setSsl(config.getBoolean(Constants.SYS_WEB_CLIENT_SSL_ENABLED))
        .setVerifyHost(true)
        .setTrustOptions(new KeyStoreOptions()
            .setType(config.getString(Constants.SYS_WEB_CLIENT_TRUSTSTORE_TYPE))
            .setPath(config.getString(Constants.SYS_WEB_CLIENT_TRUSTSTORE_PATH))
            .setPassword(config.getString(Constants.SYS_WEB_CLIENT_TRUSTSTORE_PASSWORD)));
    final WebClient webClient = WebClient.create(vertx, clientOptions);

    webClient.get(config.getInteger(Constants.SYS_PORT), "localhost", "/")
        .send()
        .onComplete(context.asyncAssertSuccess(response -> {
          String message = response.body().toString();
          logger.info("WebClient sent message to server port {}, response message: {}", config.getInteger(Constants.SYS_PORT), message);
          context.assertEquals(HttpResponseStatus.OK.code(), response.statusCode());
          context.assertEquals(RESPONSE_MESSAGE, message);
        }));
  }

  @Test
  public void testSetupCorrectTslConfig(TestContext context) throws Exception {
    JsonObject config = getCommonConfig();
    deployVerticle(context, config);
  }

  private JsonObject getCommonConfig() {
    int serverPort = TestUtils.getPort();
    return new JsonObject().put(Constants.SYS_PORT, serverPort)
        .put(Constants.SYS_HTTP_SERVER_SSL_ENABLED, true)
        .put(Constants.SYS_HTTP_SERVER_KEYSTORE_TYPE, KEYSTORE_TYPE)
        .put(Constants.SYS_HTTP_SERVER_KEYSTORE_PATH, KEYSTORE_PATH)
        .put(Constants.SYS_HTTP_SERVER_KEYSTORE_PASSWORD, KEYSTORE_PASSWORD)
        .put(Constants.SYS_WEB_CLIENT_SSL_ENABLED, true)
        .put(Constants.SYS_WEB_CLIENT_TRUSTSTORE_TYPE, KEYSTORE_TYPE)
        .put(Constants.SYS_WEB_CLIENT_TRUSTSTORE_PATH, TRUST_STORE_PATH)
        .put(Constants.SYS_WEB_CLIENT_TRUSTSTORE_PASSWORD, KEYSTORE_PASSWORD);
  }

  private void deployVerticle(TestContext context, JsonObject config) throws ApiKeyUtils.MalformedApiKeyException {
    int okapiPort = TestUtils.getPort();

    List<String> knownTenants = new ArrayList<>();
    String apiKey = ApiKeyUtils.generateApiKey("gYn0uFv3Lf", "diku", "diku");
    knownTenants.add(ApiKeyUtils.parseApiKey(apiKey).tenantId);

    MockOkapi mockOkapi = spy(new MockOkapi(okapiPort, knownTenants));
    mockOkapi.start()
        .onComplete(context.asyncAssertSuccess());

    vertx = Vertx.vertx();

    final DeploymentOptions opt = new DeploymentOptions().setConfig(config);
    vertx.deployVerticle(MainVerticle.class.getName(), opt, context.asyncAssertSuccess());
  }
}
