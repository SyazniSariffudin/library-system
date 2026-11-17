Feature: Create a fresh active loan
Scenario:
  * def borrower = call read('classpath:helpers/borrower.feature')
  * def copy = call read('classpath:helpers/book.feature')
  Given url baseUrl
  And path 'api/v1/borrowers', borrower.borrower.id, 'loans'
  And request { bookId: '#(copy.book.id)', dueAt: '#(java.time.Instant.now().plusSeconds(1209600).toString())' }
  When method post
  Then status 201
  And match response.id == '#number'
  And match response.borrowerId == '#number'
  And match response.bookId == '#number'
  And match response.dueAt == '#string'
  And match response.overdue == false
  * def loan = response
