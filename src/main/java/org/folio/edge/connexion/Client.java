package org.folio.edge.connexion;

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
    if (args.length != 4) {
      System.out.println("Usage: <host> <port> <key> <marcfile");
      System.exit(1);
    }
    Vertx vertx = Vertx.vertx();
    NetClient netClient = vertx.createNetClient();
    String localUser = args[2];
    byte[] nullByte = { 0 };
    netClient.connect(Integer.parseInt(args[1]), args[0])
        .compose(socket -> socket.write(
            "A" + localUser.getBytes(StandardCharsets.UTF_8).length + localUser).map(socket))
        .compose(socket -> socket.sendFile(args[3]).map(socket))
        .compose(socket -> socket.end().map(socket))
        .compose(socket -> socket.close())
        .onSuccess(x -> {
          log.info("Done");
          vertx.close();
        })
        .onFailure(x -> {
          log.error(x.getMessage(), x);
          vertx.close();
        });
  }
}
