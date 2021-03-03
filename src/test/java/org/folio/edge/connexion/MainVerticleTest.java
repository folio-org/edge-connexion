package org.folio.edge.connexion;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.WebClient;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.folio.edge.core.Constants.SYS_PORT;

@RunWith(VertxUnitRunner.class)
public class MainVerticleTest {
  // RMB has a utility to find a free port, and edge-common has its own testing..
  // we will use the same port as in Okapi does it its testing mode.
  private final int PORT = 9230;

  Vertx vertx;
  @Before
  public void before(TestContext context) {
    vertx = Vertx.vertx();
  }

  @After
  public void after(TestContext context) {
    vertx.close().onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void instanceMainVerticvle() {
    MainVerticle v = new MainVerticle();
    v.setMaxRecordSize(100);
    Assert.assertEquals(100, v.getMaxRecordSize());
  }

  Future<Void> deploy(MainVerticle mainVerticle) {
    JsonObject config = new JsonObject();
    config.put(SYS_PORT, PORT);
    return vertx.deployVerticle(mainVerticle, new DeploymentOptions().setConfig(config))
        .mapEmpty();
  }

  @Test
  public void testAdminHealth(TestContext context) {
    WebClient webClient = WebClient.create(vertx);
    deploy(new MainVerticle())
        .compose(x -> webClient.get(PORT, "localhost", "/admin/health").send())
        .onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void testImportWithUserOK(TestContext context) {
    deploy(new MainVerticle())
        .compose(x -> vertx.createNetClient().connect(PORT, "localhost"))
        .compose(socket -> socket.write("U6MyUser"))
        .onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void testImportTooLargeMessage(TestContext context) {
    MainVerticle mainVerticle = new MainVerticle();
    mainVerticle.setMaxRecordSize(10);
    deploy(mainVerticle)
        .compose(x -> vertx.createNetClient().connect(PORT, "localhost"))
        .compose(socket -> socket.write("00006123456"))
        .onComplete(context.asyncAssertSuccess());
  }

}
