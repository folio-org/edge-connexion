package org.folio.edge.connexion;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.edge.core.EdgeVerticleCore;

import static org.folio.edge.core.Constants.SYS_PORT;

public class MainVerticle extends EdgeVerticleCore {

    private final int MAX_RECORD_SIZE = 100000;
    private static final Logger logger = LogManager.getLogger(MainVerticle.class);
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
            port = config().getInteger(SYS_PORT);
            return vertx.createNetServer()
                    .connectHandler(socket -> {
                        Buffer buffer = Buffer.buffer();
                        socket.handler(chunk -> {
                            buffer.appendBuffer(chunk);
                            if (buffer.length() > maxRecordSize) {
                                socket.close();
                            }
                        });
                        socket.endHandler(end -> {
                            logger.info("Got buffer of size {}", buffer.length());
                        });
                    }).listen(port).mapEmpty();
        }).onComplete(promise);
    }
}
