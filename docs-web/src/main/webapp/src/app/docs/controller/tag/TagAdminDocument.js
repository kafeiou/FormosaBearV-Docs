'use strict';

/**
 * Tag Admin Document quick-tagging controller (#/tagadmin/document/:id).
 */
angular.module('docs').controller('TagAdminDocument', function($scope, $stateParams, $translate, $timeout, Restangular) {
  $scope.loadingDoc = true;
  $scope.saving = false;
  $scope.saveSuccess = false;
  $scope.saveError = null;
  $scope.doc = null;
  $scope.selectedTagIds = {};
  $scope.initialTagIds = {};
  $scope.tagFilter = '';
  $scope.pickerExpanded = {};

  function loadDocument() {
    $scope.loadingDoc = true;
    Restangular.one('document', $stateParams.id).get().then(function(data) {
      $scope.doc = data;
      $scope.selectedTagIds = {};
      $scope.initialTagIds = {};
      _.each(data.tags || [], function(t) {
        $scope.selectedTagIds[t.id] = true;
        $scope.initialTagIds[t.id] = true;
      });
      $scope.expandAllPickerTags();
      $scope.loadingDoc = false;
    }, function() {
      $scope.loadingDoc = false;
    });
  }

  /**
   * Returns all non-hidden tags available for tagging.
   */
  $scope.getSelectableTags = function() {
    return _.filter($scope.tags || [], function(t) {
      return !t.hidden && !t.effective_hidden && !t.is_container;
    });
  };

  $scope.getVisibleChildrenTags = function(parentId) {
    var filtered = _.filter($scope.tags || [], function(t) {
      if (t.hidden || t.effective_hidden) {
        return false;
      }
      return t.parent === parentId || (!t.parent && !parentId);
    });
    return filtered.sort($scope.tagComparator);
  };

  $scope.hasVisibleChildren = function(tagId) {
    return $scope.getVisibleChildrenTags(tagId).length > 0;
  };

  $scope.isPickerExpanded = function(tagId) {
    if ($scope.tagFilter && $scope.tagFilter.trim()) {
      return true;
    }
    return !!$scope.pickerExpanded[tagId];
  };

  $scope.togglePickerExpand = function(tagId, $event) {
    if ($event) {
      $event.stopPropagation();
      $event.preventDefault();
    }
    $scope.pickerExpanded[tagId] = !$scope.pickerExpanded[tagId];
  };

  $scope.expandAllPickerTags = function() {
    _.each($scope.tags || [], function(t) {
      if (!t.hidden && !t.effective_hidden) {
        $scope.pickerExpanded[t.id] = true;
      }
    });
  };

  $scope.collapseAllPickerTags = function() {
    $scope.pickerExpanded = {};
  };

  $scope.pickerNodeMatches = function(node) {
    if (!$scope.tagFilter || !$scope.tagFilter.trim()) {
      return true;
    }
    var q = $scope.tagFilter.trim().toLowerCase();
    return nodeOrVisibleDescendantsMatch(node.id, q);
  };

  function nodeOrVisibleDescendantsMatch(tagId, query) {
    var node = _.findWhere($scope.tags || [], { id: tagId });
    if (node && !node.hidden && !node.effective_hidden) {
      var text = ((node.name || '') + ' ' + (node.description || '')).toLowerCase();
      if (text.indexOf(query) !== -1) {
        return true;
      }
    }
    var children = $scope.getVisibleChildrenTags(tagId);
    return _.some(children, function(child) {
      return nodeOrVisibleDescendantsMatch(child.id, query);
    });
  }

  $scope.getSelectedTagObjects = function() {
    var result = [];
    _.each($scope.tags || [], function(t) {
      if (!t.hidden && !t.effective_hidden && $scope.selectedTagIds[t.id]) {
        result.push(t);
      }
    });
    return result;
  };

  $scope.removeSelectedTag = function(tagId) {
    if (!$scope.doc || !$scope.doc.writable) {
      return;
    }
    delete $scope.selectedTagIds[tagId];
  };

  $scope.isDirty = function() {
    var selectable = $scope.getSelectableTags();
    for (var i = 0; i < selectable.length; i++) {
      var id = selectable[i].id;
      if (!!$scope.selectedTagIds[id] !== !!$scope.initialTagIds[id]) {
        return true;
      }
    }
    return false;
  };

  $scope.saveDocumentTags = function() {
    if (!$scope.doc || !$scope.doc.writable || $scope.saving) {
      return;
    }
    $scope.saving = true;
    $scope.saveSuccess = false;
    $scope.saveError = null;

    var tagsToSave = [];
    _.each($scope.getSelectableTags(), function(t) {
      if ($scope.selectedTagIds[t.id]) {
        tagsToSave.push(t.id);
      }
    });

    Restangular.one('document', $scope.doc.id).post('tags', {
      tags: tagsToSave,
      preserve_hidden: true
    }).then(function(res) {
      $scope.saving = false;
      $scope.saveSuccess = true;
      $scope.doc.tags = (res && res.tags) || [];
      $scope.initialTagIds = {};
      $scope.selectedTagIds = {};
      _.each($scope.doc.tags, function(t) {
        $scope.initialTagIds[t.id] = true;
        $scope.selectedTagIds[t.id] = true;
      });
      $scope.$emit('tagAdminDocumentTagsUpdated', { documentId: $scope.doc.id });
      $timeout(function() {
        $scope.saveSuccess = false;
      }, 2500);
    }, function(err) {
      $scope.saving = false;
      $scope.saveError = (err && err.data && err.data.message) || $translate.instant('error');
    });
  };

  $scope.$watch('tags.length', function(len) {
    if (len > 0 && Object.keys($scope.pickerExpanded).length === 0) {
      $scope.expandAllPickerTags();
    }
  });

  $scope.$on('tagAdminDocumentTagsUpdated', function(evt, data) {
    if (evt.targetScope !== $scope && data && data.documentId === $stateParams.id) {
      loadDocument();
    }
  });

  loadDocument();
});
