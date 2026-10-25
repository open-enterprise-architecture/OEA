---
title: Die API verwenden
covers:
  screens: []
  stories: []
---

# Die API verwenden

Jede Funktion von OEA steht über die REST-API zur Verfügung. Die API ist im Vertrag `api/openapi.yaml` (OpenAPI 3.1) beschrieben; die Seiten dieses Kapitels werden daraus erzeugt und listen nur die Operationen, die diese Version implementiert. Die Beschreibungen der Operationen stammen aus dem englischen Vertrag.

## Basis-URL

Alle Operationen liegen unter `/api/v1` der Instanz, zum Beispiel `https://oea.example.org/api/v1/health`.

## Authentisierung

Es gibt keinen anonymen Zugriff, abgesehen von wenigen öffentlichen Operationen (Health, Setup, Anmeldung). Die Weboberfläche verwendet eine **Sitzung**:

1. Melde dich mit `POST /auth/local/login` (lokales Systemadmin-Konto) oder über einen Identity-Provider an (`GET /auth/oidc/{providerId}/start`).
2. Die Sitzung ist das Cookie `__Host-OEA_SESSION`. Die Antwort der Anmeldung enthält ein `csrfToken`.
3. Sende das Token im Header `X-XSRF-TOKEN` mit jedem ändernden Request (`POST`, `PUT`, `PATCH`, `DELETE`).
4. Beende die Sitzung mit `POST /auth/logout`.

[Für die Administration über die API anmelden](../administration/#api-login) zeigt das mit `curl`.

## Berechtigungen

Jede Operation nennt den Zugriff, den sie verlangt:

- **öffentlich**: ohne Anmeldung
- **jeder angemeldete Principal**: jede Sitzung; Prüfungen am Objekt selbst können dazukommen
- **Berechtigung**: die genannten Berechtigungen sind nötig; *eine der Berechtigungen* bedeutet, dass eine davon genügt; *zusätzlich …, wenn: …* nennt Berechtigungen, die ein bestimmter Request zusätzlich braucht

Berechtigungen kommen aus Rollen, siehe [Rollen und Berechtigungen](../administration/roles-and-permissions).

## Fehler

Fehler werden als RFC 9457 Problem Details beantwortet (`application/problem+json`). Das Feld `type` benennt das Problem als `urn:oea:problem:<name>`, zum Beispiel `urn:oea:problem:validation-failed`; die Referenz listet die Problem-Typen pro Antwort.

- `401`: nicht angemeldet, oder die Sitzung ist beendet
- `403`: eine nötige Berechtigung fehlt; die Antwort verrät nicht, welche
- `404`: das Objekt gibt es nicht – oder du darfst es nicht sehen
- `413`: der Request-Body ist zu groß (siehe [Größenlimits für Requests](../administration/operations#request-size-limits))
- `422`: der Request ist wohlgeformt, aber nicht gültig

## Referenz

Die Referenz hat eine Seite pro Bereich der API; öffne sie in der Seitenleiste.
