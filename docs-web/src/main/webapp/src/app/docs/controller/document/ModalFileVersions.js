'use strict';

/**
 * Modal file versions controller.
 */
angular.module('docs').controller('ModalFileVersions', function ($scope, $state, $stateParams, $uibModalInstance, $timeout, Restangular, file, writable) {
  $scope.writable = !!writable;

  Restangular.one('file/' + file.id + '/versions').get().then(function (data) {
    $scope.files = data.files;
  });

  $scope.saveVersionLabel = function (verFile) {
    Restangular.one('file/' + verFile.id).post('', {
      name: verFile.name,
      version_label: verFile.version_label || ''
    }).then(function () {
      verFile.saved = true;
      $timeout(function () {
        verFile.saved = false;
      }, 1500);
    });
  };

  $scope.openFile = function (file) {
    $state.go('document.view.content.file', { id: $stateParams.id, fileId: file.id })
  };

  $scope.close = function() {
    $uibModalInstance.close();
  };
});