'use strict';

/**
 * Tag edit controller.
 */
angular.module('docs').controller('TagEdit', function($scope, $stateParams, Restangular, $dialog, $state, $translate) {
  var originalInheritAcl = null;

  function loadTagDetails() {
    return Restangular.one('tag', $stateParams.id).get().then(function(data) {
      $scope.tag = data;
      originalInheritAcl = data.inherit_acl;

      // Replace the tag from the list with this reference
      _.each($scope.tags, function(tag, i) {
        if (tag.id === $scope.tag.id) {
          $scope.tags[i] = $scope.tag;
        }
      });
    });
  }

  // Retrieve the tag
  loadTagDetails();

  /**
   * Handle inherit_acl toggle with confirmation guardrail.
   */
  $scope.onInheritAclChange = function() {
    if ($scope.tag.inherit_acl === true && originalInheritAcl === false) {
      var title = $translate.instant('tag.edit.restore_inheritance_title');
      var msg = $translate.instant('tag.edit.restore_inheritance_confirm');
      var btns = [
        {result: 'cancel', label: $translate.instant('cancel')},
        {result: 'ok', label: $translate.instant('ok'), cssClass: 'btn-primary'}
      ];

      $dialog.messageBox(title, msg, btns, function(result) {
        if (result !== 'ok') {
          $scope.tag.inherit_acl = false;
        }
      });
    }
  };

  /**
   * Update a tag.
   */
  $scope.edit = function() {
    // Update the server
    Restangular.one('tag', $scope.tag.id).post('', $scope.tag).then(function () {
      $scope.loadTags();
      loadTagDetails();
    }, function (e) {
      if (e.data.type === 'CircularReference') {
        var title = $translate.instant('tag.edit.circular_reference_title');
        var msg = $translate.instant('tag.edit.circular_reference_message');
        var btns = [{result: 'ok', label: $translate.instant('ok'), cssClass: 'btn-primary'}];
        $dialog.messageBox(title, msg, btns);
      }
    });
  };

  /**
   * Delete a tag.
   */
  /**
   * Select parent tag for Edit Tag form.
   */
  $scope.selectEditParentTag = function(node) {
    $scope.tag.parent = node ? node.id : '';
    $scope.editParentSelectorOpen = false;
    if ($scope.onInheritAclChange) {
      $scope.onInheritAclChange();
    }
  };

  /**
   * Filter tree nodes for Edit Tag form (excludes self and descendants to avoid cycles).
   */
  $scope.filterEditParentTreeNode = function(node) {
    if ($scope.tag && $scope.tag.id) {
      if ($scope.isDescendantOf(node.id, $scope.tag.id)) {
        return false;
      }
    }
    return $scope.filterParentTreeNode(node, $scope.tag ? $scope.tag.id : null);
  };

  $scope.deleteTag = function(tag) {
    if ($scope.hasChildren && $scope.hasChildren(tag.id)) {
      var title = $translate.instant('tag.edit.delete_has_children_title');
      var msg = $translate.instant('tag.edit.delete_has_children_message');
      var btns = [{result: 'ok', label: $translate.instant('ok'), cssClass: 'btn-primary'}];
      $dialog.messageBox(title, msg, btns);
      return;
    }

    var title = $translate.instant('tag.edit.delete_tag_title');
    var msg = $translate.instant('tag.edit.delete_tag_message');
    var btns = [
      {result: 'cancel', label: $translate.instant('cancel')},
      {result: 'ok', label: $translate.instant('ok'), cssClass: 'btn-primary'}
    ];

    $dialog.messageBox(title, msg, btns, function(result) {
      if (result === 'ok') {
        Restangular.one('tag', tag.id).remove().then(function() {
          $scope.loadTags();
          $state.go('tagadmin.default');
        }, function (e) {
          if (e.data && e.data.type === 'TagHasChildren') {
            var errTitle = $translate.instant('tag.edit.delete_has_children_title');
            var errMsg = $translate.instant('tag.edit.delete_has_children_message');
            var errBtns = [{result: 'ok', label: $translate.instant('ok'), cssClass: 'btn-primary'}];
            $dialog.messageBox(errTitle, errMsg, errBtns);
          }
        });
      }
    });
  };
});