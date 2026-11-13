'use strict';

/**
 * Modal file versions controller.
 */
angular.module('docs').controller('ModalFileVersions', function ($scope, $rootScope, $state, $stateParams, $uibModalInstance, $timeout, $window, $translate, Restangular, Upload, file, writable) {
  $scope.writable = !!writable;
  $scope.privileged = false;

  var checkClientPrivileged = function () {
    if ($rootScope.userInfo) {
      if ($rootScope.userInfo.base_functions && $rootScope.userInfo.base_functions.indexOf('ADMIN') !== -1) {
        return true;
      }
      if ($rootScope.userInfo.groups && angular.isArray($rootScope.userInfo.groups)) {
        for (var i = 0; i < $rootScope.userInfo.groups.length; i++) {
          var g = ($rootScope.userInfo.groups[i] || '').toLowerCase();
          if (g === 'administrators' || g === 'tag_editor') {
            return true;
          }
        }
      }
    }
    return false;
  };
  $scope.privileged = checkClientPrivileged();
  $scope.swapEnabled = false;

  $scope.loadVersions = function () {
    Restangular.one('file/' + file.id + '/versions').get().then(function (data) {
      if (angular.isDefined(data.privileged)) {
        $scope.privileged = !!data.privileged;
      }
      $scope.swapEnabled = !!data.swap_enabled;
      $scope.files = data.files;
    });
  };
  $scope.loadVersions();

  // Copy the physical storage path to the clipboard
  var markCopied = function (verFile) {
    verFile.pathCopied = true;
    $timeout(function () {
      verFile.pathCopied = false;
    }, 1500);
  };
  var fallbackCopy = function (text) {
    var textarea = document.createElement('textarea');
    textarea.value = text;
    textarea.setAttribute('readonly', '');
    textarea.style.position = 'fixed';
    textarea.style.top = '-1000px';
    document.body.appendChild(textarea);
    textarea.select();
    var ok = false;
    try {
      ok = document.execCommand('copy');
    } catch (e) {
      ok = false;
    }
    document.body.removeChild(textarea);
    return ok;
  };
  $scope.copyStoragePath = function (verFile) {
    if (!verFile || !verFile.storage_path) {
      return;
    }
    var text = verFile.storage_path;
    if ($window.navigator && $window.navigator.clipboard && $window.isSecureContext) {
      $window.navigator.clipboard.writeText(text).then(function () {
        $scope.$applyAsync(function () { markCopied(verFile); });
      }, function () {
        $scope.$applyAsync(function () {
          if (fallbackCopy(text)) {
            markCopied(verFile);
          } else {
            $window.prompt('', text);
          }
        });
      });
    } else if (fallbackCopy(text)) {
      markCopied(verFile);
    } else {
      $window.prompt('', text);
    }
  };

  $scope.saveFileName = function (verFile) {
    if (!$scope.privileged || !$scope.swapEnabled || !verFile || !verFile.name || !verFile.name.trim()) {
      return;
    }
    var newName = verFile.name.trim();
    verFile.swapError = null;
    Restangular.one('file/' + verFile.id).post('', {
      name: newName,
      version_label: verFile.version_label || '',
      from_versions: true
    }).then(function (data) {
      verFile.name = (data && data.name) ? data.name : newName;
      if (data && data.storage_path) {
        verFile.storage_path = data.storage_path;
      }
      if (verFile.id === file.id) {
        file.name = verFile.name;
        if (data && data.storage_path) {
          file.storage_path = data.storage_path;
        }
      }
      verFile.nameSaved = true;
      $timeout(function () {
        verFile.nameSaved = false;
      }, 1500);
    }, function (resp) {
      verFile.swapError = (resp && resp.data && resp.data.message) ? resp.data.message : $translate.instant('file.versions.swap_error');
    });
  };

  $scope.saveVersionLabel = function (verFile) {
    var payload = {
      version_label: verFile.version_label || ''
    };
    if ($scope.privileged && $scope.swapEnabled && verFile.name && verFile.name.trim()) {
      payload.name = verFile.name.trim();
      payload.from_versions = true;
    }
    Restangular.one('file/' + verFile.id).post('', payload).then(function (data) {
      if (data && data.name) {
        verFile.name = data.name;
      }
      if (data && data.storage_path) {
        verFile.storage_path = data.storage_path;
      }
      if (verFile.id === file.id) {
        file.name = verFile.name;
        file.version_label = verFile.version_label;
      }
      verFile.saved = true;
      $timeout(function () {
        verFile.saved = false;
      }, 1500);
    });
  };

  $scope.swapVersion = function (files, verFile) {
    if (!$scope.privileged || !$scope.swapEnabled || !files || files.length === 0) {
      return;
    }
    var uploadedFile = files[0];
    var confirmMsg = $translate.instant('file.versions.swap_confirm', {
      version: verFile.version + 1,
      oldName: verFile.name,
      newName: uploadedFile.name
    });
    if (!$window.confirm(confirmMsg)) {
      return;
    }

    verFile.swapping = true;
    verFile.swapProgress = 0;
    verFile.swapError = null;
    Upload.upload({
      method: 'POST',
      url: '../api/file/' + verFile.id + '/swap',
      file: uploadedFile
    })
    .progress(function (e) {
      if (e.total > 0) {
        verFile.swapProgress = parseInt(100.0 * e.loaded / e.total);
      }
    })
    .success(function (data) {
      verFile.swapping = false;
      verFile.name = data.name || uploadedFile.name;
      verFile.mimetype = data.mimetype || verFile.mimetype;
      verFile.size = data.size || uploadedFile.size;
      verFile.swapped = true;
      if (verFile.id === file.id) {
        file.name = verFile.name;
        file.mimetype = verFile.mimetype;
        file.size = verFile.size;
      }
      $scope.loadVersions();
      $timeout(function () {
        verFile.swapped = false;
      }, 3000);
    })
    .error(function (data) {
      verFile.swapping = false;
      verFile.swapError = (data && data.message) ? data.message : $translate.instant('file.versions.swap_error');
    });
  };

  $scope.openFile = function (verFile) {
    $state.go('document.view.content.file', { id: $stateParams.id, fileId: verFile.id });
  };

  $scope.close = function() {
    $uibModalInstance.close();
  };
});