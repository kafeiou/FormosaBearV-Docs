'use strict';

/**
 * Document modal share controller.
 */
angular.module('docs').controller('DocumentModalShare', function ($scope, $uibModalInstance) {
  $scope.name = '';
  $scope.allowExport = false;
  $scope.close = function(confirmed) {
    if (!confirmed) {
      $uibModalInstance.close(null);
      return;
    }
    $uibModalInstance.close({
      name: $scope.name,
      allow_export: !!$scope.allowExport
    });
  };
});