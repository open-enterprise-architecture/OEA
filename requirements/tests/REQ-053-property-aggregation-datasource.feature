# Derived from: requirements/req/req-053-*.md
# V-model system test specification – created 2026-06-30

@functional @should @UC-07
Feature: REQ-053 – Configure and validate PropertyAggregation DataSource

  The system MUST validate a `PropertyAggregationDataSource`
  configuration for `kpi` and `chart` widgets and resolve it during
  data computation (REQ-055) as an aggregation query on the entity
  store. The DataSource MUST support the following parameters:
  `entityType`, `propertyName`, `aggregationFn`
  (sum|count|avg|min|max), optional `groupBy`, and optional
  `filters`.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Valid int aggregation, no groupBy – EntityType `plateau` with Propert...
    Given EntityType `plateau` with PropertyDefinition `investitionskostenPrognose` (kind=int); 3 plateau instances with values 5000, 12000, 25000
    When  DataSource configured with entityType=plateau, propertyName=investitionskostenPrognose, aggregationFn=sum
    Then  data computation returns `{ "value": 42000 }`

  @AC2
  Scenario: AC2 – Valid count aggregation with groupBy – EntityType `application-compo...
    Given EntityType `application-component`; 20 entities, of which 12 domain=finance, 8 domain=hr
    When  DataSource with propertyName=id, aggregationFn=count, groupBy=architectureDomainId
    Then  series=[{group: "finance", value: 12}, {group: "hr", value: 8}]

  @AC3
  Scenario: AC3 – Invalid propertyName for sum – PropertyDefinition `name` (kind=varchar) on EntityType `plateau`
    Given PropertyDefinition `name` (kind=varchar) on EntityType `plateau`
    When  create widget with aggregationFn=sum, propertyName=name
    Then  HTTP 422 "sum/avg/min/max require propertyName with dataType.kind=int"

  @AC4
  Scenario: AC4 – Invalid entityType – create widget with entityType=non-existent-type
    Given the precondition according to the requirement is met
    When  create widget with entityType=non-existent-type
    Then  HTTP 422 "EntityType 'non-existent-type' not found in MetamodelConfiguration"

  @AC5
  Scenario: AC5 – No entities present – EntityType `plateau` has no instances in the repository
    Given EntityType `plateau` has no instances in the repository
    When  data computation with aggregationFn=sum
    Then  `{ "value": null }` (no error; null means "no data")

  @AC6
  Scenario: AC6 – groupBy=architectureLayerId – 3 EntityTypes each assigned to one laye...
    Given 3 EntityTypes each assigned to one layer; 50 entities distributed
    When  propertyName=id, aggregationFn=count, groupBy=architectureLayerId
    Then  series with entries per layer ID and their entity count
