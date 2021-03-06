package org.folio.edge.connexion;

import io.vertx.core.buffer.Buffer;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class ImporterTest {

  @Test
  public void parseRequest1() {
    Importer importer = new Importer();
    importer.parseRequest(Buffer.buffer("U5abcdeA2fgP3hij0001012345"));
    Assert.assertEquals("abcde", importer.getUser());
    Assert.assertEquals("fg", importer.getLocalUser());
    Assert.assertEquals("hij", importer.getPassword());
    Assert.assertEquals("0001012345", importer.getRecords().get(0).toString());
  }

  @Test
  public void parseRequest2() {
    Importer importer = new Importer();
    importer.parseRequest(Buffer.buffer(" UEabcde ABfg PChij 0001012345"));
    Assert.assertEquals("abcde", importer.getUser());
    Assert.assertEquals("fg", importer.getLocalUser());
    Assert.assertEquals("hij", importer.getPassword());
    Assert.assertEquals("0001012345", importer.getRecords().get(0).toString());
  }

  @Test(expected = NumberFormatException.class)
  public void parseRequestBadNumberFormat1() {
    Importer importer = new Importer();
    importer.parseRequest(Buffer.buffer("U?abcde"));
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void parseRequestBadNumberFormat2() {
    Importer importer = new Importer();
    importer.parseRequest(Buffer.buffer("_"));
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void parseRequestBadRangeUser() {
    Importer importer = new Importer();
    importer.parseRequest(Buffer.buffer("U19abcde"));
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void parseRequestBadRangeMarc() {
    Importer importer = new Importer();
    importer.parseRequest(Buffer.buffer("1000012345"));
  }

  @Test
  public void testImportFromOCLC(TestContext context) {
    Importer importer = new Importer();
    importer.importRequest(Buffer.buffer("U5!bcdeA2fgP3hij0001012345").appendByte((byte) 0))
        .onComplete(context.asyncAssertSuccess(res -> {
          Assert.assertEquals("!bcde", importer.getUser());
          Assert.assertEquals("fg", importer.getLocalUser());
          Assert.assertEquals("hij", importer.getPassword());
          Assert.assertEquals("0001012345", importer.getRecords().get(0).toString());
        }));
  }

  @Test
  public void testImportFromOCLCOutOfBounds(TestContext context) {
    Importer importer = new Importer();
    importer.importRequest(Buffer.buffer("U19abcde"))
        .onComplete(context.asyncAssertFailure());
  }

  @Test
  public void testImportFromOCLCNumberFormat(TestContext context) {
    Importer importer = new Importer();
    importer.importRequest(Buffer.buffer("_"))
        .onComplete(context.asyncAssertFailure());
  }

}
