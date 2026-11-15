---
title: API-Zugriff ohne Browser
covers:
  screens: []
  stories: [US-142, US-149]
---

# API-Zugriff ohne Browser

Skripte, CI-Pipelines und Integrationen rufen die API mit einem **Access-Token** auf statt mit einer Browser-Sitzung. Sie erhalten es mit der Client-ID und dem Secret eines [Service-Accounts](../administration/service-accounts): über den OAuth2-Client-Credentials-Grant. Alles, was ein solcher Client ändert, steht im Audit-Log unter der Client-ID seines Service-Accounts.

## Token anfordern

Sende Client-ID und Secret als HTTP-Basic-Authentisierung an `POST /oauth2/token`, mit `grant_type=client_credentials` im Formular-Body. Halte das Secret aus der Kommandozeile und der Shell-History heraus, indem du es zum Beispiel aus deinem Secret-Speicher in eine Umgebungsvariable liest:

```bash
OEA=https://oea.example.org/api/v1
CLIENT_ID=9b3c6e1a-…            # die ID des Service-Accounts
read -rs OEA_CLIENT_SECRET       # oder von deinem CI-System bereitgestellt

TOKEN=$(curl -sS --fail -u "$CLIENT_ID:$OEA_CLIENT_SECRET" \
  -d grant_type=client_credentials "$OEA/oauth2/token" | jq -r .access_token)
```

`curl -u` sendet die Zugangsdaten im Header `Authorization`. Die Antwort:

```json
{ "access_token": "oea_at_…", "token_type": "Bearer", "expires_in": 900 }
```

Das Token ist **900 Sekunden** (15 Minuten) gültig. Es gibt kein Refresh-Token: Fordere ein neues Token an, kurz bevor das alte abläuft oder wenn ein Request mit `401` beantwortet wird.

## Die API mit dem Token aufrufen

Sende das Token im Header `Authorization` jedes Requests:

```bash
curl -sS -H "Authorization: Bearer $TOKEN" "$OEA/me" | jq .principal
curl -sS -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d @entities.json "$OEA/entities/batch"
```

- Ein Request mit Token braucht kein CSRF-Token.
- OEA akzeptiert das Token nur im Header `Authorization`, nie als Query-Parameter oder Cookie.
- Ein Request, der gleichzeitig ein Token und ein Sitzungs-Cookie mitbringt, wird mit `401` abgelehnt.
- Das Token selbst enthält keine Informationen. Seine Berechtigungen sind die der Rollen des Service-Accounts zum Zeitpunkt jedes Requests; eine Rollenänderung gilt also ab dem nächsten Request.
- Ein Service-Account kann die Operationen des eigenen Kontos, etwa eine Passwortänderung, nicht nutzen und OEA nicht administrieren.

## Fehler

| Antwort | Bedeutung |
|---|---|
| `401` mit `{"error": "invalid_client"}` bei `/oauth2/token` | Client-ID oder Secret ist falsch, das Secret ist abgelaufen oder der Service-Account ist deaktiviert. OEA antwortet in jedem Fall gleich. |
| `400` mit `unsupported_grant_type` oder `invalid_request` | Unterstützt wird nur `grant_type=client_credentials`. |
| `401` mit `WWW-Authenticate: Bearer` | Das Token ist unbekannt, abgelaufen oder widerrufen. Fordere ein neues an. |
| `403` | Einer Rolle des Service-Accounts fehlt eine nötige Berechtigung. |
| `429` mit `Retry-After` | Zu viele fehlgeschlagene Token-Anfragen (5 in 15 Minuten pro Client-ID, 100 in 10 Minuten pro Adresse), oder der Token-Endpoint ist ausgelastet. Warte so viele Sekunden, wie `Retry-After` angibt. |

Das Rotieren des Secrets oder das Deaktivieren des Service-Accounts widerruft sofort alle seine Tokens.

## Was das Audit-Log festhält

- `token.issued` für jedes Token, mit der Client-ID; `token.denied` für eine abgelehnte Token-Anfrage, mit der gesendeten Client-ID als ungeprüft markiert; `token.revoked` für jedes Token, das eine Rotation oder Deaktivierung widerrufen hat.
- Jede Änderung mit einem Token trägt den Akteurtyp `service-account` und die Client-ID als `agentId`. Der Header `X-Agent-Id` wird nur als informatives Label gespeichert.
- Weder das Secret noch das Token erscheinen je im Audit-Log oder im Anwendungslog.
