package org.folio.edge.connexion;

import org.junit.Assert;
import org.junit.Test;

public class MainVerticleTest {

  @Test
  public void instanceMainVerticvle() {
    MainVerticle v = new MainVerticle();
    v.setMaxRecordSize(100);
    Assert.assertEquals(100, v.getMaxRecordSize());
  }
}
