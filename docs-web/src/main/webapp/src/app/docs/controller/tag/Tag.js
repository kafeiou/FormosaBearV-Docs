'use strict';

/**
 * Tag controller.
 */
angular.module('docs').controller('Tag', function($scope, $rootScope, $state, $stateParams, $location, $timeout, Restangular, User, $uibModal) {
  $scope.tag = { name: '', color: '#3a87ad', description: '', parent: '', hidden: false };
  $scope.expandedTags = {};
  $scope.showTreeDocuments = true;
  $scope.tagDocs = {};
  $scope.untaggedExpanded = false;
  $scope.untaggedDocs = { loading: false, loaded: false, total: null, documents: [] };
  var TREE_DOCS_PAGE_SIZE = 20;

  /**
   * Open tag report export modal.
   */
  $scope.openTagExportModal = function() {
    $uibModal.open({
      templateUrl: 'partial/docs/tag.export.html',
      controller: 'ModalTagExport',
      resolve: {
        initialTagId: function() {
          return $stateParams.id || null;
        }
      }
    });
  };

  // Guardrail for tagadmin routes: redirect non-authorized users to tag browser
  User.userInfo().then(function(userData) {
    var canManage = false;
    if (userData && !userData.anonymous) {
      if (userData.base_functions && userData.base_functions.indexOf('ADMIN') !== -1) {
        canManage = true;
      }
      if (userData.groups && _.isArray(userData.groups)) {
        _.each(userData.groups, function(g) {
          if (g === 'administrators' || g === 'tag_editor') {
            canManage = true;
          }
        });
      }
    }
    $rootScope.canManageTags = canManage;
    if ($state.includes('tagadmin')) {
      if (data.anonymous || data.username === 'guest') {
        $state.go('tag.default');
      } else if ($state.current.name === 'tagadmin.edit' && !canManage) {
        $state.go('tagadmin.default');
      }
    }
  });

  $scope.addTagFormOpen = false;
  $scope.toggleAddTagForm = function() {
    $scope.addTagFormOpen = !$scope.addTagFormOpen;
  };

  $scope.getTagAdminAddDocHref = function() {
    if ($state.current.name === 'tagadmin.edit' && $stateParams.id) {
      return '#/tagadmin/document-add?tagId=' + encodeURIComponent($stateParams.id);
    }
    return '#/tagadmin/document-add';
  };

  $scope.importEml = function(file) {
    if (!file) {
      return;
    }
    $uibModal.open({
      templateUrl: 'partial/docs/import.html',
      controller: 'ModalImport',
      resolve: {
        file: function() {
          return file;
        }
      }
    }).result.then(function(data) {
      if (!data) {
        return;
      }
      $scope.$emit('tagAdminDocumentTagsUpdated', { documentId: data.id, tagIds: [], isNew: true });
      $state.go('tagadmin.document.view.content', { id: data.id });
    });
  };

  /**
   * Expand all ancestor tags of the specified tag ID so it is visible in the tree.
   */
  $scope.expandAncestors = function(tagId) {
    if (!tagId || !$scope.tags || !$scope.tags.length) {
      return;
    }
    var current = _.findWhere($scope.tags, { id: tagId });
    while (current && current.parent) {
      $scope.expandedTags[current.parent] = true;
      current = _.findWhere($scope.tags, { id: current.parent });
    }
  };

  // Retrieve tags
  $scope.tags = [];
  $scope.loadTags = function() {
    var params = $state.includes('tagadmin') ? { include_hidden: true } : {};
    Restangular.one('tag/list').get(params).then(function(data) {
      $scope.tags = data.tags;
      $scope.showAddTag = !!$rootScope.canManageTags;
      if ($stateParams.id) {
        $scope.expandAncestors($stateParams.id);
      }
    });
  };
  $scope.loadTags();

  $scope.$watch(function() {
    return ($scope.getCurrentTagId() || '') + '|' + ($scope.getFocusRootId() || '');
  }, function() {
    var currentId = $scope.getCurrentTagId();
    if (currentId) {
      $scope.expandAncestors(currentId);
    }
    var focusRootId = $scope.getFocusRootId();
    if (focusRootId) {
      $scope.expandedTags[focusRootId] = true;
    }
  });

  /**
   * Returns the currently selected tag ID from UI-Router state params.
   */
  $scope.getCurrentTagId = function() {
    if ($state.includes('tag.document')) {
      return ($state.params && $state.params.tagId) || ($rootScope.$stateParams && $rootScope.$stateParams.tagId) || $stateParams.tagId || null;
    }
    return ($state.params && $state.params.id) || ($rootScope.$stateParams && $rootScope.$stateParams.id) || $stateParams.id || null;
  };

  $scope.browseLastDoc = null;

  $scope.setBrowseLastDoc = function(tagId, docId) {
    if (tagId && docId) {
      $scope.browseLastDoc = { tagId: tagId, docId: docId };
    }
  };

  $scope.clearBrowseLastDoc = function() {
    $scope.browseLastDoc = null;
  };

  $scope.getTagBrowseDocHref = function(tagId, docId) {
    if (!tagId || !docId) {
      return '#/tag';
    }
    var focusRootId = $scope.getFocusRootId();
    var href = '#/tag/' + tagId + '/document/' + docId + '/view/content';
    if (focusRootId) {
      href += '?focus=' + encodeURIComponent(focusRootId);
    }
    return href;
  };

  var syncBrowseLastDocFromState = function() {
    if ($state.includes('tag.document') && $state.params && $state.params.tagId && $state.params.id) {
      $scope.browseLastDoc = { tagId: $state.params.tagId, docId: $state.params.id };
    } else if ($state.includes('tag.view') && $scope.browseLastDoc) {
      var currentTagId = ($state.params && $state.params.id) || null;
      if (currentTagId && $scope.browseLastDoc.tagId !== currentTagId) {
        $scope.browseLastDoc = null;
      }
    }
  };
  syncBrowseLastDocFromState();
  $scope.$on('$stateChangeSuccess', syncBrowseLastDocFromState);
  $scope.$on('tagBrowseDocumentDeleted', function() {
    $scope.browseLastDoc = null;
    $scope.loadTags();
  });

  /**
   * Returns the active focus-mode root tag ID, or null when viewing all tags.
   */
  $scope.getFocusRootId = function() {
    var search = $location.search() || {};
    var focus = search.focus || ($state.params && $state.params.focus) || ($rootScope.$stateParams && $rootScope.$stateParams.focus) || null;
    if (!focus || focus === true) {
      return focus === true ? $scope.getCurrentTagId() : null;
    }
    if (focus === '1' || focus === 'true') {
      return $scope.getCurrentTagId();
    }
    return focus;
  };

  $scope.isFocusMode = function() {
    return !!$scope.getFocusRootId();
  };

  $scope.getBrowseRootTags = function() {
    var focusRootId = $scope.getFocusRootId();
    if (focusRootId) {
      var focusTag = _.findWhere($scope.tags, { id: focusRootId });
      if (focusTag) {
        return [focusTag];
      }
    }
    return $scope.getChildrenTags();
  };

  $scope.getTagBrowseHref = function(tag) {
    if (!tag) {
      return '#/tag';
    }
    var focusRootId = $scope.getFocusRootId();
    if (focusRootId) {
      return '#/tag/' + tag.id + '?focus=' + encodeURIComponent(focusRootId);
    }
    return '#/tag/' + tag.id;
  };

  $scope.toggleFocusMode = function() {
    var currentId = $scope.getCurrentTagId();
    if (!currentId) {
      return;
    }
    if ($scope.isFocusMode()) {
      $location.search('focus', null);
    } else {
      $scope.expandedTags[currentId] = true;
      $location.search('focus', currentId);
    }
  };

  /**
   * Add a tag.
   */
  $scope.addTag = function() {
    var tagData = angular.copy($scope.tag);
    if (!tagData.parent) {
      delete tagData.parent;
    }
    if (!tagData.description) {
      delete tagData.description;
    }
    Restangular.one('tag').put(tagData).then(function() {
      $scope.loadTags();
      $scope.tag = { name: '', color: '#3a87ad', description: '', parent: '', hidden: false };
      $scope.parentSelector.open = false;
      $scope.parentSelector.search = '';
    });
  };

  /**
   * Sort comparator for tags within the same hierarchy level.
   * Priority: order ASC -> create_date ASC (earliest first) -> name ASC
   */
  $scope.tagComparator = function(a, b) {
    var orderA = (a.order !== undefined && a.order !== null) ? a.order : 999999999;
    var orderB = (b.order !== undefined && b.order !== null) ? b.order : 999999999;
    if (orderA !== orderB) {
      return orderA - orderB;
    }
    var dateA = a.create_date || 0;
    var dateB = b.create_date || 0;
    if (dateA !== dateB) {
      return dateA - dateB;
    }
    return (a.name || '').localeCompare(b.name || '');
  };

  /**
   * Find children tags, sorted by tagComparator.
   */
  $scope.getChildrenTags = function(parent) {
    var filtered = _.filter($scope.tags, function(tag) {
      return tag.parent === parent || (!tag.parent && !parent);
    });
    return filtered.sort($scope.tagComparator);
  };

  /**
   * Check if tag is first sibling in its level.
   */
  $scope.isFirstSibling = function(tag) {
    var siblings = $scope.getChildrenTags(tag.parent);
    return siblings.length > 0 && siblings[0].id === tag.id;
  };

  /**
   * Check if tag is last sibling in its level.
   */
  $scope.isLastSibling = function(tag) {
    var siblings = $scope.getChildrenTags(tag.parent);
    return siblings.length > 0 && siblings[siblings.length - 1].id === tag.id;
  };

  /**
   * Move tag up in its level.
   */
  $scope.moveTagUp = function(tag, $event) {
    if ($event) {
      $event.stopPropagation();
      $event.preventDefault();
    }
    var siblings = $scope.getChildrenTags(tag.parent);
    var idx = _.findIndex(siblings, { id: tag.id });
    if (idx <= 0) {
      return;
    }
    var temp = siblings[idx - 1];
    siblings[idx - 1] = siblings[idx];
    siblings[idx] = temp;

    $scope.applyAndSaveTagOrder(siblings);
  };

  /**
   * Move tag down in its level.
   */
  $scope.moveTagDown = function(tag, $event) {
    if ($event) {
      $event.stopPropagation();
      $event.preventDefault();
    }
    var siblings = $scope.getChildrenTags(tag.parent);
    var idx = _.findIndex(siblings, { id: tag.id });
    if (idx < 0 || idx >= siblings.length - 1) {
      return;
    }
    var temp = siblings[idx + 1];
    siblings[idx + 1] = siblings[idx];
    siblings[idx] = temp;

    $scope.applyAndSaveTagOrder(siblings);
  };

  /**
   * Apply sequential order numbers locally and persist to server.
   */
  $scope.applyAndSaveTagOrder = function(siblings) {
    var orderedIds = [];
    _.each(siblings, function(sib, i) {
      sib.order = i;
      orderedIds.push(sib.id);
    });

    Restangular.all('tag/reorder').post({
      tag_ids_csv: orderedIds.join(',')
    }).then(function() {
      // Reorder saved successfully
    }, function() {
      // Fallback: reload tags on error
      $scope.loadTags();
    });
  };

  /**
   * Check if a tag has child tags.
   */
  $scope.hasChildren = function(tagId) {
    return _.some($scope.tags, function(tag) {
      return tag.parent === tagId;
    });
  };

  /**
   * Check if a tag node is currently expanded.
   */
  $scope.isTagExpanded = function(tagId) {
    return !!$scope.expandedTags[tagId];
  };

  /**
   * Build tag-admin document link preserving active mode (Tags vs Document View).
   */
  $scope.getTagAdminDocHref = function(docId) {
    if (!$rootScope.canManageTags || $state.includes('tagadmin.document.view') || $state.includes('tagadmin.document.edit')) {
      return '#/tagadmin/document/' + docId + '/view/content';
    }
    return '#/tagadmin/document/' + docId;
  };

  /**
   * Toggle display of documents inside the tag admin tree.
   */
  $scope.toggleShowTreeDocuments = function() {
    $scope.showTreeDocuments = !$scope.showTreeDocuments;
    if ($scope.showTreeDocuments && $state.includes('tagadmin')) {
      $scope.refreshExpandedTagDocuments();
    }
  };

  /**
   * Returns true if the tag node can be expanded (has child tags or direct documents in tag admin).
   */
  $scope.hasExpandableContent = function(tag) {
    if (!tag) return false;
    return $scope.hasChildren(tag.id) || ($scope.showTreeDocuments && tag.direct_document_count > 0);
  };

  /**
   * Lazy-load direct documents for a specific tag node.
   */
  $scope.loadTagDocuments = function(tagId, append) {
    if (!tagId) return;
    var bucket = $scope.tagDocs[tagId];
    if (!bucket) {
      bucket = { loading: false, loaded: false, total: 0, documents: [] };
      $scope.tagDocs[tagId] = bucket;
    }
    if (bucket.loading) return;
    var offset = append ? bucket.documents.length : 0;
    bucket.loading = true;
    Restangular.one('tag', tagId).one('documents').get({
      offset: offset,
      limit: TREE_DOCS_PAGE_SIZE
    }).then(function(data) {
      var incoming = (data && data.documents) || [];
      bucket.total = (data && data.total !== undefined) ? data.total : incoming.length;
      bucket.documents = append ? bucket.documents.concat(incoming) : incoming;
      bucket.loaded = true;
      bucket.loading = false;
    }, function() {
      bucket.loading = false;
    });
  };

  /**
   * Lazy-load untagged documents for the virtual "Untagged documents" folder.
   */
  $scope.loadUntaggedDocuments = function(append) {
    if ($scope.untaggedDocs.loading) return;
    var offset = append ? $scope.untaggedDocs.documents.length : 0;
    $scope.untaggedDocs.loading = true;
    Restangular.one('tag/untagged/documents').get({
      offset: offset,
      limit: TREE_DOCS_PAGE_SIZE
    }).then(function(data) {
      var incoming = (data && data.documents) || [];
      $scope.untaggedDocs.total = (data && data.total !== undefined) ? data.total : incoming.length;
      $scope.untaggedDocs.documents = append ? $scope.untaggedDocs.documents.concat(incoming) : incoming;
      $scope.untaggedDocs.loaded = true;
      $scope.untaggedDocs.loading = false;
    }, function() {
      $scope.untaggedDocs.loading = false;
    });
  };

  $scope.toggleUntaggedExpand = function($event) {
    if ($event) {
      $event.stopPropagation();
      $event.preventDefault();
    }
    $scope.untaggedExpanded = !$scope.untaggedExpanded;
    if ($scope.untaggedExpanded && !$scope.untaggedDocs.loaded) {
      $scope.loadUntaggedDocuments(false);
    }
  };

  $scope.refreshExpandedTagDocuments = function() {
    _.each($scope.tags || [], function(t) {
      if ($scope.expandedTags[t.id]) {
        $scope.loadTagDocuments(t.id, false);
      }
    });
    if ($scope.untaggedExpanded) {
      $scope.loadUntaggedDocuments(false);
    }
  };

  /**
   * Move a document up within a tag's direct document list in the tag admin tree.
   */
  $scope.moveTreeDocUp = function(tagId, index, $event) {
    if ($event) {
      $event.stopPropagation();
      $event.preventDefault();
    }
    var bucket = $scope.tagDocs[tagId];
    if (!bucket || bucket.savingOrder || index <= 0 || index >= bucket.documents.length) {
      return;
    }
    var temp = bucket.documents[index - 1];
    bucket.documents[index - 1] = bucket.documents[index];
    bucket.documents[index] = temp;
    $scope.saveTreeDocOrder(tagId);
  };

  /**
   * Move a document down within a tag's direct document list in the tag admin tree.
   */
  $scope.moveTreeDocDown = function(tagId, index, $event) {
    if ($event) {
      $event.stopPropagation();
      $event.preventDefault();
    }
    var bucket = $scope.tagDocs[tagId];
    if (!bucket || bucket.savingOrder || index < 0 || index >= bucket.documents.length - 1) {
      return;
    }
    var temp = bucket.documents[index + 1];
    bucket.documents[index + 1] = bucket.documents[index];
    bucket.documents[index] = temp;
    $scope.saveTreeDocOrder(tagId);
  };

  /**
   * Persist reordered direct documents for a tag via POST /api/tag/{id}/document/reorder.
   */
  $scope.saveTreeDocOrder = function(tagId) {
    var bucket = $scope.tagDocs[tagId];
    if (!bucket || !bucket.documents || !bucket.documents.length) {
      return;
    }
    bucket.savingOrder = true;
    var docIds = _.pluck(bucket.documents, 'id');
    Restangular.one('tag', tagId)
      .all('document/reorder')
      .post({
        document_ids_csv: docIds.join(','),
        offset: 0
      })
      .then(function() {
        bucket.savingOrder = false;
      }, function() {
        bucket.savingOrder = false;
        $scope.loadTagDocuments(tagId, false);
      });
  };

  var scrollActiveTreeDocIntoView = function() {
    $timeout(function() {
      var el = document.querySelector('.tag-tree .tag-tree-row.tag-tree-active');
      if (el && el.scrollIntoView) {
        el.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
      }
    }, 350);
  };

  $scope.$on('tagAdminDocumentTagsUpdated', function(evt, payload) {
    $scope.loadTags();
    if (payload && payload.isNew) {
      $scope.showTreeDocuments = true;
      var tagIds = payload.tagIds || [];
      if (tagIds.length === 0) {
        $scope.untaggedExpanded = true;
        $scope.loadUntaggedDocuments(false);
      } else {
        _.each(tagIds, function(tid) {
          $scope.expandAncestors(tid);
          $scope.expandedTags[tid] = true;
          $scope.loadTagDocuments(tid, false);
        });
      }
      scrollActiveTreeDocIntoView();
    } else if ($scope.showTreeDocuments) {
      $scope.refreshExpandedTagDocuments();
    }
  });

  $scope.$on('tagAdminDocumentFilesUpdated', function() {
    if ($scope.showTreeDocuments) {
      $scope.refreshExpandedTagDocuments();
    }
  });

  /**
   * Toggle expand/collapse for a single tag node.
   */
  $scope.toggleTagExpand = function(tagId, $event) {
    if ($event) {
      $event.stopPropagation();
      $event.preventDefault();
    }
    $scope.expandedTags[tagId] = !$scope.expandedTags[tagId];
    if ($scope.expandedTags[tagId] && $state.includes('tagadmin') && $scope.showTreeDocuments) {
      var bucket = $scope.tagDocs[tagId];
      if (!bucket || !bucket.loaded) {
        $scope.loadTagDocuments(tagId, false);
      }
    }
  };

  /**
   * When clicking a tag name in the tree, auto-expand its children if it has any.
   */
  $scope.onTagClick = function(tag) {
    if (tag && ($scope.hasChildren(tag.id) || ($state.includes('tagadmin') && $scope.showTreeDocuments && tag.direct_document_count > 0))) {
      $scope.expandedTags[tag.id] = true;
      if ($state.includes('tagadmin') && $scope.showTreeDocuments) {
        var bucket = $scope.tagDocs[tag.id];
        if (!bucket || !bucket.loaded) {
          $scope.loadTagDocuments(tag.id, false);
        }
      }
    }
  };

  /**
   * Check if any tag in the tree has children.
   */
  $scope.hasAnyNestedTags = function() {
    return _.some($scope.tags, function(tag) {
      return !!tag.parent || ($state.includes('tagadmin') && $scope.showTreeDocuments && tag.direct_document_count > 0);
    });
  };

  /**
   * Expand all parent tags in the tree.
   */
  $scope.expandAllTags = function() {
    _.each($scope.tags, function(tag) {
      if ($scope.hasChildren(tag.id) || ($state.includes('tagadmin') && $scope.showTreeDocuments && tag.direct_document_count > 0)) {
        $scope.expandedTags[tag.id] = true;
      }
    });
    if ($state.includes('tagadmin') && $scope.showTreeDocuments) {
      $scope.refreshExpandedTagDocuments();
    }
  };

  /**
   * Collapse all tags in the tree.
   */
  $scope.collapseAllTags = function() {
    $scope.expandedTags = {};
    $scope.untaggedExpanded = false;
  };

  $scope.areAllTagsExpanded = function() {
    var expandable = _.filter($scope.tags || [], function(tag) {
      return $scope.hasChildren(tag.id) || ($state.includes('tagadmin') && $scope.showTreeDocuments && tag.direct_document_count > 0);
    });
    if (expandable.length === 0) {
      return false;
    }
    return _.every(expandable, function(tag) {
      return !!$scope.expandedTags[tag.id];
    });
  };

  $scope.toggleExpandAllTags = function() {
    if ($scope.areAllTagsExpanded()) {
      $scope.collapseAllTags();
    } else {
      $scope.expandAllTags();
    }
  };

  // Parent tag tree selector state
  $scope.parentTreeExpanded = {};
  $scope.parentSelector = {
    open: false,
    search: ''
  };

  /**
   * Get full hierarchical path breadcrumb of a tag (e.g. "DCC > QMS > QP").
   */
  $scope.getTagBreadcrumb = function(tagId) {
    if (!tagId || !$scope.tags || !$scope.tags.length) {
      return '';
    }
    var current = _.findWhere($scope.tags, { id: tagId });
    if (!current) {
      return '';
    }
    var path = [current.name];
    var visited = {};
    visited[current.id] = true;
    while (current && current.parent) {
      if (visited[current.parent]) break;
      visited[current.parent] = true;
      current = _.findWhere($scope.tags, { id: current.parent });
      if (current) {
        path.unshift(current.name);
      }
    }
    return path.join(' > ');
  };

  /**
   * Get tag color by ID.
   */
  $scope.getTagColor = function(tagId) {
    if (!tagId || !$scope.tags) return '#3a87ad';
    var t = _.findWhere($scope.tags, { id: tagId });
    return t ? t.color : '#3a87ad';
  };

  /**
   * Select parent tag from tree for Add Tag form.
   */
  $scope.selectParentTag = function(node) {
    $scope.tag.parent = node ? node.id : '';
    $scope.parentSelector.open = false;
  };

  /**
   * Check if parent selector tree node is expanded.
   */
  $scope.isParentTreeExpanded = function(tagId) {
    return !!$scope.parentTreeExpanded[tagId];
  };

  /**
   * Toggle parent selector tree node expansion.
   */
  $scope.toggleParentTreeExpand = function(tagId, $event) {
    if ($event) {
      $event.stopPropagation();
      $event.preventDefault();
    }
    $scope.parentTreeExpanded[tagId] = !$scope.parentTreeExpanded[tagId];
  };

  /**
   * Check if a tag is equal to or a descendant of another tag.
   */
  $scope.isDescendantOf = function(tagId, ancestorId) {
    if (!tagId || !ancestorId) return false;
    if (tagId === ancestorId) return true;
    var current = _.findWhere($scope.tags, { id: tagId });
    var visited = {};
    while (current && current.parent) {
      if (current.parent === ancestorId) return true;
      if (visited[current.parent]) break;
      visited[current.parent] = true;
      current = _.findWhere($scope.tags, { id: current.parent });
    }
    return false;
  };

  /**
   * Recursive check if node or any of its descendants match the query.
   */
  $scope.nodeOrDescendantsMatch = function(tagId, query, excludeId) {
    if (excludeId && tagId === excludeId) return false;
    var node = _.findWhere($scope.tags, { id: tagId });
    if (node && node.name && node.name.toLowerCase().indexOf(query) !== -1) {
      return true;
    }
    var children = $scope.getChildrenTags(tagId);
    return _.some(children, function(child) {
      return $scope.nodeOrDescendantsMatch(child.id, query, excludeId);
    });
  };

  /**
   * Filter tree nodes based on search query.
   */
  $scope.filterParentTreeNode = function(node) {
    if (!$scope.parentSelector.search) return true;
    var q = $scope.parentSelector.search.toLowerCase();
    return $scope.nodeOrDescendantsMatch(node.id, q, null);
  };
});
