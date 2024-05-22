package org.folio.edge.connexion;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.file.FileSystemException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.folio.edge.core.Constants;
import org.folio.edge.core.utils.ApiKeyUtils;
import org.folio.edge.core.utils.test.MockOkapi;
import org.folio.edge.core.utils.test.TestUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;

import static org.folio.edge.core.Constants.*;
import static org.folio.edge.core.Constants.SYS_REQUEST_TIMEOUT_MS;
import static org.mockito.Mockito.spy;

@RunWith(VertxUnitRunner.class)
public class MainVerticleSslAndTslTest {

  private static final String KEYSTORE_TYPE = "BCFKS";
  private static final String KEYSTORE_PATH = "test.keystore 1.bcfks";
  private static final String TRUST_STORE_PATH = "test.truststore 1.bcfks";
  private static final String KEYSTORE_PASSWORD = "SecretPassword";

  private Vertx vertx;

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Before
  public void setUpOnce() {
    Security.addProvider(new BouncyCastleFipsProvider());
    vertx = Vertx.vertx();
  }

  @After
  public void tearDownOnce() {
    vertx.close();
  }

  @Test
  public void setupSslConfigWithoutType(TestContext context) throws Exception {
    JsonObject config = getSslConfig()
        .put(SYS_HTTP_SERVER_SSL_ENABLED, true);

    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("'keystore_type' system param must be specified when ssl_enabled = true");

    deployVerticle(context, config);
  }

  @Test
  public void setupSslConfigWithoutPath(TestContext context) throws Exception {
    JsonObject config = getSslConfig()
        .put(SYS_HTTP_SERVER_SSL_ENABLED, true)
        .put(SYS_HTTP_SERVER_KEYSTORE_TYPE, "JKS");

    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("'keystore_path' system param must be specified when ssl_enabled = true");

    deployVerticle(context, config);
  }

  @Test
  public void setupSslConfigWithoutPassword(TestContext context) throws Exception {
    JsonObject config = getSslConfig()
        .put(SYS_HTTP_SERVER_SSL_ENABLED, true)
        .put(SYS_HTTP_SERVER_KEYSTORE_TYPE, "JKS")
        .put(SYS_HTTP_SERVER_KEYSTORE_PATH, "sample_keystore.jks");

    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("'keystore_password' system param must be specified when ssl_enabled = true");

    deployVerticle(context, config);
  }

  @Test
  public void setupSslConfigWitInvalidPath(TestContext context) throws Exception {
    JsonObject config = getSslConfig()
        .put(SYS_HTTP_SERVER_SSL_ENABLED, true)
        .put(SYS_HTTP_SERVER_KEYSTORE_TYPE, "JKS")
        .put(SYS_HTTP_SERVER_KEYSTORE_PATH, "some_keystore_path")
        .put(SYS_HTTP_SERVER_KEYSTORE_PASSWORD, "password");

    thrown.expect(FileSystemException.class);
    thrown.expectMessage("Unable to read file at path 'some_keystore_path'");

    deployVerticle(context, config);
  }

  @Test
  public void setupSslConfigWithNotValidPassword(TestContext context) throws Exception {
    JsonObject config = getSslConfig()
        .put(SYS_HTTP_SERVER_SSL_ENABLED, true)
        .put(SYS_HTTP_SERVER_KEYSTORE_TYPE, "JKS")
        .put(SYS_HTTP_SERVER_KEYSTORE_PATH, "sample_keystore.jks")
        .put(SYS_HTTP_SERVER_KEYSTORE_PASSWORD, "not_valid_password");

    thrown.expect(IOException.class);
    thrown.expectMessage("keystore password was incorrect");

    deployVerticle(context, config);
  }

  @Test
  public void setupCorrectSslConfig(TestContext context) throws Exception {
    JsonObject config = getSslConfig()
        .put(SYS_HTTP_SERVER_SSL_ENABLED, true)
        .put(SYS_HTTP_SERVER_KEYSTORE_TYPE, "JKS")
        .put(SYS_HTTP_SERVER_KEYSTORE_PATH, "sample_keystore.jks")
        .put(SYS_HTTP_SERVER_KEYSTORE_PASSWORD, "password");

    deployVerticle(context, config);
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

  @Test
  public void testSetupCorrectTslConfig(TestContext context) throws Exception {
    JsonObject config = getTslConfig();
    deployVerticle(context, config);
  }

  private JsonObject getSslConfig() {
    int serverPort = TestUtils.getPort();
    int okapiPort = TestUtils.getPort();
    return new JsonObject()
        .put(SYS_PORT, serverPort)
        .put(SYS_OKAPI_URL, "http://localhost:" + okapiPort)
        .put(SYS_SECURE_STORE_PROP_FILE, "src/test/resources/ephemeral.properties")
        .put(SYS_LOG_LEVEL, "TRACE")
        .put(SYS_REQUEST_TIMEOUT_MS, 5000);
  }

  private JsonObject getTslConfig() {
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
}
