package org.folio.edge.connexion;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetSocket;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.BodyHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.edge.core.utils.ApiKeyUtils;
import org.folio.okapi.common.ChattyHttpResponseExpectation;
import org.folio.okapi.common.XOkapiHeaders;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Base64;

import static org.folio.edge.core.Constants.SYS_OKAPI_URL;
import static org.folio.edge.core.Constants.SYS_PORT;
import static org.folio.edge.core.Constants.SYS_SECURE_STORE_PROP_FILE;

@RunWith(VertxUnitRunner.class)
public class MainVerticleTest {
  private static final Logger log = LogManager.getLogger(MainVerticleTest.class);
  // RMB has a utility to find a free port, and edge-common has its own testing..
  // we will use the same port as in Okapi does it its testing mode.
  private final int PORT = 9230;
  private final int MOCK_PORT = 9231;
  private final String MARC_SAMPLE = "00008cgm";
  private final String MARC_REJECT = "00008rej";
  private String expectMARC;

  Vertx vertx;

  @Before
  public void before(TestContext context) {
    expectMARC = MARC_SAMPLE;
    vertx = Vertx.vertx();
    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());
    router.post("/authn/login").handler(ctx -> {
      HttpServerRequest request = ctx.request();
      if (!"application/json".equals(request.getHeader("Content-Type"))) {
        ctx.response().setStatusCode(400);
        ctx.response().putHeader("Content-Type", "text/plain");
        ctx.response().end("Bad Request");
        return;
      }
      JsonObject login = ctx.body().asJsonObject();
      String username = login.getString("username");
      String password = login.getString("password");
      // only accept the diku that is in src/test/resources/ephemeral.properties
      if ("diku".equals(request.getHeader(XOkapiHeaders.TENANT))
          && "dikuuser".equals(username) && "abc123".equals(password)) {
        ctx.response().setStatusCode(201);
        ctx.response().putHeader(XOkapiHeaders.TOKEN, "validtoken");
        ctx.response().putHeader("Content-Type", "application/json");
        ctx.response().end(login.encode());
        log.info("login ok");
        return;
      }
      ctx.response().setStatusCode(400);
      ctx.response().putHeader("Content-Type", "text/plain");
      ctx.response().end("Bad Request");
    });
    router.post("/copycat/imports").handler(ctx -> {
      HttpServerRequest request = ctx.request();
      if (!"application/json".equals(request.getHeader("Content-Type"))) {
        ctx.response().setStatusCode(400);
        ctx.response().putHeader("Content-Type", "text/plain");
        ctx.response().end("Bad Request");
        return;
      }
      if (!"validtoken".equals(request.getHeader(XOkapiHeaders.TOKEN))) {
        ctx.response().setStatusCode(401);
        ctx.response().putHeader("Content-Type", "text/plain");
        ctx.response().end("Bad or missing token");
        return;
      }
      if (!"diku".equals(request.getHeader(XOkapiHeaders.TENANT))) {
        ctx.response().setStatusCode(401);
        ctx.response().putHeader("Content-Type", "text/plain");
        ctx.response().end("Token is " + request.getHeader(XOkapiHeaders.TENANT));
        return;
      }
      JsonObject copycatImports = ctx.getBodyAsJson();
      if (!MainVerticle.COPYCAT_PROFILE_OCLC.equals(copycatImports.getString("profileId"))) {
        ctx.response().setStatusCode(400);
        ctx.response().putHeader("Content-Type", "text/plain");
        ctx.response().end("Missing profileId");
        return;
      }
      try {
        byte[] decode = Base64.getDecoder().decode(copycatImports.getJsonObject("record").getString("marc"));
        if (expectMARC != null && !expectMARC.equals(new String(decode))) {
          ctx.response().setStatusCode(400);
          ctx.response().putHeader("Content-Type", "text/plain");
          ctx.response().end("Bad request (bad MARC)");
          return;
        }
        ctx.response().setStatusCode(200);
        ctx.response().putHeader("Content-Type", "application/json");
        ctx.response().end(copycatImports.encode());
      } catch (Exception e) {
        ctx.response().setStatusCode(400);
        ctx.response().putHeader("Content-Type", "text/plain");
        ctx.response().end("Bad request");
      }
    });
    vertx.createHttpServer().requestHandler(router).listen(MOCK_PORT)
        .onComplete(context.asyncAssertSuccess(x -> log.info("mock server started OK")));
  }

  @After
  public void after(TestContext context) {
    log.info("after called");
    vertx.close().onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void instanceMainVerticvle() {
    MainVerticle v = new MainVerticle();
    v.setMaxRecordSize(100);
    Assert.assertEquals(100, v.getMaxRecordSize());
  }

  Future<Void> deploy(MainVerticle mainVerticle) {
    return deploy(mainVerticle, new JsonObject());
  }

  Future<Void> deploy(MainVerticle mainVerticle, JsonObject config) {
    config.put(SYS_OKAPI_URL, "http://localhost:" + MOCK_PORT);
    config.put(SYS_PORT, PORT);
    config.put(SYS_SECURE_STORE_PROP_FILE, "src/test/resources/ephemeral.properties");
    return vertx.deployVerticle(mainVerticle, new DeploymentOptions().setConfig(config))
        .mapEmpty();
  }

  static Future<NetSocket> handleResponse(NetSocket socket, Buffer buffer) {
    socket.handler(chunk -> {
      int i;
      for (i = 0; i < chunk.length(); i++) {
        if (chunk.getByte(i) == (byte) 0) {
          buffer.appendBuffer(chunk.getBuffer(0, i));
          socket.close();
          return;
        }
      }
      buffer.appendBuffer(chunk);
    });
    return Future.succeededFuture(socket);
  }

  static Future<NetSocket> handleResponse(NetSocket socket) {
    return handleResponse(socket, Buffer.buffer());
  }

  @Test
  public void testAdminHealth(TestContext context) {
    WebClient webClient = WebClient.create(vertx);
    deploy(new MainVerticle())
        .compose(x -> webClient.get(PORT, "localhost", "/admin/health")
            .send()
            .expecting(ChattyHttpResponseExpectation.SC_OK))
        .onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void testAdminHealth2(TestContext context) {
    WebClient webClient = WebClient.create(vertx);
    deploy(new MainVerticle())
        .compose(x -> webClient.get(PORT, "localhost", "/admin/health")
            .send()
            .expecting(ChattyHttpResponseExpectation.SC_OK))
        .compose(x -> webClient.get(PORT, "localhost", "/other")
            .send()
            .expecting(ChattyHttpResponseExpectation.SC_OK))
        .onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void testImportTooLargeMessage(TestContext context) {
    MainVerticle mainVerticle = new MainVerticle();
    mainVerticle.setMaxRecordSize(10);
    mainVerticle.setCompleteHandler(context.asyncAssertFailure(x ->
        context.assertEquals("OCLC import size exceeded", x.getMessage())));
    deploy(mainVerticle)
        .compose(x -> vertx.createNetClient().connect(PORT, "localhost"))
        .compose(socket -> socket.write("00016123456"));
  }

  @Test
  public void testImportWithNoRecord(TestContext context) {
    MainVerticle mainVerticle = new MainVerticle();
    mainVerticle.setCompleteHandler(context.asyncAssertFailure(x ->
        context.assertEquals("No records provided", x.getMessage())));
    deploy(mainVerticle)
        .compose(x -> vertx.createNetClient().connect(PORT, "localhost"))
        .compose(socket -> socket.write("U4User").map(socket))
        .compose(NetSocket::close);
  }

  @Test
  public void testImportNullByte1(TestContext context) {
    MainVerticle mainVerticle = new MainVerticle();
    mainVerticle.setCompleteHandler(context.asyncAssertFailure(x ->
        context.assertEquals("No records provided", x.getMessage())));
    deploy(mainVerticle)
        .compose(x -> vertx.createNetClient().connect(PORT, "localhost"))
        .compose(socket -> socket.write("U4User\0").map(socket))
        .compose(NetSocket::close);
  }

  @Test
  public void testImportNullByte2(TestContext context) {
    MainVerticle mainVerticle = new MainVerticle();
    mainVerticle.setCompleteHandler(context.asyncAssertFailure(x ->
        context.assertEquals("No records provided", x.getMessage())));
    deploy(mainVerticle)
        .compose(x -> vertx.createNetClient().connect(PORT, "localhost"))
        .compose(socket -> socket.write("\0").map(socket))
        .compose(NetSocket::close);
  }

  @Test
  public void testImportWithLoginStrategyKeyNoLocalUser(TestContext context) {
    MainVerticle mainVerticle = new MainVerticle();
    mainVerticle.setCompleteHandler(context.asyncAssertFailure(x ->
        context.assertEquals("access denied", x.getMessage())));
    deploy(mainVerticle, new JsonObject())
        .compose(x -> vertx.createNetClient().connect(PORT, "localhost"))
        .compose(MainVerticleTest::handleResponse)
        .compose(socket -> socket.write("U1A" + MARC_SAMPLE));
  }

  @Test
  public void testImportWithLoginStrategyKeyOk(TestContext context) {
    String apiKey = ApiKeyUtils.generateApiKey("gYn0uFv3Lf", "diku", "dikuuser");
    MainVerticle mainVerticle = new MainVerticle();
    mainVerticle.setCompleteHandler(context.asyncAssertSuccess());
    deploy(mainVerticle, new JsonObject().put("login_strategy", "key"))
        .compose(x -> vertx.createNetClient().connect(PORT, "localhost"))
        .compose(MainVerticleTest::handleResponse)
        .compose(socket -> socket.write("A" + apiKey.length() + apiKey + MARC_SAMPLE));
  }

  @Test
  public void testImportWithLoginStrategyKeyOkWithResponse(TestContext context) {
    String apiKey = ApiKeyUtils.generateApiKey("gYn0uFv3Lf", "diku", "dikuuser");
    MainVerticle mainVerticle = new MainVerticle();
    Buffer response = Buffer.buffer();
    mainVerticle.setCompleteHandler(context.asyncAssertSuccess(x ->
        context.assertEquals("Import ok", Client.trimConnexionResponse(response.toString()))
    ));
    deploy(mainVerticle, new JsonObject().put("login_strategy", "key"))
        .compose(x -> vertx.createNetClient().connect(PORT, "localhost"))
        .compose(socket -> handleResponse(socket, response))
        .compose(socket -> socket.write("A" + apiKey.length() + apiKey + MARC_SAMPLE + "\0"));
  }

  @Test
  public void testImportWithLoginStrategyKeyOkTrim(TestContext context) {
    String apiKey = ApiKeyUtils.generateApiKey("gYn0uFv3Lf", "diku", "dikuuser");
    MainVerticle mainVerticle = new MainVerticle();
    mainVerticle.setCompleteHandler(context.asyncAssertSuccess());
    deploy(mainVerticle, new JsonObject().put("login_strategy", "key"))
        .compose(x -> vertx.createNetClient().connect(PORT, "localhost"))
        .compose(MainVerticleTest::handleResponse)
        .compose(socket -> socket.write("A" + (apiKey.length() + 4) + "  " + apiKey + "  " + MARC_SAMPLE));
  }

  @Test
  public void testImportWithLoginStrategyKeyBadPassword(TestContext context) {
    // is listed in ephemeral.properties, but is rejected by /authn/login
    String apiKey = ApiKeyUtils.generateApiKey("gYn0uFv3Lf", "badlib", "foo");
    MainVerticle mainVerticle = new MainVerticle();
    Buffer response = Buffer.buffer();
    mainVerticle.setCompleteHandler(context.asyncAssertFailure(x -> {
      // context.assertEquals("/authn/login returned status 400", x.getMessage());
      context.assertEquals("Error: Login failed. POST /authn/login for tenant 'badlib' and username 'foo' returned status 400: Bad Request", Client.trimConnexionResponse(response.toString()));
    }));
    deploy(mainVerticle, new JsonObject())
        .compose(x -> vertx.createNetClient().connect(PORT, "localhost"))
        .compose(socket -> handleResponse(socket, response))
        .compose(socket -> socket.write("A" + apiKey.length() + apiKey + MARC_SAMPLE + "\0"));
  }

  @Test
  public void testImportWithLoginStrategyKeyStoreMismatch(TestContext context) {
    // is not listed in ephemeral.properties
    String apiKey = ApiKeyUtils.generateApiKey("gYn0uFv3Lf", "unknown", "foo");
    MainVerticle mainVerticle = new MainVerticle();
    mainVerticle.setCompleteHandler(context.asyncAssertFailure(x ->
        context.assertEquals("Error retrieving password", x.getMessage())));
    deploy(mainVerticle, new JsonObject())
        .compose(x -> vertx.createNetClient().connect(PORT, "localhost"))
        .compose(MainVerticleTest::handleResponse)
        .compose(socket -> socket.write("A" + apiKey.length() + apiKey + MARC_SAMPLE));
  }

  @Test
  public void testImportWithLoginStrategyFullOk(TestContext context) {
    String localUser = "diku dikuuser abc123";
    MainVerticle mainVerticle = new MainVerticle();
    mainVerticle.setCompleteHandler(context.asyncAssertSuccess());
    deploy(mainVerticle, new JsonObject().put("login_strategy", "full"))
        .compose(x -> vertx.createNetClient().connect(PORT, "localhost"))
        .compose(MainVerticleTest::handleResponse)
        .compose(socket -> socket.write("A" + localUser.length() + localUser + MARC_SAMPLE));
  }

  @Test
  public void testImportWithLoginStrategyFullTwoOk(TestContext context) {
    Async async = context.async();
    String localUser = "diku dikuuser abc123";
    MainVerticle mainVerticle = new MainVerticle();
    mainVerticle.setCompleteHandler(context.asyncAssertSuccess(x -> async.complete()));
    deploy(mainVerticle, new JsonObject().put("login_strategy", "full"))
        .compose(x -> vertx.createNetClient().connect(PORT, "localhost"))
        .compose(MainVerticleTest::handleResponse)
        .compose(socket -> socket.write("A" + localUser.length() + localUser + MARC_SAMPLE));
    async.await();
    mainVerticle.setCompleteHandler(context.asyncAssertSuccess());
    vertx.createNetClient().connect(PORT, "localhost")
        .compose(MainVerticleTest::handleResponse)
        .compose(socket -> socket.write("A" + localUser.length() + localUser + MARC_SAMPLE));
  }

  @Test
  public void testImportWithLoginStrategyUnknown(TestContext context) {
    MainVerticle mainVerticle = new MainVerticle();
    deploy(mainVerticle, new JsonObject().put("login_strategy", "unknown"))
        .onComplete(context.asyncAssertFailure(x ->
            context.assertEquals("No enum constant org.folio.edge.connexion.MainVerticle.LoginStrategyType.unknown",
            x.getMessage())));
  }

  @Test
  public void testImportWithLoginStrategyBothAndKey(TestContext context) {
	    String apiKey = ApiKeyUtils.generateApiKey("gYn0uFv3Lf", "diku", "dikuuser");
	    MainVerticle mainVerticle = new MainVerticle();
	    mainVerticle.setCompleteHandler(context.asyncAssertSuccess());
	    deploy(mainVerticle, new JsonObject().put("login_strategy", "both"))
	        .compose(x -> vertx.createNetClient().connect(PORT, "localhost"))
	        .compose(MainVerticleTest::handleResponse)
	        .compose(socket -> socket.write("A" + (apiKey.length() + 4) + "  " + apiKey + "  " + MARC_SAMPLE));
  }
  
  @Test
  public void testImportWithLoginStrategyBothAndFull(TestContext context) {
	  String localUser = "diku dikuuser abc123";
	    MainVerticle mainVerticle = new MainVerticle();
	    mainVerticle.setCompleteHandler(context.asyncAssertSuccess());
	    deploy(mainVerticle, new JsonObject().put("login_strategy", "both"))
        .compose(x -> vertx.createNetClient().connect(PORT, "localhost"))
        .compose(MainVerticleTest::handleResponse)
        .compose(socket -> socket.write("A" + localUser.length() + localUser + MARC_SAMPLE));
  }

  @Test
  public void testImportWithLoginStrategyFullBadLocalFormat(TestContext context) {
    String localUser = "diku dikuuser";
    MainVerticle mainVerticle = new MainVerticle();
    mainVerticle.setCompleteHandler(context.asyncAssertFailure(x ->
        context.assertEquals("Bad format of localUser", x.getMessage())));
    deploy(mainVerticle, new JsonObject().put("login_strategy", "full"))
        .compose(x -> vertx.createNetClient().connect(PORT, "localhost"))
        .compose(MainVerticleTest::handleResponse)
        .compose(socket -> socket.write("A" + localUser.length() + localUser + MARC_SAMPLE));
  }

  @Test
  public void testImportWithLoginStrategyFullBadMARC(TestContext context) {
    String localUser = "diku dikuuser abc123";
    MainVerticle mainVerticle = new MainVerticle();
    mainVerticle.setCompleteHandler(context.asyncAssertFailure(x ->
        context.assertEquals("Response status code 400 is not equal to 200: Bad request (bad MARC)", x.getMessage())));
    deploy(mainVerticle, new JsonObject().put("login_strategy", "full"))
        .compose(x -> vertx.createNetClient().connect(PORT, "localhost"))
        .compose(MainVerticleTest::handleResponse)
        .compose(socket -> socket.write("A" + localUser.length() + localUser + MARC_REJECT));
  }

  @Test
  public void testImportWithLoginStrategyFullBadPw(TestContext context) {
    String localUser = "diku dikuuser abc321";
    MainVerticle mainVerticle = new MainVerticle();
    mainVerticle.setCompleteHandler(context.asyncAssertFailure(x ->
        context.assertEquals("Login failed. POST /authn/login for tenant 'diku' and username 'dikuuser' returned status 400: Bad Request", x.getMessage())));
    deploy(mainVerticle, new JsonObject().put("login_strategy", "full"))
        .compose(x -> vertx.createNetClient().connect(PORT, "localhost"))
        .compose(MainVerticleTest::handleResponse)
        .compose(socket -> socket.write("A" + localUser.length() + localUser + MARC_SAMPLE));
  }

  @Test
  public void testImportWithLoginStrategyFullBadTenant(TestContext context) {
    String localUser = "ukid dikuuser abc123";
    MainVerticle mainVerticle = new MainVerticle();
    mainVerticle.setCompleteHandler(context.asyncAssertFailure(x ->
        context.assertEquals("Login failed. POST /authn/login for tenant 'ukid' and username 'dikuuser' returned status 400: Bad Request", x.getMessage())));
    deploy(mainVerticle, new JsonObject().put("login_strategy", "full"))
        .compose(x -> vertx.createNetClient().connect(PORT, "localhost"))
        .compose(MainVerticleTest::handleResponse)
        .compose(socket -> socket.write("A" + localUser.length() + localUser + MARC_SAMPLE));
  }

  private void verifyParseLocalUserFull(String input, String expectTenant, String expectUser, String expectPassword) {
    StringBuilder tenant = new StringBuilder();
    StringBuilder user = new StringBuilder();
    StringBuilder password = new StringBuilder();
    MainVerticle.parseLocalUserFull(input, tenant, user, password);
    Assert.assertEquals(expectTenant, tenant.toString());
    Assert.assertEquals(expectUser, user.toString());
    Assert.assertEquals(expectPassword, password.toString());
  }

  @Test
  public void testParseLocalUserFull() {
    verifyParseLocalUserFull("", "", "", "");
    verifyParseLocalUserFull("a", "a", "", "");
    verifyParseLocalUserFull(" b", "", "b", "");
    verifyParseLocalUserFull("a  b", "a", "b", "");
    verifyParseLocalUserFull("a b c", "a", "b", "c");
    verifyParseLocalUserFull("a  b  cd e ", "a", "b", " cd e ");
    verifyParseLocalUserFull("a\nb\n cd e\n", "a", "b", " cd e\n");
  }

  @Test
  public void testClientOK(TestContext context) {
    expectMARC = null; // mock will not check for SAMPLE_MARC
    String apiKey = ApiKeyUtils.generateApiKey("gYn0uFv3Lf", "diku", "dikuuser");
    String [] args = { "localhost", Integer.toString(PORT),
        apiKey, "src/test/resources/how-to-program-a-computer.marc"};
    MainVerticle mainVerticle = new MainVerticle();
    mainVerticle.setCompleteHandler(context.asyncAssertSuccess());
    deploy(mainVerticle, new JsonObject())
        .compose(x -> Client.main1(vertx, args))
        .onSuccess(x -> context.assertEquals("Import ok", x));
  }

  @Test
  public void testClientBadPassword(TestContext context) {
    expectMARC = null; // mock will not check for SAMPLE_MARC
    String apiKey = ApiKeyUtils.generateApiKey("gYn0uFv3Lf", "diku", "dikuuserx");
    String [] args = { "localhost", Integer.toString(PORT),
        apiKey, "src/test/resources/how-to-program-a-computer.marc"};
    MainVerticle mainVerticle = new MainVerticle();
    mainVerticle.setCompleteHandler(context.asyncAssertFailure());
    deploy(mainVerticle, new JsonObject())
        .compose(x -> Client.main1(vertx, args))
        .onSuccess(x -> context.assertEquals("Error: Error retrieving password", x));
  }

  @Test
  public void testClientBadFilename(TestContext context) {
    expectMARC = null; // mock will not check for SAMPLE_MARC
    String apiKey = ApiKeyUtils.generateApiKey("gYn0uFv3Lf", "diku", "dikuuser");
    String [] args = { "localhost", Integer.toString(PORT),
        apiKey, "src/test/resources/does-not-exist.marc"};
    MainVerticle mainVerticle = new MainVerticle();
    deploy(mainVerticle, new JsonObject())
        .compose(x -> Client.main1(vertx, args)).onComplete(context.asyncAssertFailure(cause ->
        context.assertTrue(cause.getMessage().contains("src/test/resources/does-not-exist.marc"),
            cause.getMessage())
    ));
  }

  @Test
  public void testClientBadConnectionRefused(TestContext context) {
    String apiKey = ApiKeyUtils.generateApiKey("gYn0uFv3Lf", "diku", "dikuuser");
    String [] args = { "localhost", Integer.toString(PORT),
        apiKey, "src/test/resources/how-to-program-a-computer.marc"};
    Client.main1(vertx, args).onComplete(context.asyncAssertFailure(cause ->
        context.assertTrue(cause.getClass().getName().contains("ConnectException"), cause.getClass().getName())
    ));
  }

  @Test
  public void testClientBadNoArgs(TestContext context) {
    String [] args = { "localhost" };
    Client.main1(vertx, args).onComplete(context.asyncAssertFailure(cause ->
        context.assertTrue(cause.getMessage().contains("Usage:"), cause.getMessage())
    ));
  }

  @Test
  public void testClientOK1(TestContext context) {
    expectMARC = null; // mock will not check for SAMPLE_MARC
    String apiKey = ApiKeyUtils.generateApiKey("gYn0uFv3Lf", "diku", "dikuuser");
    String [] args = { "localhost", Integer.toString(PORT),
        apiKey, "src/test/resources/how-to-program-a-computer.marc"};
    MainVerticle mainVerticle = new MainVerticle();
    mainVerticle.setCompleteHandler(context.asyncAssertSuccess());
    deploy(mainVerticle, new JsonObject())
        .onComplete(x -> Client.main(args));
  }

  @Test
  public void showArgs(TestContext context) {
    Client.main(new String [] {"help"});
    Client.main1(vertx, new String [] {"help"})
        .onComplete(context.asyncAssertFailure(x ->
          context.assertEquals("Usage: <host> <port> <key> <marcfile>", x.getMessage())
        ));
    }
}
