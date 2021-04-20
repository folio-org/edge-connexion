package org.folio.edge.connexion;

import io.vertx.core.buffer.Buffer;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.nio.charset.StandardCharsets;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class ConnexionRequestTest {

  @Test
  public void parseRequest1() {
    ConnexionRequest connexionRequest = new ConnexionRequest();
    connexionRequest.feedInput(Buffer.buffer("U5abcdeA2fgP3hij0001012345"));
    Assert.assertEquals("abcde", connexionRequest.getUser());
    Assert.assertEquals("fg", connexionRequest.getLocalUser());
    Assert.assertEquals("hij", connexionRequest.getPassword());
    Assert.assertEquals("0001012345", connexionRequest.getRecords().get(0).toString());
  }

  @Test
  public void parseRequest2() {
    ConnexionRequest connexionRequest = new ConnexionRequest();
    connexionRequest.feedInput(Buffer.buffer(" UEabcde ABfg PChij 0001012345"));
    Assert.assertEquals("abcde", connexionRequest.getUser());
    Assert.assertEquals("fg", connexionRequest.getLocalUser());
    Assert.assertEquals("hij", connexionRequest.getPassword());
    Assert.assertEquals("0001012345", connexionRequest.getRecords().get(0).toString());
  }

  @Test
  public void parseRequestUTF8() {
    ConnexionRequest connexionRequest = new ConnexionRequest();
    String localUser = "testlib user bøf";
    Assert.assertEquals(16, localUser.length());
    int byteLength = localUser.getBytes(StandardCharsets.UTF_8).length;
    Assert.assertEquals(17, byteLength);
    connexionRequest.feedInput(Buffer.buffer("A" + byteLength + localUser + "00007æ" + "00008123"));
    Assert.assertEquals("testlib user bøf", connexionRequest.getLocalUser());
    Assert.assertEquals("00007æ", connexionRequest.getRecords().get(0).toString());
    Assert.assertEquals("00008123", connexionRequest.getRecords().get(1).toString());
  }

  public void parseRequestBadNumberFormat1() {
    ConnexionRequest connexionRequest = new ConnexionRequest();
    Assert.assertEquals(0, connexionRequest.feedInput(Buffer.buffer("U?abcde")));
  }

  public void parseRequestBadNumberFormat2() {
    ConnexionRequest connexionRequest = new ConnexionRequest();
    Assert.assertEquals(0, connexionRequest.feedInput(Buffer.buffer("_")));
  }

  public void parseRequestBadRangeUser() {
    ConnexionRequest connexionRequest = new ConnexionRequest();
    Assert.assertEquals(0, connexionRequest.feedInput(Buffer.buffer("U19abcde")));
  }

  public void parseRequestBadRangeMarc() {
    ConnexionRequest connexionRequest = new ConnexionRequest();
    Assert.assertEquals(0, connexionRequest.feedInput(Buffer.buffer("1000012345")));
  }

  @Test
  public void testImportFromOCLC() {
    ConnexionRequest connexionRequest = new ConnexionRequest();
    connexionRequest.feedInput(Buffer.buffer("U5!bcdeA2fgP3hij0001012345"));
    Assert.assertEquals("!bcde", connexionRequest.getUser());
    Assert.assertEquals("fg", connexionRequest.getLocalUser());
    Assert.assertEquals("hij", connexionRequest.getPassword());
    Assert.assertEquals("0001012345", connexionRequest.getRecords().get(0).toString());
  }

  @Test
  public void testFeedInput() {
    ConnexionRequest connexionRequest = new ConnexionRequest();
    Assert.assertEquals(0, connexionRequest.feedInput(Buffer.buffer()));
    Assert.assertEquals(0, connexionRequest.feedInput(Buffer.buffer("A")));
    Assert.assertEquals(0, connexionRequest.feedInput(Buffer.buffer("A3")));
    Assert.assertEquals(0, connexionRequest.feedInput(Buffer.buffer("U3ab")));
    Assert.assertEquals(5, connexionRequest.feedInput(Buffer.buffer("U3abc")));
    Assert.assertEquals(1, connexionRequest.feedInput(Buffer.buffer(" U3ab")));
    Assert.assertEquals(7, connexionRequest.feedInput(Buffer.buffer(" U3abc ")));
    Assert.assertEquals(7, connexionRequest.feedInput(Buffer.buffer(" U3abc 0")));
    Assert.assertEquals(7, connexionRequest.feedInput(Buffer.buffer(" U3abc 00010")));
    Assert.assertEquals(7, connexionRequest.feedInput(Buffer.buffer(" U3abc 000101")));
    Assert.assertEquals(7, connexionRequest.feedInput(Buffer.buffer(" U3abc 0001012")));
    Assert.assertEquals(7, connexionRequest.feedInput(Buffer.buffer(" U3abc 00010123")));
    Assert.assertEquals(7, connexionRequest.feedInput(Buffer.buffer(" U3abc 000101234")));
    Assert.assertEquals(17, connexionRequest.feedInput(Buffer.buffer(" U3abc 0001012345")));
    Assert.assertEquals(0, connexionRequest.feedInput(Buffer.buffer("GET / HTTP/1.1")));
    Assert.assertEquals(0, connexionRequest.feedInput(Buffer.buffer("0")));
    Assert.assertEquals(0, connexionRequest.feedInput(Buffer.buffer("0")));
    Assert.assertEquals(0, connexionRequest.feedInput(Buffer.buffer("00")));
    Assert.assertEquals(0, connexionRequest.feedInput(Buffer.buffer("000")));
    Assert.assertEquals(0, connexionRequest.feedInput(Buffer.buffer("0000")));
    Assert.assertEquals(5, connexionRequest.feedInput(Buffer.buffer("00005")));
    Assert.assertEquals(0, connexionRequest.feedInput(Buffer.buffer("00016123456")));
    Assert.assertEquals(0, connexionRequest.feedInput(Buffer.buffer("0001212345")));
    Assert.assertEquals(11, connexionRequest.feedInput(Buffer.buffer("00011123456")));
    Assert.assertEquals(11, connexionRequest.feedInput(Buffer.buffer("000111234560")));
  }
}
