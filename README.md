# edge-connexion

Copyright (C) 2021 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

## Introduction

[edge-connexion](https://github.com/folio-org/edge-connexion) is a FOLIO
module for importing records to a FOLIO instance via
[OCLC Connexion](https://www.oclc.org/en/connexion.html)

This module is based on [edge-common](https://github.com/folio-org/edge-common).

All the system properties mentioned [here](https://github.com/folio-org/edge-common#system-properties)
are relevant with the exception of `api_key_sources`.

This module starts a listener on the `port` (8081 default) that serves OCLC Connexion requests
as well as basic HTTP to honor a GET on `/admin/health`, so that productions systems can check
for liveness of this service.

The form of the identity is controlled by the configuration `login_strategy`.
Two values are supported:

* `key` "Local user" is an API key. Presumably this API key that was originally created with
edge-common's API key utility. In this mode the secret store of edge-common is used to
determine the password. This is the default - if `login_strategy` is not omitted.

* `full`. The identity is a 3 token field consisting of tenant, user and password, separated by
white space.

## Additional information

Other FOLIO Developer documentation is at [dev.folio.org](https://dev.folio.org/)

### Issue tracker

See project [EDGCONX](https://issues.folio.org/browse/EDGCONX)
at the [FOLIO issue tracker](https://dev.folio.org/guidelines/issue-tracker).

### ModuleDescriptor

See the template [ModuleDescriptor-template.json](descriptors/ModuleDescriptor-template.json)
for the interfaces that this module requires and provides, the permissions, and the
additional module metadata.

This module does not include a launch descriptor as it is an edge module.

### Code analysis

[SonarQube analysis](https://sonarcloud.io/dashboard?id=org.folio%3Aedge-connexion).

### Download and configuration

The built artifacts for this module are available.
See [configuration](https://dev.folio.org/download/artifacts) for
repository access.
