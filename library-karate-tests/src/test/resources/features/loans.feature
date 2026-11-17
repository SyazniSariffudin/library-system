Feature: Borrowing and returning physical copies
Background:
  * url baseUrl

Scenario: Return, borrow again, and replay an old return safely
  * def setup = call read('classpath:helpers/loan.feature')
  * def loan = setup.loan
  * def returnPatch = [{ op: 'replace', path: '/status', value: 'RETURNED' }]
  Given path 'api/v1/borrowers', loan.borrowerId, 'loans', loan.id
  And header Content-Type = 'application/json-patch+json'
  And request returnPatch
  When method patch
  Then status 200
  And match response.status == 'RETURNED'
  And match response.returnedAt == '#string'
  * def returnedAt = response.returnedAt
  Given path 'api/v1/borrowers', loan.borrowerId, 'loans'
  And request { bookId: '#(loan.bookId)', dueAt: '#(java.time.Instant.now().plusSeconds(1209600).toString())' }
  When method post
  Then status 201
  And match response.id != loan.id
  Given path 'api/v1/borrowers', loan.borrowerId, 'loans', loan.id
  And header Content-Type = 'application/json-patch+json'
  And request returnPatch
  When method patch
  Then status 200
  And match response.returnedAt == returnedAt
  * def listing = call read('classpath:helpers/list-books.feature')
  * def copies = listing.copies.filter(x => x.id == loan.bookId)
  And match copies[0].available == false

Scenario: Two borrowers can borrow separate copies sharing an ISBN
  * def first = call read('classpath:helpers/loan.feature')
  * def second = call read('classpath:helpers/loan.feature')
  * match first.loan.bookId != second.loan.bookId

Scenario: An active loan prevents repeat borrowing
  * def setup = call read('classpath:helpers/loan.feature')
  Given path 'api/v1/borrowers', setup.loan.borrowerId, 'loans'
  And request { bookId: '#(setup.loan.bookId)', dueAt: '#(java.time.Instant.now().plusSeconds(1209600).toString())' }
  When method post
  Then status 409
  And match response.code == 'BOOK_ALREADY_BORROWED'

Scenario: Another borrower cannot return the loan
  * def setup = call read('classpath:helpers/loan.feature')
  * def other = call read('classpath:helpers/borrower.feature')
  Given path 'api/v1/borrowers', other.borrower.id, 'loans', setup.loan.id
  And header Content-Type = 'application/json-patch+json'
  And request [{ op: 'replace', path: '/status', value: 'RETURNED' }]
  When method patch
  Then status 404

Scenario: Missing book returns 404
  * def reader = call read('classpath:helpers/borrower.feature')
  Given path 'api/v1/borrowers', reader.borrower.id, 'loans'
  And request { bookId: 9999999999, dueAt: '#(java.time.Instant.now().plusSeconds(1209600).toString())' }
  When method post
  Then status 404

Scenario: Concurrent requests for the same copy have one winner
  * def copy = call read('classpath:helpers/book.feature')
  * def first = call read('classpath:helpers/borrower.feature')
  * def second = call read('classpath:helpers/borrower.feature')
  * def ConcurrentBorrow = Java.type('dev.syaz.library.acceptance.ConcurrentBorrow')
  * def statuses = ConcurrentBorrow.run(baseUrl, first.borrower.id, second.borrower.id, copy.book.id)
  * match statuses == [201, 409]

Scenario Outline: Borrowing requires a future expected return timestamp
  * def copy = call read('classpath:helpers/book.feature')
  * def reader = call read('classpath:helpers/borrower.feature')
  Given path 'api/v1/borrowers', reader.borrower.id, 'loans'
  And request <body>
  When method post
  Then status 400
  And match response.code == '<code>'

  Examples:
    | body                                                                | code              |
    | { bookId: '#(copy.book.id)' }                                        | VALIDATION_FAILED |
    | { bookId: '#(copy.book.id)', dueAt: null }                           | VALIDATION_FAILED |
    | { bookId: '#(copy.book.id)', dueAt: '2000-01-01T00:00:00Z' }          | INVALID_DUE_AT    |
    | { bookId: '#(copy.book.id)', dueAt: 'not-a-timestamp' }               | INVALID_REQUEST   |
