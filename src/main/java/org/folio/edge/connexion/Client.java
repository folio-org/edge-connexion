package org.folio.edge.connexion;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
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
          } else {
            log.info("{}", res.result());
          }
          vertx.close();
        });
  }

  static String trimConnexionResponse(String s) {
    int i = s.length();
    while (i > 0) {
      char c = s.charAt(i - 1);
      if (c != '\0' && c != '\n' && c != '\r') {
        break;
      }
      --i;
    }
    return s.substring(0, i);
  }

  static Future<String> main1(Vertx vertx, String [] args) {
    if (args.length != 4) {
      return Future.failedFuture("Usage: <host> <port> <key> <marcfile>");
    }
    NetClient netClient = vertx.createNetClient();
    String localUser = args[2];
    Promise<String> promise = Promise.promise();
    netClient.connect(Integer.parseInt(args[1]), args[0])
        .compose(socket -> socket.write(
            "A" + localUser.getBytes(StandardCharsets.UTF_8).length + localUser).map(socket))
        .compose(socket -> {
          Buffer response = Buffer.buffer();
          socket.handler(chunk -> {
            response.appendBuffer(chunk);
            for (int i = 0; i < chunk.length(); i++) {
              if (chunk.getByte(i) == (byte) 0) {
                socket.close();
                return;
              }
            }
          });
          socket.endHandler(end -> promise.complete(trimConnexionResponse(response.toString())));
          return Future.succeededFuture(socket);
        })
        .compose(socket -> socket.sendFile(args[3]).map(socket))
        .onFailure(promise::fail);
    return promise.future();
  }
}
