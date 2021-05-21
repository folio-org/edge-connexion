package org.folio.edge.connexion;

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
  private Buffer buffer;

  ConnexionRequest() {
    buffer = Buffer.buffer();
  }

  private static int parseValue(Buffer buffer, int i, Buffer v) {
    // two length specs
    //  1. single character specifies, where @=0, A=1, B=2, ... CHR-255=191 (up to 191 in length)
    //  2. multiple digits, followed by value (a value may not start with a digit!!)
    i++;
    byte leadingByte = buffer.getByte(i);
    int len;
    if (leadingByte >= 64) {
      len = leadingByte - 64;
      i++;
    } else if (leadingByte < 0) {
      len = 192 + leadingByte;
      i++;
    } else if (leadingByte == 48) {
      len = 0;
      i++;
    } else {
      // 2: digits makes up a number.
      int llen = 0;
      while (buffer.getByte(i + llen) >= 48 && buffer.getByte(i + llen) < 58) {
        llen++;
      }
      len = Integer.parseInt(buffer.getString(i, i + llen));
      i += llen;
    }
    if (i + len > buffer.length()) {
      throw new IndexOutOfBoundsException("incomplete value");
    }
    v.appendBuffer(buffer, i, len);
    return i + len;
  }

  private static int parseMarc(Buffer buffer, int i, List<Buffer> records) {
    int len = Integer.parseInt(buffer.getString(i, i + 5));
    if (buffer.length() < i + len) {
      throw new IndexOutOfBoundsException("Incomplete marc");
    }
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

  Buffer getBuffer() {
    return buffer;
  }

  void handle(Buffer chunk) {
    buffer.appendBuffer(chunk);
    int ret = feedInput(buffer);
    buffer = buffer.getBuffer(ret, buffer.length());
  }

  int feedInput(Buffer buffer) {
    int i = 0;
    while (i < buffer.length()) {
      try {
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
      } catch (IndexOutOfBoundsException | NumberFormatException e) {
        break;
      }
    }
    return i;
  }

}
