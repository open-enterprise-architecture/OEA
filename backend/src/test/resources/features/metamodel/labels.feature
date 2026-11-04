# Spec: openspec metamodel/labels, metamodel/package-import
# Source material: requirements/user-stories/US-140-load-dynamic-labels.md, REQ-152

@US-140 @UC-04 @metamodel
Feature: Translated labels of the metamodel

  Every logged-in person reads the labels of a locale as one bundle. Without a
  translation a key carries the name in the corporate language. The bundle is
  cached by ETag: an unchanged state is answered with 304, a change gives a new
  ETag.

  Background:
    Given a running repository instance with the German labels of the starter package

  @AC1
  Scenario: German labels after the starter import
    When a person reads the German labels
    Then "Application Component" is labelled "Anwendungskomponente"
    And the value "planned" of "Status" is labelled "Geplant"

  @REQ-152 @AC1
  Scenario: Unchanged labels
    Given the person has read the German labels
    When the person reads the German labels again with the received ETag
    Then the labels are answered with 304 and no body

  @REQ-152 @AC2
  Scenario: Labels after a change
    Given the person has read the German labels
    When the metamodel administrator labels "Application Component" in German as "Applikation"
    And the person reads the German labels again with the received ETag
    Then the labels are answered with 200 and a new ETag
    And "Application Component" is labelled "Applikation"
