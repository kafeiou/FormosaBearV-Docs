'use strict';

/**
 * Settings monitoring controller.
 */
angular.module('docs').controller('SettingsMonitoring', function($scope, $interval, $translate, $dialog, Restangular) {
  var pollPromise = null;
  var wasRunning = false;

  $scope.ocrMode = 'NONE';
  $scope.reindexStatus = null;

  // Storage & DB integrity scan/clean state
  $scope.integrityScanning = false;
  $scope.integrityCleaning = false;
  $scope.integrityResult = null;
  $scope.integritySelectedFiles = {};
  $scope.integritySelectedDisk = {};
  $scope.integrityCleanMessage = null;
  $scope.integrityError = null;

  function loadAppInfo() {
    Restangular.one('app').get().then(function(data) {
      $scope.app = data;
    });
  }

  function loadLogs() {
    Restangular.one('app/log').get({
      limit: 100
    }).then(function(data) {
      $scope.logs = data.logs;
    });
  }

  function stopPolling() {
    if (pollPromise) {
      $interval.cancel(pollPromise);
      pollPromise = null;
    }
  }

  function startPolling() {
    if (pollPromise) {
      return;
    }
    pollPromise = $interval(function() {
      loadReindexStatus();
      loadAppInfo();
    }, 1500);
  }

  function loadReindexStatus() {
    Restangular.one('app/batch/reindex/status').get().then(function(data) {
      $scope.reindexStatus = data;
      if (data && data.running) {
        wasRunning = true;
        if (data.ocr_mode) {
          $scope.ocrMode = data.ocr_mode;
        }
        startPolling();
      } else {
        stopPolling();
        if (wasRunning) {
          wasRunning = false;
          loadAppInfo();
          loadLogs();
        }
      }
    });
  }

  $scope.startReindexing = function() {
    if ($scope.reindexStatus && $scope.reindexStatus.running) {
      return;
    }
    Restangular.one('app').post('batch/reindex', {
      ocr_mode: $scope.ocrMode
    }).then(function(data) {
      if (data && data.progress) {
        $scope.reindexStatus = data.progress;
      }
      wasRunning = true;
      startPolling();
      loadReindexStatus();
    });
  };

  $scope.scanIntegrity = function() {
    if ($scope.integrityScanning) {
      return;
    }
    $scope.integrityScanning = true;
    $scope.integrityError = null;
    $scope.integrityCleanMessage = null;
    Restangular.one('app/integrity/scan').get().then(function(data) {
      $scope.integrityResult = data;
      $scope.integritySelectedFiles = {};
      $scope.integritySelectedDisk = {};
      // Default-select orphaned historical versions and orphaned disk files
      _.each(data.orphaned_versions || [], function(item) {
        $scope.integritySelectedFiles[item.file_id] = true;
      });
      _.each(data.orphaned_disk_files || [], function(item) {
        $scope.integritySelectedDisk[item.relative_path] = true;
      });
      // Missing physical files: default-select only non-latest versions; leave latest_version unchecked for safety
      _.each(data.missing_files || [], function(item) {
        $scope.integritySelectedFiles[item.file_id] = !item.latest_version;
      });
      $scope.integrityScanning = false;
    }, function(err) {
      $scope.integrityScanning = false;
      $scope.integrityError = (err && err.data && err.data.message) || $translate.instant('error');
    });
  };

  $scope.getTotalIntegrityIssues = function() {
    if (!$scope.integrityResult) {
      return 0;
    }
    return ($scope.integrityResult.orphaned_versions ? $scope.integrityResult.orphaned_versions.length : 0)
      + ($scope.integrityResult.missing_files ? $scope.integrityResult.missing_files.length : 0)
      + ($scope.integrityResult.orphaned_disk_files ? $scope.integrityResult.orphaned_disk_files.length : 0);
  };

  $scope.getSelectedIntegrityCount = function() {
    var count = 0;
    _.each($scope.integritySelectedFiles, function(checked) {
      if (checked) count++;
    });
    _.each($scope.integritySelectedDisk, function(checked) {
      if (checked) count++;
    });
    return count;
  };

  $scope.toggleAllCategory = function(category, checked) {
    if (!$scope.integrityResult || !$scope.integrityResult[category]) {
      return;
    }
    if (category === 'orphaned_disk_files') {
      _.each($scope.integrityResult[category], function(item) {
        $scope.integritySelectedDisk[item.relative_path] = checked;
      });
    } else {
      _.each($scope.integrityResult[category], function(item) {
        $scope.integritySelectedFiles[item.file_id] = checked;
      });
    }
  };

  $scope.cleanSelectedIntegrity = function() {
    var fileIds = [];
    var diskPaths = [];
    _.each($scope.integritySelectedFiles, function(checked, id) {
      if (checked) fileIds.push(id);
    });
    _.each($scope.integritySelectedDisk, function(checked, p) {
      if (checked) diskPaths.push(p);
    });
    var total = fileIds.length + diskPaths.length;
    if (total === 0 || $scope.integrityCleaning) {
      return;
    }

    var title = $translate.instant('settings.monitoring.integrity_confirm_title');
    var msg = $translate.instant('settings.monitoring.integrity_confirm_message', {
      dbCount: fileIds.length,
      diskCount: diskPaths.length
    });
    var btns = [
      { result: 'cancel', label: $translate.instant('cancel') },
      { result: 'ok', label: $translate.instant('ok'), cssClass: 'btn-danger' }
    ];
    $dialog.messageBox(title, msg, btns, function(result) {
      if (result !== 'ok') {
        return;
      }
      $scope.integrityCleaning = true;
      $scope.integrityError = null;
      $scope.integrityCleanMessage = null;
      Restangular.one('app/integrity').post('clean', {
        file_ids: fileIds,
        disk_paths: diskPaths
      }).then(function(res) {
        $scope.integrityCleaning = false;
        $scope.integrityCleanMessage = $translate.instant('settings.monitoring.integrity_clean_success', {
          dbCount: res.cleaned_db_files || 0,
          diskCount: res.cleaned_disk_files || 0
        });
        $scope.scanIntegrity();
        loadAppInfo();
      }, function(err) {
        $scope.integrityCleaning = false;
        $scope.integrityError = (err && err.data && err.data.message) || $translate.instant('error');
      });
    });
  };

  $scope.$on('$destroy', function() {
    stopPolling();
  });

  loadAppInfo();
  loadLogs();
  loadReindexStatus();
});
