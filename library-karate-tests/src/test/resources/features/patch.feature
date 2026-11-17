Feature: JSON Patch validation and atomicity
Background:
  * url baseUrl
  * def setup = call read('classpath:helpers/loan.feature')
  * def loan = setup.loan

Scenario: Ordered test and replace succeeds
  Given path 'api/v1/borrowers', loan.borrowerId, 'loans', loan.id
  And header Content-Type = 'application/json-patch+json'
  And request [{ op: 'test', path: '/status', value: 'BORROWED' }, { op: 'replace', path: '/status', value: 'RETURNED' }]
  When method patch
  Then status 200
  And match response.status == 'RETURNED'

Scenario: A later failing test rolls back the entire patch
  Given path 'api/v1/borrowers', loan.borrowerId, 'loans', loan.id
  And header Content-Type = 'application/json-patch+json'
  And request [{ op: 'replace', path: '/status', value: 'RETURNED' }, { op: 'test', path: '/status', value: 'BORROWED' }]
  When method patch
  Then status 409
  And match response.code == 'PATCH_TEST_FAILED'
  Given path 'api/v1/borrowers', loan.borrowerId, 'loans'
  And request { bookId: '#(loan.bookId)', dueAt: '#(java.time.Instant.now().plusSeconds(1209600).toString())' }
  When method post
  Then status 409

Scenario Outline: Reject malformed or forbidden operations
  Given path 'api/v1/borrowers', loan.borrowerId, 'loans', loan.id
  And header Content-Type = 'application/json-patch+json'
  And request <body>
  When method patch
  Then status <expected>
  And match response.code == '<code>'
  Examples:
    | body                                                       | expected | code               |
    | { status: 'RETURNED' }                                      | 400      | INVALID_JSON_PATCH |
    | [{ op: 'replace', path: '/status' }]                         | 400      | INVALID_JSON_PATCH |
    | [{ op: 'unknown', path: '/status', value: 'RETURNED' }]       | 400      | INVALID_JSON_PATCH |
    | [{ op: 'remove', path: '/status' }]                          | 422      | PATCH_NOT_ALLOWED  |
    | [{ op: 'replace', path: '/bookId', value: 'other' }]          | 422      | PATCH_NOT_ALLOWED  |
    | [{ op: 'replace', path: '/status', value: 'BORROWED' }]       | 422      | PATCH_NOT_ALLOWED  |

Scenario: Ordinary JSON content type is rejected
  Given path 'api/v1/borrowers', loan.borrowerId, 'loans', loan.id
  And header Content-Type = 'application/json'
  And request [{ op: 'replace', path: '/status', value: 'RETURNED' }]
  When method patch
  Then status 415
