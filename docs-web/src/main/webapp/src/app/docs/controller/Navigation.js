'use strict';

/**
 * Navigation controller.
 */
angular.module('docs').controller('Navigation', function($scope, $state, $stateParams, $rootScope, User, Restangular, $dialog, $translate) {
  var encryptPromptShown = false;

  var checkFirstTimeFileEncrypt = function(userData) {
    if (encryptPromptShown || !userData || userData.anonymous) {
      return;
    }
    if (!userData.base_functions || userData.base_functions.indexOf('ADMIN') === -1) {
      return;
    }
    Restangular.one('app').get().then(function(appData) {
      $rootScope.app = appData;
      if (appData && appData.file_encrypt_locked === false && !encryptPromptShown) {
        encryptPromptShown = true;
        $translate('settings.config.first_time_encrypt_title').then(function() {
          var title = $translate.instant('settings.config.first_time_encrypt_title');
          var msg = $translate.instant('settings.config.first_time_encrypt_message');
          var btns = [
            { result: false, label: $translate.instant('settings.config.lock_unencrypted_btn'), cssClass: 'btn-primary' },
            { result: true, label: $translate.instant('settings.config.lock_encrypted_btn'), cssClass: 'btn-warning' }
          ];
          $dialog.messageBox(title, msg, btns, function(encryptChoice) {
            if (encryptChoice === true || encryptChoice === false) {
              Restangular.one('app').post('file_encrypt', {
                enabled: encryptChoice
              }).then(function() {
                if ($rootScope.app) {
                  $rootScope.app.file_encrypt = encryptChoice;
                  $rootScope.app.file_encrypt_locked = true;
                }
              });
            } else {
              encryptPromptShown = false;
            }
          });
        });
      }
    });
  };

  var updateTagManagePermission = function(userData) {
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
    checkFirstTimeFileEncrypt(userData);
  };

  $rootScope.$watch('userInfo', function(newVal) {
    updateTagManagePermission(newVal);
  });

  User.userInfo().then(function(data) {
    $rootScope.userInfo = data;
    updateTagManagePermission(data);
    if (data.anonymous) {
      if($state.current.name !== 'login') {
        $state.go('login', {
          redirectState: $state.current.name,
          redirectParams: JSON.stringify($stateParams),
        }, {
          location: 'replace'
        });
      }
    }
  });

  /**
   * User logout.
   */
  $scope.logout = function($event) {
    User.logout().then(function() {
      User.userInfo(true).then(function(data) {
        $rootScope.userInfo = data;
      });
      $state.go('main');
    });
    $event.preventDefault();
  };
});