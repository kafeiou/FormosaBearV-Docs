'use strict';

/**
 * Settings SBOM (Software Bill of Materials) controller.
 */
angular.module('docs').controller('SettingsSbom', function($scope, $window, Restangular) {
  $scope.loading = true;
  $scope.sbom = null;
  $scope.searchQuery = '';
  $scope.sections = {
    plugins: true,
    frontend: false,
    backend: false
  };

  $scope.loadSbom = function(refresh) {
    $scope.loading = true;
    var params = refresh ? { refresh: true } : {};
    Restangular.one('app/sbom').get(params).then(function(data) {
      $scope.sbom = data;
      $scope.loading = false;
    }, function() {
      $scope.loading = false;
    });
  };

  $scope.toggleSection = function(key) {
    $scope.sections[key] = !$scope.sections[key];
  };

  $scope.isSectionOpen = function(key, matchedCount) {
    if ($scope.searchQuery && $scope.searchQuery.trim().length > 0) {
      return matchedCount > 0;
    }
    return $scope.sections[key];
  };

  $scope.matchesQuery = function(item) {
    if (!$scope.searchQuery || !$scope.searchQuery.trim()) {
      return true;
    }
    var q = $scope.searchQuery.trim().toLowerCase();
    var text = ((item.group || '') + ' ' + (item.name || '') + ' ' + (item.version || '') + ' ' + (item.status || '')).toLowerCase();
    return text.indexOf(q) !== -1;
  };

  $scope.exportSbom = function(format) {
    $window.location.href = '../api/app/sbom/export?format=' + encodeURIComponent(format);
  };

  $scope.loadSbom(false);
});
