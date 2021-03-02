package org.folio.edge.connexion;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetClient;
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

  @Test
  public void testAdminHealth(TestContext context) {
    final int port = 9230;
    JsonObject config = new JsonObject();
    config.put(SYS_PORT, port);

    WebClient webClient = WebClient.create(vertx);
    webClient.get(port, "localhost", "/admin/health").send().onComplete(context.asyncAssertFailure(f ->
        vertx.deployVerticle(new MainVerticle(), new DeploymentOptions().setConfig(config))
            .compose(x -> webClient.get(port, "localhost", "/admin/health").send())
            .onComplete(context.asyncAssertSuccess())
    ));
  }

  @Test
  public void testImportWithUserOK(TestContext context) {
    final int port = 9230;
    JsonObject config = new JsonObject();
    config.put(SYS_PORT, port);

    NetClient netClient = vertx.createNetClient();
    netClient.connect(port, "localhost").onComplete(context.asyncAssertFailure(f ->
        vertx.deployVerticle(new MainVerticle(), new DeploymentOptions().setConfig(config))
            .compose(x -> netClient.connect(port, "localhost").compose(socket -> socket.write("U6MyUser")))
            .onComplete(context.asyncAssertSuccess())
    ));
  }

  @Test
  public void testImportTooLargeMessage(TestContext context) {
    final int port = 9230;
    JsonObject config = new JsonObject();
    config.put(SYS_PORT, port);

    MainVerticle mainVerticle = new MainVerticle();
    mainVerticle.setMaxRecordSize(10);
    NetClient netClient = vertx.createNetClient();
    netClient.connect(port, "localhost").onComplete(context.asyncAssertFailure(f ->
        vertx.deployVerticle(mainVerticle, new DeploymentOptions().setConfig(config))
            .compose(x -> netClient.connect(port, "localhost").compose(socket -> socket.write("00006123456")))
            .onComplete(context.asyncAssertSuccess())
    ));
  }

}
