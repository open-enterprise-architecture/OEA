---
title: Plateaus
covers:
  screens: [SCR-065, SCR-066]
  stories: [US-084, US-085, US-086, US-087, US-088, US-089, US-155, US-157]
---

# Plateaus

Ein Plateau ist ein benannter, stabiler Zustand deiner Architektur. Die **Baseline** ist die Architektur, die heute im Betrieb ist. **Ziel-** und **Übergangsplateaus** beschreiben geplante Zustände: einen Zielzustand und Zwischenstände auf dem Weg dorthin. [Lösungen](./solutions) können auf ein Plateau hinarbeiten.

Für den Plateaumodus gibt es keinen Schalter: Er beginnt, wenn du die erste Baseline anlegst. Lösungen ohne Plateau arbeiten weiter wie bisher.

Zum Anlegen und Bearbeiten von Plateaus brauchst du die Berechtigung `plateau:write` (zum Beispiel die Rolle `enterprise-architect`). Um sie zu sehen, reicht `plateau:write`, `plateau:go-live` oder `solution:write` (siehe [Rollen und Berechtigungen](../administration/roles-and-permissions)).

## Die Seite Plateaus

Wähle in der Kopfzeile *Plateaus*. Die Seite zeigt:

- die **Baseline**, hervorgehoben, mit dem Datum, seit dem sie gilt;
- die **Ziel- und Übergangsplateaus**, nach geplantem Datum, jeweils mit Vorgänger, Basis und der Zahl der Lösungen, die darauf hinarbeiten. Gibt es keines, steht dort „Noch kein Zielplateau geplant“;
- die **realisierten Plateaus**, eingeklappt, das neueste zuerst. Ein Plateau wird realisiert, wenn sein Nachfolger live geht.

## Die initiale Baseline anlegen

Ohne Baseline steht auf der Seite „Es gibt noch kein Baseline-Plateau“. Wähle *Initiale Baseline anlegen*, gib einen Namen wie „Baseline 2026-Q2“ und optional eine Beschreibung und das Datum des Stands ein und wähle *Plateau anlegen*. Es gibt immer nur eine Baseline.

## Ein Ziel- oder Übergangsplateau anlegen

1. Wähle *Neues Plateau*.
2. Fülle den Dialog aus:

   | Feld | Bedeutung |
   |---|---|
   | Name | Pflicht, bis 255 Zeichen, eindeutig unabhängig von Gross- und Kleinschreibung |
   | Art | *Ziel* oder *Übergang* |
   | Beschreibung | optional, bis 2000 Zeichen |
   | Gültig ab | optional: das geplante Go-live-Datum |
   | Vorgänger | das Plateau, auf das dieses folgt; die Baseline ist vorausgewählt. Ein realisiertes Plateau kann kein Vorgänger sein |
   | Basis | wovon die Lösungen dieses Plateaus ausgehen (siehe unten) |

3. Wähle *Plateau anlegen*.

### Die Basis {#basis}

Die Basis bestimmt, was eine Lösung, die auf das Plateau hinarbeitet, als ihre Ausgangslage sieht:

- **Aktueller Katalog:** der Katalog, wie er jetzt ist.
- **Inhalt des Vorgängers:** das Vorgängerplateau mit den Änderungen seiner aktiven Lösungen. Eine Entität, die eine Lösung des Vorgängers ausser Betrieb nimmt, ist mit *Wird abgelöst durch …* markiert, eine neue Entität, die sie vorschlägt, mit *Vorgeschlagen von …*, und geänderte Namen und Werte zeigen ihren geplanten Wert. Eine vorgeschlagene Entität kann noch nicht geändert oder ausser Betrieb genommen werden, weil sie vor ihrem Go-live nicht existiert. Hat der Vorgänger selbst die Basis „Inhalt des Vorgängers“, zählt auch dessen Vorgänger.

Ändern zwei Lösungen desselben Plateaus dasselbe Feld, zählt die zuletzt gemachte Änderung.

## Ein Plateau bearbeiten

Wähle *Bearbeiten* neben einem Plateau, um Name, Beschreibung, Datum, Vorgänger oder Basis zu ändern. Die Art ändert sich nur durch das Go-live. Der Vorgänger ist fest, sobald eine Lösung auf das Plateau hinarbeitet.

Ein realisiertes Plateau ist schreibgeschützt: Es hat kein *Bearbeiten*, und die API antwortet „Realized plateau is read-only“.

## Ein Plateau live schalten {#go-live}

Ist der geplante Zustand erreicht, geht das Plateau live: Es wird die neue Baseline, die alte Baseline wird realisiert, und die Änderungen seiner Lösungen kommen in den Katalog. Du brauchst die Berechtigung `plateau:go-live` (zum Beispiel die Rolle `enterprise-architect`).

Nur das Plateau, das direkt auf die Baseline folgt, kann live gehen; es zeigt *Live schalten*. Der Dialog *Plateau live schalten* zeigt:

- welches Plateau die Baseline wird und welches realisiert wird;
- wie viele Entitäten angelegt, geändert und abgelöst werden;
- die Lösungen, die mit dem Plateau live gehen;
- Warnungen, die das Go-live nicht stoppen: noch nicht genehmigte Lösungen und die offenen Punkte, die das Go-live hinterlässt.

Gib den genauen Namen des Plateaus ein und wähle *Go-live bestätigen*. Das Go-live kann nicht rückgängig gemacht werden.

Alle aktiven Lösungen des Plateaus gehen in einem Schritt live, in der Reihenfolge, in der ihre Änderungen zuletzt bearbeitet wurden, jede so, wie eine einzelne [Lösung live geht](./solutions#go-live). Wurde ein Feld, das eine Lösung ändert, inzwischen geändert, im Katalog oder durch eine Lösung, die vor ihr live geht, listet der Dialog den Konflikt bei dieser Lösung; *Auflösen* öffnet *Konflikte auflösen*. Solange nicht jeder Konflikt aufgelöst ist, geht nichts live. Schlägt beim Go-live etwas fehl, ändert sich nichts.

Nach dem Go-live:

- ist das Plateau ab heute die Baseline, und die alte Baseline ist bis heute realisiert und schreibgeschützt;
- sind die Lösungen des Plateaus *Umgesetzt*, und abgelöste Entitäten sind aus dem Katalog gelöscht;
- folgen Plateaus, die auf die alte Baseline folgten, nun der neuen, und ihre Lösungen starten von ihr.

## Über die API

`GET /api/v1/plateaus` listet die Plateaus, `POST /api/v1/plateaus` legt eines an, und `GET` und `PUT /api/v1/plateaus/{id}` lesen und bearbeiten eines. `GET /api/v1/plateaus/{id}/go-live` zeigt, was das Go-live tun würde, und `POST` mit dem Namen des Plateaus schaltet es live. Anlegen, Bearbeiten und Go-live werden als `plateau.created`, `plateau.updated` und `plateau.went-live` protokolliert.
