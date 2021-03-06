package org.folio.edge.connexion;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import java.util.LinkedList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ConnexionRequest {
  private static final Logger log = LogManager.getLogger(ConnexionRequest.class);

  private final Buffer user = Buffer.buffer();
  private final Buffer localUser = Buffer.buffer();
  private final Buffer password = Buffer.buffer();
  private final List<Buffer> records = new LinkedList<>();

  private static int parseValue(Buffer buffer, int i, Buffer v) {
    // two length specs
    //  1. single character specifies, where @=0, A=1, B=2, ... DEL=63 (only up to 63 in length !!)
    //  2. multiple digits, followed by value (a value may not start with a digit!!)
    i++;
    int len = buffer.getByte(i) - 64;
    if (len >= 0) {
      i++; // 1: single character length, skip length itself
    } else {
      // 2: digits makes up a number.
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

  /**
   * Parse request from OCLC Connexion client.
   * May throw exception for bad buffer.
   * @param buffer OCLC Connexion buffer.
   */
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
          log.info("byte={}", buffer.getByte(i));
          i = parseMarc(buffer, i, records);
      }
    }
  }

  /**
   * Parse request from OCLC Connexion client.
   * @param buffer OCLC Connexion buffer.
   * @return Future failed future on bad buffer. succeeded future otherwise.
   */
  Future<Void> parse(Buffer buffer) {
    try {
      parseRequest(buffer);
    } catch (Exception e) {
      log.warn(e.getMessage(), e);
      return Future.failedFuture(e);
    }
    return Future.succeededFuture();
  }

}
