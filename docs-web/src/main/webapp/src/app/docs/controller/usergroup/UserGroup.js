'use strict';

/**
 * User/group controller.
 */
angular.module('docs').controller('UserGroup', function(Restangular, $scope, $rootScope, $state) {
  // Load users
  Restangular.one('user/list').get({
    sort_column: 1,
    asc: true
  }).then(function(data) {
    $scope.users = data.users;
  });

  // Load groups (blocked for guest account)
  if (!$rootScope.userInfo || $rootScope.userInfo.username !== 'guest') {
    Restangular.one('group').get({
      sort_column: 1,
      asc: true
    }).then(function(data) {
      $scope.groups = data.groups;
    }, function() {
      $scope.groups = [];
    });
  } else {
    $scope.groups = [];
  }

  // Open a user
  $scope.openUser = function(user) {
    $state.go('user.profile', { username: user.username });
  };

  // Open a group
  $scope.openGroup = function(group) {
    $state.go('group.profile', { name: group.name });
  };
});