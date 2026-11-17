Feature: ISBN editions and physical copies
Background:
  * url baseUrl

Scenario: Same ISBN creates distinct copies and is normalized
  * def first = call read('classpath:helpers/book.feature')
  Given path 'api/v1/books'
  And request { isbn: '978-0-13-468599-1', title: 'Effective Java', author: 'Joshua Bloch' }
  When method post
  Then status 201
  And match response.id != first.book.id
  And match response.isbn == '9780134685991'
  * def second = response
  * def listing = call read('classpath:helpers/list-books.feature')
  * match listing.copies contains first.book
  * match listing.copies contains second

Scenario: Listing defaults to a page of twenty copies
  Given path 'api/v1/books'
  When method get
  Then status 200
  And match response.page == 0
  And match response.size == 20
  And match response.content == '#array'
  And assert response.content.length <= 20
  And assert response.totalElements >= response.content.length
  And assert response.totalPages == Math.ceil(response.totalElements / 20)

Scenario: Consecutive pages are ordered and a page beyond the end is empty
  * call read('classpath:helpers/book.feature')
  * call read('classpath:helpers/book.feature')
  Given path 'api/v1/books'
  And params { page: 0, size: 1 }
  When method get
  Then status 200
  And match response.content == '#[1]'
  * def firstId = response.content[0].id
  Given path 'api/v1/books'
  And params { page: 1, size: 1 }
  When method get
  Then status 200
  And match response.page == 1
  And match response.size == 1
  And match response.content == '#[1]'
  And assert response.content[0].id > firstId
  Given path 'api/v1/books'
  And params { page: 1000000, size: 50 }
  When method get
  Then status 200
  And match response.content == []
  And match response.page == 1000000
  And match response.size == 50
  And assert response.totalElements >= 2
  And assert response.totalPages == Math.ceil(response.totalElements / 50)

Scenario Outline: Invalid pagination is rejected
  Given path 'api/v1/books'
  And param <parameter> = '<value>'
  When method get
  Then status 400
  And match response.code == 'INVALID_REQUEST'
  Examples:
    | parameter | value      |
    | page      | -1         |
    | page      | invalid    |
    | page      | 2147483648 |
    | size      | 0          |
    | size      | -1         |
    | size      | 51         |
    | size      | invalid    |
    | size      | 2147483648 |

Scenario Outline: Conflicting ISBN metadata
  * call read('classpath:helpers/book.feature')
  Given path 'api/v1/books'
  And request { isbn: '9780134685991', title: '<title>', author: '<author>' }
  When method post
  Then status 409
  And match response.code == 'ISBN_METADATA_CONFLICT'
  Examples:
    | title          | author       |
    | Other title    | Joshua Bloch |
    | Effective Java | Other author |

Scenario: Same title and author with different ISBNs is allowed
  * call read('classpath:helpers/book.feature')
  Given path 'api/v1/books'
  And request { isbn: '9780132350884', title: 'Effective Java', author: 'Joshua Bloch' }
  When method post
  Then status 201
  And match response.isbn == '9780132350884'

Scenario: Invalid ISBN checksum
  Given path 'api/v1/books'
  And request { isbn: '9780134685992', title: 'Invalid book', author: 'Author' }
  When method post
  Then status 400
  And match response.code == 'VALIDATION_FAILED'
