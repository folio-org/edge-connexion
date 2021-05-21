package org.folio.edge.connexion;

import io.vertx.core.buffer.Buffer;
import java.nio.charset.StandardCharsets;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class ConnexionRequestTest {

  @Test
  public void parseRequest1DigitLength() {
    ConnexionRequest connexionRequest = new ConnexionRequest();
    connexionRequest.feedInput(Buffer.buffer("U5abcdeA2fgP3hij0001012345"));
    Assert.assertEquals("abcde", connexionRequest.getUser());
    Assert.assertEquals("fg", connexionRequest.getLocalUser());
    Assert.assertEquals("hij", connexionRequest.getPassword());
    Assert.assertEquals("0001012345", connexionRequest.getRecords().get(0).toString());
  }

  @Test
  public void parseRequestSingleCharLength1() {
    ConnexionRequest connexionRequest = new ConnexionRequest();
    connexionRequest.feedInput(Buffer.buffer(" UEabcde ABfg PChij 0001012345"));
    Assert.assertEquals("abcde", connexionRequest.getUser());
    Assert.assertEquals("fg", connexionRequest.getLocalUser());
    Assert.assertEquals("hij", connexionRequest.getPassword());
    Assert.assertEquals("0001012345", connexionRequest.getRecords().get(0).toString());
  }

  @Test
  public void parseRequestSingleCharLength2() {
    ConnexionRequest connexionRequest = new ConnexionRequest();
    Buffer req = Buffer.buffer();
    req.appendString(" UEabcde ");
    req.appendString("A");
    String key = "eyJzIjoiVjJBY2JsUUt3eSIsInQiOiJkaWt1IiwidSI6ImRpa3VfYWRtaW4ifQ==";
    req.appendByte((byte) (key.length() + 64));
    req.appendString(key);
    req.appendString("PChij 0001012345");
    connexionRequest.feedInput(req);
    Assert.assertEquals("abcde", connexionRequest.getUser());
    Assert.assertEquals(key, connexionRequest.getLocalUser());
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
  @Parameters({
      "0, A",
      "0, A3",
      "0, U3ab",
      "5, U3abc",
      "1, #U3ab",
      "7, #U3abc#",
      "7, #U3abc#0",
      "7, #U3abc#00010",
      "7, #U3abc#000101",
      "7, #U3abc#0001012",
      "7, #U3abc#00010123",
      "7, #U3abc#000101234",
      "17, #U3abc#0001012345",
      "0, GET#/#HTTP/1.1",
      "0, 0",
      "0, 00",
      "0, 000",
      "0, 0000",
      "5, 00005",
      "0, 00016123456",
      "0, 0001212345",
      "11, 00011123456",
      "11, 000111234560",
      "0, 1000012345",
      "0, U19abcde",
      "0, _",
      "0, U?abcde",
      "2, P09876",
      "7, P0U3abc"
  })
  public void testFeedInput(int expected, String input) {
    ConnexionRequest connexionRequest = new ConnexionRequest();
    String r = input.replace('#', ' ');
    Assert.assertEquals(expected, connexionRequest.feedInput(Buffer.buffer(r)));
  }

}
