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
    connexionRequest.parseRequest(Buffer.buffer("U5abcdeA2fgP3hij0001012345"));
    Assert.assertEquals("abcde", connexionRequest.getUser());
    Assert.assertEquals("fg", connexionRequest.getLocalUser());
    Assert.assertEquals("hij", connexionRequest.getPassword());
    Assert.assertEquals("0001012345", connexionRequest.getRecords().get(0).toString());
  }

  @Test
  public void parseRequest2() {
    ConnexionRequest connexionRequest = new ConnexionRequest();
    connexionRequest.parseRequest(Buffer.buffer(" UEabcde ABfg PChij 0001012345"));
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
    connexionRequest.parseRequest(Buffer.buffer("A" + byteLength + localUser + "00007æ" + "00008123"));
    Assert.assertEquals("testlib user bøf", connexionRequest.getLocalUser());
    Assert.assertEquals("00007æ", connexionRequest.getRecords().get(0).toString());
    Assert.assertEquals("00008123", connexionRequest.getRecords().get(1).toString());
  }

  @Test(expected = NumberFormatException.class)
  public void parseRequestBadNumberFormat1() {
    ConnexionRequest connexionRequest = new ConnexionRequest();
    connexionRequest.parseRequest(Buffer.buffer("U?abcde"));
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void parseRequestBadNumberFormat2() {
    ConnexionRequest connexionRequest = new ConnexionRequest();
    connexionRequest.parseRequest(Buffer.buffer("_"));
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void parseRequestBadRangeUser() {
    ConnexionRequest connexionRequest = new ConnexionRequest();
    connexionRequest.parseRequest(Buffer.buffer("U19abcde"));
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void parseRequestBadRangeMarc() {
    ConnexionRequest connexionRequest = new ConnexionRequest();
    connexionRequest.parseRequest(Buffer.buffer("1000012345"));
  }

  @Test
  public void testImportFromOCLC(TestContext context) {
    ConnexionRequest connexionRequest = new ConnexionRequest();
    connexionRequest.parse(Buffer.buffer("U5!bcdeA2fgP3hij0001012345"))
        .onComplete(context.asyncAssertSuccess(res -> {
          Assert.assertEquals("!bcde", connexionRequest.getUser());
          Assert.assertEquals("fg", connexionRequest.getLocalUser());
          Assert.assertEquals("hij", connexionRequest.getPassword());
          Assert.assertEquals("0001012345", connexionRequest.getRecords().get(0).toString());
        }));
  }

  @Test
  public void testImportFromOCLCOutOfBounds(TestContext context) {
    ConnexionRequest connexionRequest = new ConnexionRequest();
    connexionRequest.parse(Buffer.buffer("U19abcde"))
        .onComplete(context.asyncAssertFailure());
  }

  @Test
  public void testImportFromOCLCNumberFormat(TestContext context) {
    ConnexionRequest connexionRequest = new ConnexionRequest();
    connexionRequest.parse(Buffer.buffer("_"))
        .onComplete(context.asyncAssertFailure());
  }

}
