Feature: Create a physical copy
Scenario:
  Given url baseUrl
  And path 'api/v1/books'
  And request { isbn: '9780134685991', title: 'Effective Java', author: 'Joshua Bloch' }
  When method post
  Then status 201
  And match response.id == '#number'
  * def book = response
