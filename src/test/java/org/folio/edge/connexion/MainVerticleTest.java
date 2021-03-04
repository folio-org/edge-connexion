package org.folio.edge.connexion;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.BodyHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

  Vertx vertx;
  @Before
  public void before(TestContext context) {
    vertx = Vertx.vertx();
    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());
    router.post("/authn/login").handler(ctx -> {
      JsonObject login = ctx.getBodyAsJson();
      String username = login.getString("username");
      String password = login.getString("password");
      // only accept the diku that is in src/test/resources/ephemeral.properties
      if ("diku".equals(ctx.request().getHeader("X-Okapi-Tenant"))
          && "dikuuser".equals(username) && "abc123".equals(password)) {
        ctx.response().setStatusCode(201);
        ctx.response().putHeader("X-Okapi-Token", "validtoken");
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
      if (!"validtoken".equals(request.getHeader("X-Okapi-Token"))) {
        ctx.response().setStatusCode(401);
        ctx.response().putHeader("Content-Type", "text/plain");
        ctx.response().end("Bad or missing token");
      }
      JsonObject copycatImports = ctx.getBodyAsJson();
      if (!MainVerticle.COPYCAT_PROFILE_OCLC.equals(copycatImports.getString("profileId"))) {
        ctx.response().setStatusCode(400);
        ctx.response().putHeader("Content-Type", "text/plain");
        ctx.response().end("Missing profileId");
      }
      try {
        byte[] decode = Base64.getDecoder().decode(copycatImports.getJsonObject("record").getString("marc"));
        if (!MARC_SAMPLE.equals(new String(decode))) {
          ctx.response().setStatusCode(400);
          ctx.response().putHeader("Content-Type", "text/plain");
          ctx.response().end("Bad request");
          return;
        }
      } catch (Exception e) {
        ctx.response().setStatusCode(400);
        ctx.response().putHeader("Content-Type", "text/plain");
        ctx.response().end("Bad request");
      }
      ctx.response().setStatusCode(200);
      ctx.response().putHeader("Content-Type", "application/json");
      ctx.response().end(copycatImports.encode());
    });
    vertx.createHttpServer().requestHandler(router).listen(MOCK_PORT)
        .onComplete(context.asyncAssertSuccess(x -> {
          log.info("mock server started OK");
        }));
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

  @Test
  public void testAdminHealth(TestContext context) {
    WebClient webClient = WebClient.create(vertx);
    deploy(new MainVerticle())
        .compose(x -> webClient.get(PORT, "localhost", "/admin/health").send())
        .onComplete(context.asyncAssertSuccess(response -> {
          context.assertEquals(200, response.statusCode());
        }));
  }

  @Test
  public void testImportTooLargeMessage(TestContext context) {
    Async async = context.async();
    MainVerticle mainVerticle = new MainVerticle();
    mainVerticle.setMaxRecordSize(10);
    mainVerticle.setCompleteHandler(context.asyncAssertFailure(x -> {
      context.assertEquals("OCLC import size exceeded", x.getMessage());
      async.complete();
    }));
    deploy(mainVerticle)
        .compose(x -> vertx.createNetClient().connect(PORT, "localhost"))
        .compose(socket -> socket.write("00006123456"))
        .onComplete(context.asyncAssertSuccess());
    async.await();
  }

  @Test
  public void testImportWithNoRecord(TestContext context) {
    Async async = context.async();
    MainVerticle mainVerticle = new MainVerticle();
    mainVerticle.setCompleteHandler(context.asyncAssertFailure(x -> {
      context.assertEquals("One record expected in OCLC Connexion request", x.getMessage());
      async.complete();
    }));
    deploy(mainVerticle)
        .compose(x -> vertx.createNetClient().connect(PORT, "localhost"))
        .compose(socket -> socket.write("U6MyUser").map(socket))
        .compose(socket -> socket.close())
        .onComplete(context.asyncAssertSuccess());
    async.await();
  }

  @Test
  public void testImportWithLoginStrategyFullOk(TestContext context) {
    Async async = context.async();
    MainVerticle mainVerticle = new MainVerticle();
    mainVerticle.setCompleteHandler(context.asyncAssertSuccess(x -> async.complete()));
    deploy(mainVerticle, new JsonObject().put("login_strategy", "full"))
        .compose(x -> vertx.createNetClient().connect(PORT, "localhost"))
        .compose(socket -> socket.write("A20diku dikuuser abc123" + MARC_SAMPLE).map(socket))
        .compose(socket -> socket.close())
        .onComplete(context.asyncAssertSuccess());
    async.await();
  }

  @Test
  public void testImportWithLoginStrategyFullTwoOk(TestContext context) {
    Async async = context.async();
    MainVerticle mainVerticle = new MainVerticle();
    mainVerticle.setCompleteHandler(context.asyncAssertSuccess(x -> async.complete()));
    deploy(mainVerticle, new JsonObject().put("login_strategy", "full"))
        .compose(x -> vertx.createNetClient().connect(PORT, "localhost"))
        .compose(socket -> socket.write("A20diku dikuuser abc123" + MARC_SAMPLE).map(socket))
        .compose(socket -> socket.close())
        .onComplete(context.asyncAssertSuccess());
    async.await();
    Async async2 = context.async();
    mainVerticle.setCompleteHandler(context.asyncAssertSuccess(x -> async2.complete()));
    vertx.createNetClient().connect(PORT, "localhost")
        .compose(socket -> socket.write("A20diku dikuuser abc123" + MARC_SAMPLE).map(socket))
        .compose(socket -> socket.close())
        .onComplete(context.asyncAssertSuccess());
    async2.await();
  }

  @Test
  public void testImportWithLoginStrategyUnknown(TestContext context) {
    Async async = context.async();
    MainVerticle mainVerticle = new MainVerticle();
    mainVerticle.setCompleteHandler(context.asyncAssertFailure(x -> {
      context.assertEquals("Bad login_strategy unknown", x.getMessage());
      async.complete();
    }));
    deploy(mainVerticle, new JsonObject().put("login_strategy", "unknown"))
        .compose(x -> vertx.createNetClient().connect(PORT, "localhost"))
        .compose(socket -> socket.write("A20diku dikuuser abc123" + MARC_SAMPLE).map(socket))
        .compose(socket -> socket.close())
        .onComplete(context.asyncAssertSuccess());
    async.await();
  }

  @Test
  public void testImportWithLoginStrategyFullBadMARC(TestContext context) {
    Async async = context.async();
    MainVerticle mainVerticle = new MainVerticle();
    mainVerticle.setCompleteHandler(context.asyncAssertFailure(x -> {
      context.assertEquals("/copycat/imports returned status 400", x.getMessage());
      async.complete();
    }));
    deploy(mainVerticle, new JsonObject().put("login_strategy", "full"))
        .compose(x -> vertx.createNetClient().connect(PORT, "localhost"))
        .compose(socket -> socket.write("A20diku dikuuser abc123" + MARC_REJECT).map(socket))
        .compose(socket -> socket.close())
        .onComplete(context.asyncAssertSuccess());
    async.await();
  }

  @Test
  public void testImportWithLoginStrategyFullBadPw(TestContext context) {
    Async async = context.async();
    MainVerticle mainVerticle = new MainVerticle();
    mainVerticle.setCompleteHandler(context.asyncAssertFailure(x -> {
      context.assertEquals("/authn/login returned status 400", x.getMessage());
      async.complete();
    }));
    deploy(mainVerticle, new JsonObject().put("login_strategy", "full"))
        .compose(x -> vertx.createNetClient().connect(PORT, "localhost"))
        .compose(socket -> socket.write("A20diku dikuuser abc321" + MARC_SAMPLE).map(socket))
        .compose(socket -> socket.close())
        .onComplete(context.asyncAssertSuccess());
    async.await();
  }

  @Test
  public void testImportWithLoginStrategyFullBadTenant(TestContext context) {
    Async async = context.async();
    MainVerticle mainVerticle = new MainVerticle();
    mainVerticle.setCompleteHandler(context.asyncAssertFailure(x -> {
      context.assertEquals("/authn/login returned status 400", x.getMessage());
      async.complete();
    }));
    deploy(mainVerticle, new JsonObject().put("login_strategy", "full"))
        .compose(x -> vertx.createNetClient().connect(PORT, "localhost"))
        .compose(socket -> socket.write("A20ukid dikuuser abc123" + MARC_SAMPLE).map(socket))
        .compose(socket -> socket.close())
        .onComplete(context.asyncAssertSuccess());
    async.await();
  }

}
