Feature: Create a unique borrower
Scenario:
  * def email = java.util.UUID.randomUUID().toString() + '@example.com'
  Given url baseUrl
  And path 'api/v1/borrowers'
  And request { name: 'Reader', email: '#(email)' }
  When method post
  Then status 201
  * def borrower = response
