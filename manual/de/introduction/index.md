---
title: Was OEA ist
covers:
  screens: []
  stories: []
---

# Was OEA ist

OEA – Open Enterprise Architecture – ist ein Open-Source-Werkzeug für das Enterprise Architecture Management. Damit erfasst du die Bestandteile der Architektur deiner Organisation (Anwendungen, Daten, Technologie), beschreibst sie mit den Eigenschaften, die deine Organisation braucht, und betrachtest sie in Tabellen und später in Diagrammen und Dashboards.

## Editionen

- **Community Edition**: Open Source unter der GNU Affero General Public License 3.0 (AGPL-3.0)
- **Enterprise Edition**: derselbe Kern mit zusätzlichen Funktionen unter einer proprietären Lizenz

Dieses Handbuch beschreibt die Community Edition.

## Begriffe

**Metamodell**
: Die Arten von Dingen, die dein Repository enthält (*Entitätstypen*, zum Beispiel *Application Component* oder *Data Object*), und ihre Eigenschaften. Eine neue Instanz startet ohne Entitätstypen; du importierst ein Starterpaket oder ein eigenes Metamodell-Paket.

**Entität**
: Ein Element der Architektur, zum Beispiel die Anwendung *CRM System*. Jede Entität gehört zu einem Entitätstyp und trägt Werte für dessen Eigenschaften. Jede Änderung erzeugt eine neue Version.

**Katalog**
: Eine wiederverwendbare Tabellenansicht des Repositorys. Ein Katalog zeigt die Entitäten eines Entitätstyps als Zeilen; du sortierst und blätterst durch sie.

**Rollen und Berechtigungen**
: Was eine Person tun darf, hängt von den Berechtigungen ihrer Rollen ab. Rollen werden über die Gruppen deines Identity-Providers vergeben.

## Was die aktuelle Version bietet

Dieses Handbuch beschreibt den Entwicklungsstand von OEA. Es enthält die Funktionen, die implementiert sind:

- eine Instanz installieren und ihren ersten Administrator einrichten
- einen Identity-Provider (OpenID Connect) anbinden und Rollen über seine Gruppen vergeben
- die Starterkonfiguration und Metamodell-Pakete importieren
- dich über den Identity-Provider oder mit dem lokalen Systemadmin-Konto anmelden, mit zweitem Faktor
- Kataloge anlegen und ihre Entitäten ansehen
- Entitäten im Batch über die API schreiben

::: info Weboberfläche
In diesem Entwicklungsstand läuft die Weboberfläche in der Entwicklungsumgebung des Repositorys (`scripts/dev.sh`, siehe den [Development Guide](https://github.com/open-enterprise-architecture/OEA/blob/main/docs/development.md)). Das Betreiber-Setup aus [Installation](../setup/installation) stellt die API bereit; alle Funktionen dieses Handbuchs stehen auch über die [API](../api/) zur Verfügung.
:::
