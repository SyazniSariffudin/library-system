Feature: Read physical copies across every page
Scenario:
  * def collectBooks =
    """
    function() {
      var copies = [];
      var page = 0;
      var totalPages = 1;
      while (page < totalPages) {
        var result = karate.call('classpath:helpers/book-page.feature', { pageIndex: page }).bookPage;
        copies = copies.concat(result.content);
        totalPages = result.totalPages;
        page++;
      }
      return copies;
    }
    """
  * def copies = collectBooks()
