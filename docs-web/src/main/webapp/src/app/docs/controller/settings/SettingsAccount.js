'use strict';

/**
 * Settings account controller.
 */
angular.module('docs').controller('SettingsAccount', function($scope, $rootScope, Restangular, $translate, User) {
  $scope.user = {
    name: ($rootScope.userInfo && $rootScope.userInfo.name) ? $rootScope.userInfo.name : ''
  };
  $scope.editUserAlert = false;

  $scope.$watch('userInfo.name', function(newName) {
    if (newName !== undefined && (!$scope.editUserForm || !$scope.editUserForm.$dirty)) {
      $scope.user.name = newName || '';
    }
  });
  
  // Alerts
  $scope.alerts = [];
  
  /**
   * Close an alert.
   */
  $scope.closeAlert = function(index) {
    $scope.alerts.splice(index, 1);
  };
  
  /**
   * Edit user.
   */
  $scope.editUser = function(form) {
    if (form && !form.$valid) {
      return;
    }
    var payload = {
      name: $scope.user.name || ''
    };
    if ($scope.user.password) {
      payload.password = $scope.user.password;
    }
    Restangular.one('user').post('', payload).then(function() {
      $scope.user.password = '';
      $scope.user.passwordconfirm = '';
      if (form) {
        form.$setPristine();
      }
      User.userInfo(true).then(function(userData) {
        $rootScope.userInfo = userData;
        if ($rootScope.refreshUserDisplayMap) {
          $rootScope.refreshUserDisplayMap();
        }
      });
      $scope.alerts = [{ type: 'success', msg: $translate.instant('settings.account.updated') }];
    }, function(err) {
      var msg = $translate.instant('error.general');
      if (err && err.data && err.data.message) {
        msg = err.data.message;
      }
      $scope.alerts = [{ type: 'danger', msg: msg }];
    });
  };
});
