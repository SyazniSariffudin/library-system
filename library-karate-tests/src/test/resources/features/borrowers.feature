Feature: Borrower registration
Background:
  * url baseUrl

Scenario: Normalization and duplicate email handling
  * def email = java.util.UUID.randomUUID().toString() + '@EXAMPLE.COM'
  Given path 'api/v1/borrowers'
  And request { name: '  Reader  ', email: '#("  " + email + "  ")' }
  When method post
  Then status 201
  And match response == { id: '#number', name: 'Reader', email: '#(email.toLowerCase())' }
  Given path 'api/v1/borrowers'
  And request { name: 'Other reader', email: '#(email.toLowerCase())' }
  When method post
  Then status 409
  And match response.code == 'EMAIL_ALREADY_REGISTERED'

Scenario Outline: Invalid borrower requests
  Given path 'api/v1/borrowers'
  And request <body>
  When method post
  Then status 400
  And match response.code == 'VALIDATION_FAILED'
  Examples:
    | body                                  |
    | { name: '', email: 'a@example.com' }   |
    | { name: 'Reader', email: 'invalid' }   |
    | { name: 'Reader' }                    |
