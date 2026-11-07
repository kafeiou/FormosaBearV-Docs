'use strict';

/**
 * Tag controller.
 */
angular.module('docs').controller('Tag', function($scope, $rootScope, $state, $stateParams, Restangular, User) {
  $scope.tag = { name: '', color: '#3a87ad', parent: '' };
  $scope.expandedTags = {};

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
    if ($state.includes('tagadmin') && !canManage) {
      $state.go('tag.default');
    }
  });

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
    Restangular.one('tag/list').get().then(function(data) {
      $scope.tags = data.tags;
      $scope.showAddTag = !!$rootScope.canManageTags;
      if ($stateParams.id) {
        $scope.expandAncestors($stateParams.id);
      }
    });
  };
  $scope.loadTags();

  $scope.$watch(function() {
    return $stateParams.id;
  }, function(newId) {
    if (newId) {
      $scope.expandAncestors(newId);
    }
  });

  /**
   * Add a tag.
   */
  $scope.addTag = function() {
    var tagData = angular.copy($scope.tag);
    if (!tagData.parent) {
      delete tagData.parent;
    }
    Restangular.one('tag').put(tagData).then(function() {
      $scope.loadTags();
      $scope.tag = { name: '', color: '#3a87ad', parent: '' };
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
   * Toggle expand/collapse for a single tag node.
   */
  $scope.toggleTagExpand = function(tagId, $event) {
    if ($event) {
      $event.stopPropagation();
      $event.preventDefault();
    }
    $scope.expandedTags[tagId] = !$scope.expandedTags[tagId];
  };

  /**
   * When clicking a tag name in the tree, auto-expand its children if it has any.
   */
  $scope.onTagClick = function(tag) {
    if (tag && $scope.hasChildren(tag.id)) {
      $scope.expandedTags[tag.id] = true;
    }
  };

  /**
   * Check if any tag in the tree has children.
   */
  $scope.hasAnyNestedTags = function() {
    return _.some($scope.tags, function(tag) {
      return !!tag.parent;
    });
  };

  /**
   * Expand all parent tags in the tree.
   */
  $scope.expandAllTags = function() {
    _.each($scope.tags, function(tag) {
      if ($scope.hasChildren(tag.id)) {
        $scope.expandedTags[tag.id] = true;
      }
    });
  };

  /**
   * Collapse all tags in the tree.
   */
  $scope.collapseAllTags = function() {
    $scope.expandedTags = {};
  };

  // Parent tag tree selector state
  $scope.parentTreeExpanded = {};
  $scope.parentTreeSearch = '';

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
    $scope.parentSelectorOpen = false;
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
  $scope.filterParentTreeNode = function(node, excludeId) {
    if (excludeId && $scope.isDescendantOf(node.id, excludeId)) {
      return false;
    }
    if (!$scope.parentTreeSearch) return true;
    var q = $scope.parentTreeSearch.toLowerCase();
    return $scope.nodeOrDescendantsMatch(node.id, q, excludeId);
  };
});
