package org.folio.edge.connexion;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.LinkedList;
import java.util.List;

public class Importer {
  private static final Logger log = LogManager.getLogger(Importer.class);

  private final Buffer user = Buffer.buffer();
  private final Buffer localUser = Buffer.buffer();
  private final Buffer password = Buffer.buffer();
  private final List<Buffer> records = new LinkedList<>();

  private static int parseValue(Buffer buffer, int i, Buffer v) {
    i++;
    int len = buffer.getByte(i) - 64;
    if (len >= 0) {
      i++;
    } else {
      int llen = 0;
      while (buffer.getByte(i + llen) >= 48 && buffer.getByte(i + llen) < 58) {
        llen++;
      }
      len = Integer.parseInt(buffer.getString(i, i + llen));
      i += llen;
    }
    v.appendBuffer(buffer, i, len);
    return i + len;
  }

  private static int parseMarc(Buffer buffer, int i, List<Buffer> records) {
    int len = Integer.parseInt(buffer.getString(i, i + 5));
    records.add(buffer.slice(i, i + len));
    return i + len;
  }

  String getUser() {
    return user.toString();
  }

  String getLocalUser() {
    return localUser.toString();
  }

  String getPassword() {
    return password.toString();
  }

  List<Buffer> getRecords() {
    return records;
  }

  void parseRequest(Buffer buffer) {
    int i = 0;
    while (i < buffer.length()) {
      switch (buffer.getByte(i)) {
        case 'U':
          i = parseValue(buffer, i, user);
          break;
        case 'A':
          i = parseValue(buffer, i, localUser);
          break;
        case 'P':
          i = parseValue(buffer, i, password);
          break;
        case ' ':
          i++;
          break;
        default:
          i = parseMarc(buffer, i, records);
      }
    }
  }

  Future<Void> importFromOCLC(Buffer buffer) {
    try {
      parseRequest(buffer);
    } catch (Exception e) {
      log.warn(e.getMessage(), e);
      return Future.failedFuture(e);
    }
    return Future.succeededFuture();
  }
}
