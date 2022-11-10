package org.folio.edge.connexion;

import org.junit.Assert;
import org.junit.Test;

public class TokenCacheTest {
  @Test
  public void testCapacity() {
    TokenCache tk = TokenCache.create(1);
    Assert.assertNull(tk.get("tenant", "user1"));
    tk.put("tenant", "user1", "v1", System.currentTimeMillis() + 10000);
    Assert.assertEquals("v1", tk.get("tenant", "user1"));
    tk.put("tenant", "user2", "v2", System.currentTimeMillis() + 10000);
    Assert.assertEquals("v2", tk.get("tenant", "user2"));
    Assert.assertNull(null, tk.get("tenant", "user1"));
  }

  @Test
  public void testExpiry() {
    TokenCache tk = TokenCache.create(1);
    Assert.assertNull(tk.get("tenant", "user1"));
    tk.put("tenant", "user1", "v1", System.currentTimeMillis() + 10000);
    Assert.assertEquals("v1", tk.get("tenant", "user1"));
    tk.put("tenant", "user2", "v2", System.currentTimeMillis() - 1);
    Assert.assertEquals("v1", tk.get("tenant", "user1"));
    Assert.assertNull(null, tk.get("tenant", "user2"));
  }
}
