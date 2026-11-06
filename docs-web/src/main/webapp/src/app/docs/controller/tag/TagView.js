'use strict';

/**
 * Tag view controller (browsing documents and files under a selected tag).
 */
angular.module('docs').controller('TagView', function($scope, $rootScope, $stateParams, $state, $timeout, Restangular) {
  $scope.offset = 0;
  $scope.currentPage = 1;
  $scope.limit = _.isUndefined(localStorage.tagDocumentsPageSize) ? '30' : localStorage.tagDocumentsPageSize;
  $scope.filterText = '';
  $scope.paginationShown = true;
  $scope.loadingDocuments = true;
  $scope.savingOrder = false;
  $scope.expandedDocs = {};

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
    if (!$scope.tag || !$scope.tag.id) {
      return;
    }
    $scope.loadingDocuments = true;
    var params = {
      tag_id: $scope.tag.id,
      offset: $scope.offset,
      limit: $scope.limit,
      files: true
    };
    if ($scope.filterText && $scope.filterText.trim().length > 0) {
      params.search = $scope.filterText.trim();
    }
    Restangular.one('document/list')
      .get(params)
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
   * Toggle expand/collapse for a document's files.
   */
  $scope.toggleDocExpand = function(docId, $event) {
    if ($event) {
      $event.stopPropagation();
    }
    $scope.expandedDocs[docId] = !$scope.expandedDocs[docId];
  };

  /**
   * Check if a document's files are expanded.
   */
  $scope.isDocExpanded = function(docId) {
    return !!$scope.expandedDocs[docId];
  };

  /**
   * Expand files for all documents on current page.
   */
  $scope.expandAllDocs = function() {
    _.each($scope.documents, function(d) {
      $scope.expandedDocs[d.id] = true;
    });
  };

  /**
   * Collapse files for all documents on current page.
   */
  $scope.collapseAllDocs = function() {
    $scope.expandedDocs = {};
  };

  /**
   * Check if any document on current page is expanded.
   */
  $scope.hasAnyDocExpanded = function() {
    return _.some($scope.documents, function(d) {
      return !!$scope.expandedDocs[d.id];
    });
  };

  /**
   * Move a document up in the current page list.
   */
  $scope.moveDocumentUp = function(doc, index) {
    if (index <= 0 || $scope.savingOrder) {
      return;
    }
    var temp = $scope.documents[index - 1];
    $scope.documents[index - 1] = $scope.documents[index];
    $scope.documents[index] = temp;
    $scope.applyAndSaveDocumentOrder();
  };

  /**
   * Move a document down in the current page list.
   */
  $scope.moveDocumentDown = function(doc, index) {
    if (index >= $scope.documents.length - 1 || $scope.savingOrder) {
      return;
    }
    var temp = $scope.documents[index + 1];
    $scope.documents[index + 1] = $scope.documents[index];
    $scope.documents[index] = temp;
    $scope.applyAndSaveDocumentOrder();
  };

  /**
   * Save reordered documents for the current tag.
   */
  $scope.applyAndSaveDocumentOrder = function() {
    if (!$scope.tag || !$scope.documents || !$scope.documents.length) {
      return;
    }
    $scope.savingOrder = true;
    var docIds = _.pluck($scope.documents, 'id');
    Restangular.one('tag', $scope.tag.id)
      .all('document/reorder')
      .post({
        document_ids_csv: docIds.join(','),
        offset: $scope.offset
      })
      .finally(function() {
        $scope.savingOrder = false;
      });
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
    localStorage.tagDocumentsPageSize = next;
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
