---
title: Plateaus
covers:
  screens: [SCR-065]
  stories: [US-084, US-085, US-089, US-155]
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

## Über die API

`GET /api/v1/plateaus` listet die Plateaus, `POST /api/v1/plateaus` legt eines an, und `GET` und `PUT /api/v1/plateaus/{id}` lesen und bearbeiten eines. Anlegen und Bearbeiten werden als `plateau.created` und `plateau.updated` protokolliert.
