'use strict';

/**
 * Settings user edition page controller.
 */
angular.module('docs').controller('SettingsUserEdit', function($scope, $dialog, $state, $stateParams, Restangular, $translate) {
  /**
   * Returns true if in edit mode (false in add mode).
   */
  $scope.isEdit = function () {
    return $stateParams.username;
  };
  
  /**
   * In edit mode, load the current user.
   */
  $scope.transferData = { targetUsername: '' };
  $scope.isTransferring = false;

  $scope.reloadCurrentUser = function () {
    return Restangular.one('user', $stateParams.username).get().then(function (data) {
      data.storage_quota /= 1000000;
      $scope.user = data;
      if ($scope.editUserForm) {
        $scope.editUserForm.$setPristine();
      }
    });
  };

  if ($scope.isEdit()) {
    $scope.reloadCurrentUser();
  } else {
    $scope.user = {}; // Very important otherwise ng-if in template will make a new scope variable
  }

  /**
   * Update the current user.
   */
  $scope.edit = function () {
    var promise = null;
    var user = angular.copy($scope.user);
    user.storage_quota *= 1000000;
    
    if ($scope.isEdit()) {
      promise = Restangular
        .one('user', $stateParams.username)
        .post('', user);
    } else {
      promise = Restangular
        .one('user')
        .put(user);
    }
    
    promise.then(function () {
      $scope.loadUsers();
      if ($scope.isEdit()) {
        $scope.reloadCurrentUser();
      } else {
        $state.go('settings.user');
      }
    }, function (e) {
      var title = $translate.instant('settings.user.edit.edit_user_failed_title');
      var msg = $translate.instant('settings.user.edit.edit_user_failed_message');
      if (e && e.data && e.data.type !== 'AlreadyExistingUsername') {
        msg = e.data.message || $translate.instant('error.general');
      }
      var btns = [{result: 'ok', label: $translate.instant('ok'), cssClass: 'btn-primary'}];
      $dialog.messageBox(title, msg, btns);
    });
  };

  /**
   * Delete the current user.
   */
  $scope.remove = function () {
    var title = $translate.instant('settings.user.edit.delete_user_title');
    var msg = $translate.instant('settings.user.edit.delete_user_message');
    var btns = [
      { result:'cancel', label: $translate.instant('cancel') },
      { result:'ok', label: $translate.instant('ok'), cssClass: 'btn-primary' }
    ];

    $dialog.messageBox(title, msg, btns, function (result) {
      if (result === 'ok') {
        Restangular.one('user', $stateParams.username).remove().then(function () {
          $scope.loadUsers();
          $state.go('settings.user');
        }, function(e) {
          if (e.data.type === 'UserUsedInRouteModel') {
            var title = $translate.instant('settings.user.edit.user_used_title');
            var msg = $translate.instant('settings.user.edit.user_used_message', { name: e.data.message });
            var btns = [{result: 'ok', label: $translate.instant('ok'), cssClass: 'btn-primary'}];
            $dialog.messageBox(title, msg, btns);
          } else if (e.data.type === 'UserHasAssets') {
            var parts = (e.data.message || '0,0,0').split(',');
            var title = $translate.instant('settings.user.edit.user_has_assets_title');
            var msg = $translate.instant('settings.user.edit.user_has_assets_message', {
              documents: parts[0] || 0,
              tags: parts[1] || 0,
              files: parts[2] || 0
            });
            var btns = [{result: 'ok', label: $translate.instant('ok'), cssClass: 'btn-primary'}];
            $dialog.messageBox(title, msg, btns);
          }
        });
      }
    });
  };

  /**
   * Returns active users eligible to receive transferred assets.
   */
  $scope.getTransferTargets = function () {
    if (!$scope.users) {
      return [];
    }
    return $scope.users.filter(function (u) {
      return !u.disabled && u.username !== 'guest' && u.username !== $stateParams.username;
    });
  };

  /**
   * Transfer all owned tags, documents, files, and workflow steps to another active user.
   */
  $scope.transferAssets = function () {
    if (!$scope.transferData.targetUsername) {
      return;
    }
    var targetUsername = $scope.transferData.targetUsername;
    var title = $translate.instant('settings.user.edit.transfer_confirm_title');
    var msg = $translate.instant('settings.user.edit.transfer_confirm_message', {
      source: $stateParams.username,
      target: targetUsername,
      documents: ($scope.user && $scope.user.document_count) || 0,
      tags: ($scope.user && $scope.user.tag_count) || 0,
      files: ($scope.user && $scope.user.file_count) || 0
    });
    var btns = [
      { result: 'cancel', label: $translate.instant('cancel') },
      { result: 'ok', label: $translate.instant('ok'), cssClass: 'btn-warning' }
    ];

    $dialog.messageBox(title, msg, btns, function (result) {
      if (result === 'ok') {
        $scope.isTransferring = true;
        Restangular.one('user/' + $stateParams.username + '/transfer').post('', {
          target_username: targetUsername
        }).then(function (res) {
          $scope.isTransferring = false;
          $scope.transferData.targetUsername = '';
          $scope.loadUsers();
          $scope.reloadCurrentUser();
          var doneTitle = $translate.instant('settings.user.edit.transfer_success_title');
          var doneMsg = $translate.instant('settings.user.edit.transfer_success_message', {
            source: $stateParams.username,
            target: targetUsername,
            documents: res.transferred_documents || 0,
            tags: res.transferred_tags || 0,
            files: res.transferred_files || 0,
            routes: res.transferred_routes || 0
          });
          var okBtns = [{ result: 'ok', label: $translate.instant('ok'), cssClass: 'btn-primary' }];
          $dialog.messageBox(doneTitle, doneMsg, okBtns);
        }, function (err) {
          $scope.isTransferring = false;
          var errTitle = $translate.instant('settings.user.edit.transfer_error_title');
          var errMsg = (err && err.data && err.data.message) ? err.data.message : $translate.instant('settings.user.edit.transfer_error_message');
          var okBtns = [{ result: 'ok', label: $translate.instant('ok'), cssClass: 'btn-primary' }];
          $dialog.messageBox(errTitle, errMsg, okBtns);
        });
      }
    });
  };

  /**
   * Send a password reset email.
   */
  $scope.passwordReset = function () {
      Restangular.one('user').post('password_lost', {
          username: $stateParams.username
      }).then(function () {
          var title = $translate.instant('settings.user.edit.password_lost_sent_title');
          var msg = $translate.instant('settings.user.edit.password_lost_sent_message', { username: $stateParams.username });
          var btns = [{result: 'ok', label: $translate.instant('ok'), cssClass: 'btn-primary'}];
          $dialog.messageBox(title, msg, btns);
      }, function(err) {
          var title = $translate.instant('settings.user.edit.password_lost_sent_title');
          var msg = $translate.instant('settings.user.edit.password_lost_error');
          if (err && err.data && err.data.type === 'LdapUserPasswordResetForbidden') {
              msg = $translate.instant('login.password_lost_ldap_forbidden');
          }
          var btns = [{result: 'ok', label: $translate.instant('ok'), cssClass: 'btn-primary'}];
          $dialog.messageBox(title, msg, btns);
      });
  };

  $scope.disableTotp = function () {
    var title = $translate.instant('settings.user.edit.disable_totp_title');
    var msg = $translate.instant('settings.user.edit.disable_totp_message');
    var btns = [
      { result:'cancel', label: $translate.instant('cancel') },
      { result:'ok', label: $translate.instant('ok'), cssClass: 'btn-primary' }
    ];

    $dialog.messageBox(title, msg, btns, function (result) {
      if (result === 'ok') {
        Restangular.one('user/' + $stateParams.username + '/disable_totp').post('').then(function() {
          $scope.user.totp_enabled = false;
        });
      }
    });
  };

  /**
   * Convert LDAP user to internal user.
   */
  $scope.convertToInternal = function () {
    var title = $translate.instant('settings.user.edit.convert_to_internal_title');
    var msg = $translate.instant('settings.user.edit.convert_to_internal_confirm');
    var btns = [
      { result: 'cancel', label: $translate.instant('cancel') },
      { result: 'ok', label: $translate.instant('ok'), cssClass: 'btn-warning' }
    ];

    $dialog.messageBox(title, msg, btns, function (result) {
      if (result === 'ok') {
        $scope.user.auth_source = 'INTERNAL';
        if ($scope.editUserForm) {
          $scope.editUserForm.$setDirty();
        }
      }
    });
  };
});