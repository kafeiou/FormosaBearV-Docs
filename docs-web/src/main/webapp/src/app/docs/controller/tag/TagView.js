'use strict';

/**
 * Tag view controller (browsing documents and files under a selected tag).
 */
angular.module('docs').controller('TagView', function($scope, $stateParams, $state, $timeout, Restangular) {
  $scope.sortColumn = 3;
  $scope.asc = false;
  $scope.offset = 0;
  $scope.currentPage = 1;
  $scope.limit = _.isUndefined(localStorage.documentsPageSize) ? '10' : localStorage.documentsPageSize;
  $scope.filterText = '';
  $scope.paginationShown = true;
  $scope.loadingDocuments = true;

  var timeoutPromise;

  // Load tag detail
  Restangular.one('tag', $stateParams.id).get().then(function(data) {
    $scope.tag = data;
    if ($scope.expandAncestors) {
      $scope.expandAncestors(data.id);
    }
    $scope.loadDocuments();
  });

  /**
   * Get breadcrumb trail for the current tag.
   */
  $scope.getTagBreadcrumb = function() {
    if (!$scope.tag || !$scope.tags || !$scope.tags.length) {
      return $scope.tag ? [$scope.tag] : [];
    }
    var trail = [];
    var current = _.findWhere($scope.tags, { id: $scope.tag.id }) || $scope.tag;
    trail.push(current);
    while (current && current.parent) {
      current = _.findWhere($scope.tags, { id: current.parent });
      if (current) {
        trail.push(current);
      }
    }
    return trail.reverse();
  };

  /**
   * Load documents & attached files for the current tag.
   */
  $scope.pageDocuments = function() {
    if (!$scope.tag || !$scope.tag.name) {
      return;
    }
    $scope.loadingDocuments = true;
    var searchQuery = 'tag:' + $scope.tag.name;
    if ($scope.filterText && $scope.filterText.trim().length > 0) {
      searchQuery += ' ' + $scope.filterText.trim();
    }
    Restangular.one('document/list')
      .get({
        offset: $scope.offset,
        limit: $scope.limit,
        sort_column: $scope.sortColumn,
        asc: $scope.asc,
        search: searchQuery,
        files: true
      })
      .then(function(data) {
        $scope.documents = data.documents;
        $scope.totalDocuments = data.total;
        $scope.loadingDocuments = false;
      }, function() {
        $scope.loadingDocuments = false;
      });
  };

  /**
   * Reload from first page.
   */
  $scope.loadDocuments = function() {
    $scope.offset = 0;
    $scope.currentPage = 1;
    $scope.pageDocuments();
  };

  /**
   * Watch page change.
   */
  $scope.$watch('currentPage', function(prev, next) {
    if (prev === next) {
      return;
    }
    $scope.offset = ($scope.currentPage - 1) * parseInt($scope.limit);
    $scope.pageDocuments();
  });

  /**
   * Watch page size change.
   */
  $scope.$watch('limit', function(next, prev) {
    localStorage.documentsPageSize = next;
    if (next === prev) {
      return;
    }
    $scope.loadDocuments();
  });

  /**
   * Watch filter keyword change with debounce.
   */
  $scope.$watch('filterText', function(next, prev) {
    if (next === prev) {
      return;
    }
    if (timeoutPromise) {
      $timeout.cancel(timeoutPromise);
    }
    timeoutPromise = $timeout(function() {
      $scope.loadDocuments();
    }, 250);
  });

  /**
   * Sort documents by column (1: title, 3: creation date).
   */
  $scope.sortDocuments = function(sortColumn) {
    if (sortColumn === $scope.sortColumn) {
      $scope.asc = !$scope.asc;
    } else {
      $scope.asc = sortColumn === 1;
    }
    $scope.sortColumn = sortColumn;
    $scope.loadDocuments();
  };

  /**
   * Open file preview modal directly in the current tag view.
   */
  $scope.openFile = function(document, file, $event) {
    if ($event) {
      $event.stopPropagation();
    }
    $state.go('tag.view.file', {
      id: $stateParams.id,
      docId: document.id,
      fileId: file.id
    });
  };

  /**
   * Navigate to full document view.
   */
  $scope.viewDocument = function(docId) {
    $state.go('document.view', { id: docId });
  };
});
