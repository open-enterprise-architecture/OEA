# Spec: openspec platform/ui-language
# Source material: requirements/user-stories/US-140-load-dynamic-labels.md

@US-140 @UC-04 @identity
Feature: Language of the user interface

  Every person chooses the language of the user interface; without a choice the
  language of the browser applies.

  Background:
    Given a running repository instance with the German labels of the starter package

  @AC1
  Scenario: Choosing German
    Given the person has chosen no language
    When the person chooses the language "de"
    Then the current principal reports the language "de"
