package org.folio.edge.connexion;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.edge.core.EdgeVerticleCore;

import static org.folio.edge.core.Constants.SYS_PORT;

public class MainVerticle extends EdgeVerticleCore {

  private static final int MAX_RECORD_SIZE = 100000;
  private static final int DEFAULT_PORT = 8081;
  private static final Logger log = LogManager.getLogger(MainVerticle.class);
  private int maxRecordSize = MAX_RECORD_SIZE;

  void setMaxRecordSize(int sz) {
    maxRecordSize = sz;
  }

  int getMaxRecordSize() {
    return maxRecordSize;
  }

  int port;
  @Override
  public void start(Promise<Void> promise) {
    Future.<Void>future(p -> super.start(p)).<Void>compose(res -> {
      port = config().getInteger(SYS_PORT, DEFAULT_PORT);
      return vertx.createNetServer()
          .connectHandler(socket -> {

            Buffer buffer = Buffer.buffer();
            // handle both HTTP For /admin/health and for the Connexion callback
            socket.handler(chunk -> {
              buffer.appendBuffer(chunk);
              for (int i = 0; i < buffer.length(); i++) {
                // look for LFCRLF or LFLF
                if (buffer.getByte(i) == '\n') {
                  int j = i + 1;
                  if (j < buffer.length() && buffer.getByte(j) == '\r') {
                    j++;
                  }
                  if (j < buffer.length() && buffer.getByte(j) == '\n') {
                    if ("GET ".equals(buffer.getString(0, 4))) {
                      // close now, ignoring keep alive (which is our right!)
                      log.debug("Got HTTP: {}", buffer.toString());
                      socket.write("HTTP/1.0 200 OK\r\n\r\n")
                          .onComplete(x -> socket.close());
                    }
                  }
                }
              }
              if (buffer.length() > maxRecordSize) {
                socket.close();
              }
            });
            socket.endHandler(end -> {
              log.info("Got buffer of size {}", buffer.length());
            });
          }).listen(port).mapEmpty();
    }).onComplete(promise);
  }
}
