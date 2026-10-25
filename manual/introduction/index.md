---
title: What OEA is
covers:
  screens: []
  stories: []
---

# What OEA is

OEA – Open Enterprise Architecture – is an open-source tool for enterprise architecture management. You use it to capture the components of your organization's architecture (applications, data, technology), describe them with the properties your organization needs, and look at them in tables and, later, in diagrams and dashboards.

## Editions

- **Community Edition**: open source under the GNU Affero General Public License 3.0 (AGPL-3.0)
- **Enterprise Edition**: the same core with additional functions under a proprietary license

This manual describes the Community Edition.

## Concepts

**Metamodel**
: The types of things your repository contains (*entity types*, for example *Application Component* or *Data Object*) and their properties. A new instance starts without entity types; you import a starter package or your own metamodel package.

**Entity**
: One element of the architecture, for example the application *CRM System*. Every entity belongs to an entity type and carries values for its properties. Every change creates a new version.

**Catalog**
: A reusable table view of the repository. A catalog shows the entities of one entity type as rows; you sort and page through them.

**Roles and permissions**
: What a person may do depends on the permissions of their roles. Roles are granted through the groups of your identity provider.

## What the current version offers

This manual describes the development state of OEA. It contains the functions that are implemented:

- installing an instance and setting up its first administrator
- connecting an identity provider (OpenID Connect) and granting roles through its groups
- importing the starter configuration and metamodel packages
- logging in with the identity provider or the local system admin account, with a second factor
- creating catalogs and viewing their entities
- writing entities in batch through the API

::: info Web interface
In this development state, the web interface runs in the development environment of the repository (`scripts/dev.sh`, see the [development guide](https://github.com/open-enterprise-architecture/OEA/blob/main/docs/development.md)). The operator setup of [Installation](../setup/installation) provides the API; all functions of this manual are also available through the [API](../api/).
:::
