'use strict';

/**
 * Document modal add tag controller.
 */
angular.module('docs').controller('DocumentModalAddTag', function ($scope, $uibModalInstance, Restangular) {
  $scope.tag = { name: '', color: '#3a87ad', parent: '' };
  $scope.tags = [];
  $scope.parentTreeExpanded = {};
  $scope.parentTreeSearch = '';
  $scope.parentSelectorOpen = false;

  Restangular.one('tag/list').get().then(function(data) {
    $scope.tags = data.tags || [];
  });

  $scope.getChildrenTags = function(parent) {
    var filtered = _.filter($scope.tags, function(tag) {
      return tag.parent === parent || (!tag.parent && !parent);
    });
    return filtered.sort(function(a, b) {
      var orderA = (a.order !== undefined && a.order !== null) ? a.order : 999999999;
      var orderB = (b.order !== undefined && b.order !== null) ? b.order : 999999999;
      if (orderA !== orderB) return orderA - orderB;
      return (a.name || '').localeCompare(b.name || '');
    });
  };

  $scope.hasChildren = function(tagId) {
    return _.some($scope.tags, function(tag) {
      return tag.parent === tagId;
    });
  };

  $scope.getTagBreadcrumb = function(tagId) {
    if (!tagId || !$scope.tags || !$scope.tags.length) return '';
    var current = _.findWhere($scope.tags, { id: tagId });
    if (!current) return '';
    var path = [current.name];
    var visited = {};
    visited[current.id] = true;
    while (current && current.parent) {
      if (visited[current.parent]) break;
      visited[current.parent] = true;
      current = _.findWhere($scope.tags, { id: current.parent });
      if (current) path.unshift(current.name);
    }
    return path.join(' > ');
  };

  $scope.getTagColor = function(tagId) {
    if (!tagId || !$scope.tags) return '#3a87ad';
    var t = _.findWhere($scope.tags, { id: tagId });
    return t ? t.color : '#3a87ad';
  };

  $scope.selectParentTag = function(node) {
    $scope.tag.parent = node ? node.id : '';
    $scope.parentSelectorOpen = false;
  };

  $scope.isParentTreeExpanded = function(tagId) {
    return !!$scope.parentTreeExpanded[tagId];
  };

  $scope.toggleParentTreeExpand = function(tagId, $event) {
    if ($event) {
      $event.stopPropagation();
      $event.preventDefault();
    }
    $scope.parentTreeExpanded[tagId] = !$scope.parentTreeExpanded[tagId];
  };

  $scope.nodeOrDescendantsMatch = function(tagId, query) {
    var node = _.findWhere($scope.tags, { id: tagId });
    if (node && node.name && node.name.toLowerCase().indexOf(query) !== -1) return true;
    var children = $scope.getChildrenTags(tagId);
    return _.some(children, function(child) {
      return $scope.nodeOrDescendantsMatch(child.id, query);
    });
  };

  $scope.filterParentTreeNode = function(node) {
    if (!$scope.parentTreeSearch) return true;
    return $scope.nodeOrDescendantsMatch(node.id, $scope.parentTreeSearch.toLowerCase());
  };

  $scope.close = function(tag) {
    $uibModalInstance.close(tag);
  };
});
