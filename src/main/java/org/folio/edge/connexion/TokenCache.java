package org.folio.edge.connexion;

import org.folio.edge.connexion.impl.TokenCacheImpl;

public interface TokenCache {
  static TokenCache create(int capacity) {
    return new TokenCacheImpl(capacity);
  }

  void put(String tenant, String user, String value, long expires);

  String get(String tenant, String user);

}
