'use strict';

/**
 * Tag controller.
 */
angular.module('docs').controller('Tag', function($scope, $rootScope, $state, $stateParams, Restangular, User) {
  $scope.tag = { name: '', color: '#3a87ad' };
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
    Restangular.one('tag').put($scope.tag).then(function(data) {
      $scope.tags.push({ id: data.id, name: $scope.tag.name, color: $scope.tag.color });
      $scope.tag = { name: '', color: '#3a87ad' };
    });
  };

  /**
   * Find children tags.
   */
  $scope.getChildrenTags = function(parent) {
    return _.filter($scope.tags, function(tag) {
      return tag.parent === parent || !tag.parent && !parent;
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
});