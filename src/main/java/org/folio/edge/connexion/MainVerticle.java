package org.folio.edge.connexion;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.edge.core.Constants;
import org.folio.edge.core.EdgeVerticleCore;

public class MainVerticle extends EdgeVerticleCore {

  private static final int MAX_RECORD_SIZE = 100000;
  private static final int DEFAULT_PORT = 8081;
  private static final Logger log = LogManager.getLogger(MainVerticle.class);
  private int maxRecordSize = MAX_RECORD_SIZE;
  int port;

  void setMaxRecordSize(int sz) {
    maxRecordSize = sz;
  }

  int getMaxRecordSize() {
    return maxRecordSize;
  }

  @Override
  public void start(Promise<Void> promise) {
    Future.<Void>future(p -> super.start(p)).<Void>compose(res -> {
      port = config().getInteger(Constants.SYS_PORT, DEFAULT_PORT);
      return vertx.createNetServer()
          .connectHandler(socket -> {
            // handle both HTTP For /admin/health and request Connexion client
            Buffer buffer = Buffer.buffer();
            socket.handler(chunk -> {
              buffer.appendBuffer(chunk);
              log.info("handler size {} , max {}", buffer.length(), maxRecordSize);
              if (buffer.length() > maxRecordSize) {
                log.warn("OCLC import size exceeded {}", maxRecordSize);
                socket.endHandler(x -> {});
                socket.close();
                return;
              }
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
                      socket.endHandler(x -> {});
                      log.debug("Got HTTP: {}", buffer.toString());
                      socket.write("HTTP/1.0 200 OK\r\n\r\n")
                          .onComplete(x -> socket.close());
                      return;
                    }
                  }
                }
              }
            });
            socket.endHandler(end -> {
              Importer importer = new Importer();
              importer.importRequest(buffer)
                  .onFailure(cause -> log.warn(cause.getMessage(), cause))
                  .onSuccess(complete -> log.info("Importing successfully"));
            });
          }).listen(port).mapEmpty();
    }).onComplete(promise);
  }
}
