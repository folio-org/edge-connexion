package org.folio.edge.connexion;

import io.vertx.core.Promise;
import io.vertx.ext.web.Router;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.edge.core.EdgeVerticle;

public class MainVerticle extends EdgeVerticle {

  private static final Logger logger = LogManager.getLogger(MainVerticle.class);

  @Override
  public void start(Promise<Void> promise) {
    logger.info("start");
    promise.complete();
  }

  @Override
  public void stop(Promise<Void> promise) {
    logger.info("stpp");
    promise.complete();
  }

  @Override
  public Router defineRoutes() {
    return null;
  }
}
