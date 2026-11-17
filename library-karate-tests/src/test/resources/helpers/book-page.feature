Feature: Read one page of physical copies
Scenario:
  Given url baseUrl
  And path 'api/v1/books'
  And param page = pageIndex
  And param size = 50
  When method get
  Then status 200
  And match response.page == pageIndex
  And match response.size == 50
  * def bookPage = response
