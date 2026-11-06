'use strict';

/**
 * Settings backup and restore controller.
 */
angular.module('docs').controller('SettingsBackup', function ($scope, $window, $timeout, $filter, Restangular, Upload) {
  $scope.info = {};
  $scope.backupFile = null;
  $scope.downloading = false;
  $scope.restoring = false;
  $scope.progressPercentage = 0;
  $scope.restoreSuccess = false;
  $scope.restoreError = false;
  $scope.restoreErrorMessage = '';
  $scope.restoreStatusMessage = '';

  /**
   * Loads backup statistics and configuration information.
   */
  $scope.loadInfo = function () {
    Restangular.one('backup/info').get().then(function (data) {
      $scope.info = data;
    }, function () {
      $scope.info = {};
    });
  };

  /**
   * Triggers download of the streaming backup ZIP archive.
   */
  $scope.downloadBackup = function () {
    $scope.downloading = true;
    $window.location.href = '../api/backup';
    $timeout(function () {
      $scope.downloading = false;
    }, 4000);
  };

  /**
   * High-risk confirmation dialog and triggers upload/restore.
   */
  $scope.confirmAndRestore = function () {
    if (!$scope.backupFile) {
      return;
    }

    var promptText = $filter('translate')('settings.backup.confirm_restore_prompt');
    if (!$window.confirm(promptText)) {
      return;
    }

    $scope.restoring = true;
    $scope.restoreSuccess = false;
    $scope.restoreError = false;
    $scope.progressPercentage = 0;
    $scope.restoreStatusMessage = $filter('translate')('settings.backup.uploading_and_restoring');

    Upload.upload({
      url: '../api/backup/restore',
      data: { file: $scope.backupFile }
    }).then(function () {
      $scope.restoring = false;
      $scope.progressPercentage = 100;
      $scope.restoreSuccess = true;
      $scope.loadInfo();
    }, function (response) {
      $scope.restoring = false;
      $scope.restoreError = true;
      var msg = (response && response.data && response.data.message) ?
        response.data.message :
        $filter('translate')('settings.backup.restore_failed_generic');
      $scope.restoreErrorMessage = msg;
    }, function (evt) {
      if (evt.total > 0) {
        $scope.progressPercentage = Math.min(99, parseInt(100.0 * evt.loaded / evt.total, 10));
      }
    });
  };

  /**
   * Clears currently selected backup file.
   */
  $scope.clearFile = function () {
    $scope.backupFile = null;
    $scope.restoreError = false;
    $scope.restoreSuccess = false;
  };

  /**
   * Refreshes the application.
   */
  $scope.reloadPage = function () {
    $window.location.reload();
  };

  $scope.loadInfo();
});
