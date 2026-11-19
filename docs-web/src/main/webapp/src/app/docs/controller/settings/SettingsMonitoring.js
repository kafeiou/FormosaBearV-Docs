'use strict';

/**
 * Settings monitoring controller.
 */
angular.module('docs').controller('SettingsMonitoring', function($scope, $interval, Restangular) {
  var pollPromise = null;
  var wasRunning = false;

  $scope.ocrMode = 'NONE';
  $scope.reindexStatus = null;

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

  $scope.$on('$destroy', function() {
    stopPolling();
  });

  loadAppInfo();
  loadLogs();
  loadReindexStatus();
});
