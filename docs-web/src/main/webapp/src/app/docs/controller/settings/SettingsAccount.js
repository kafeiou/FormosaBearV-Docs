'use strict';

/**
 * Settings account controller.
 */
angular.module('docs').controller('SettingsAccount', function($scope, Restangular, $translate) {
  $scope.user = {};
  $scope.editUserAlert = false;
  
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
    Restangular.one('user').post('', $scope.user).then(function() {
      $scope.user = {};
      if (form) {
        form.$setPristine();
      }
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
