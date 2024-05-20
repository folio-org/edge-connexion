# edge-connexion

Copyright (C) 2021-2022 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

## Introduction

[edge-connexion](https://github.com/folio-org/edge-connexion) is a FOLIO
module for importing records to a FOLIO instance via
[OCLC Connexion](https://www.oclc.org/en/connexion.html)

This module is based on
[edge-common](https://github.com/folio-org/edge-common).

All the system properties mentioned
[here](https://github.com/folio-org/edge-common#system-properties)
are relevant with the exception of `api_key_sources`.

This module starts a listener on the port `port` (8081 default) that serves
OCLC Connexion requests as well as basic HTTP to honor a GET on
`/admin/health`, so that productions systems can check for liveness of
this service.

A OCLC Connexion request can hold 4 pieces

* User
* Local User
* Password
* MARC record

Only "Local User" and "MARC record" is used by edge-connexion.

The form of the identity is controlled by the configuration `login_strategy`.
Two values are supported:

* `key`: "Local user" of the OCLC Connexion request is an API key.
Presumably this API key that was originally created with
edge-common's API key utility. In this mode the secret store of edge-common
is used to determine the password.
This is the default - if `login_strategy` is omitted.

* `full`: "Local user" consists of 3 tokens -  tenant, user and password -
separated by white space.

Whether using the `key` configuration with an
[institutional user](https://github.com/folio-org/edge-common#institutional-users)
as documented in the edge-common library, or using the `full`
configuration with a standard FOLIO user, the user must have the
`copycat.all` permission. In addition, due to an issue in
mod-source-record-manager
([MODSOURMAN-432](https://issues.folio.org/browse/MODSOURMAN-432)),
the user must have a `personal` property with at least a `lastName`.

The edge-connexion module uses the `copycat-imports` interface for MARC
record importing.
It always uses the OCLC WorldCat
[copycat profile](https://github.com/folio-org/mod-copycat/blob/master/src/main/resources/reference-data/profiles/oclc-worldcat.json).

## Testing and running

Figure out tenant, user and password to use.

Generate a key with something like:

    java -jar ../edge-common/target/edge-common-api-key-utils.jar -g -t diku -u diku_admin

Set up password in `ephemeral.properties`. Now run edge-connexion with

    java -Dokapi_url=http://localhost:9130 -Dport=8081 \
      -Dsecure_store_props=ephemeral.properties \
      -jar target/edge-connexion-fat.jar

You can import a test record with a test client that is bundled with the
edge-connection fat jar. It takes 4 arguments: host, port, key and filename.
Here, filename is supposedly a MARC record. A sample record is found in
`src/test/resources`.
Example of running the client against the edge-connexion server on port 8081:

    java -cp target/edge-connexion-fat.jar org.folio.edge.connexion.Client \
      localhost 8081 ey.. src/test/resources/manden.marc

You an also hack you way with netcat:

    java -jar ../edge-common/target/edge-common-api-key-utils.jar \
      -g -t diku -u diku_admin >key
    echo -n "A`wc -c <key`" >req
    cat key src/test/resources/manden.marc >>req
    nc -w5 localhost 8081 <req

Besides the Connexion protocol, the module also supports HTTP GET on
`/admin/health`. This is useful, if you want ot check that it's alive.
Example:

    curl http://localhost:8081/admin/health

## Configuration

| Property                  | Default             | Description                                                               |
|---------------------------|---------------------|---------------------------------------------------------------------------|
| `port`                    | `8081`              | Server port to listen on                                                  |
| `okapi_url`               | *required*          | Where to find Okapi (URL)                                                 |
| `secure_store`            | `Ephemeral`         | Type of secure store to use.  Valid: `Ephemeral`, `AwsSsm`, `Vault`       |
| `secure_store_props`      | `NA`                | Path to a properties file specifying secure store configuration           |
| `token_cache_ttl_ms`      | `3600000`           | How long to cache JWTs, in milliseconds (ms)                              |
| `null_token_cache_ttl_ms` | `30000`             | How long to cache login failure (null JWTs), in milliseconds (ms)         |
| `token_cache_capacity`    | `100`               | Max token cache size                                                      |
| `log_level`               | `INFO`              | Log4j Log Level                                                           |
| `request_timeout_ms`      | `30000`             | Request Timeout                                                           |
| `ssl_enabled`             | `false`             | Set whether SSL/TLS is enabled for Vertx Http Server                      |
| `keystore_type`           | `NA`                | Set the key store type                                                    |
| `keystore_provider`       | `NA`                | Set the provider name of the key store                                    |
| `keystore_path`           | `NA`                | Set the path to the key store file                                        |
| `keystore_password`       | `NA`                | Set the password for the key store                                        |
| `key_alias`               | `NA`                | Optional param that points to a specific key within the key store         |
| `key_alias_password`      | `NA`                | Optional param that points to a password of `key_alias` if it protected   |
| `api_key_sources`         | `PARAM,HEADER,PATH` | Defines the sources (order of precendence) of the API key.                |


## Additional information

Other FOLIO Developer documentation is at
[dev.folio.org](https://dev.folio.org/)

### Issue tracker

See project [EDGCONX](https://issues.folio.org/browse/EDGCONX)
at the [FOLIO issue tracker](https://dev.folio.org/guidelines/issue-tracker).

### ModuleDescriptor

See the template [ModuleDescriptor-template.json](descriptors/ModuleDescriptor-template.json)
for the interfaces that this module requires and provides, the permissions,
and the additional module metadata.

This module does not include a launch descriptor as it is an edge module.

### Code analysis

[SonarQube analysis](https://sonarcloud.io/dashboard?id=org.folio%3Aedge-connexion).

### Download and configuration

The built artifacts for this module are available.
See [configuration](https://dev.folio.org/download/artifacts) for
repository access.
