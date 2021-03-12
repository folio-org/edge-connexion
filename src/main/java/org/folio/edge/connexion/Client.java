package org.folio.edge.connexion;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetClient;
import java.nio.charset.StandardCharsets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Client {
  private static final Logger log = LogManager.getLogger(Client.class);

  /**
   * Connexion client utility.
   * @param args command line arguments.
   */
  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();
    main1(vertx, args)
        .onComplete(res -> {
          if (res.failed()) {
            log.error(res.cause().getMessage(), res.cause());
          }
          vertx.close();
        });
  }

  static Future<Void> main1(Vertx vertx, String [] args) {
    if (args.length != 4) {
      return Future.failedFuture("Usage: <host> <port> <key> <marcfile");
    }
    NetClient netClient = vertx.createNetClient();
    String localUser = args[2];
    return netClient.connect(Integer.parseInt(args[1]), args[0])
        .compose(socket -> socket.write(
            "A" + localUser.getBytes(StandardCharsets.UTF_8).length + localUser).map(socket))
        .compose(socket -> socket.sendFile(args[3]).map(socket))
        .compose(socket -> socket.end().map(socket))
        .compose(socket -> socket.close().mapEmpty());
  }
}
