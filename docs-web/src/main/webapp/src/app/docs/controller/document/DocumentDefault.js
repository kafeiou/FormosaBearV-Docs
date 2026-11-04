'use strict';

/**
 * Document default controller.
 */
angular.module('docs').controller('DocumentDefault', function ($scope, $rootScope, $state, Restangular, Upload, $translate, $uibModal, $dialog, User, $interval) {
  var processingPollTimer = null;

  var stopProcessingPoll = function () {
    if (processingPollTimer) {
      $interval.cancel(processingPollTimer);
      processingPollTimer = null;
    }
  };

  var startProcessingPoll = function () {
    if (processingPollTimer) {
      return;
    }
    processingPollTimer = $interval(function () {
      if (!$scope.files || !_.some($scope.files, { processing: true })) {
        stopProcessingPoll();
        return;
      }
      Restangular.one('file/list').get().then(function (data) {
        var remoteMap = _.indexBy(data.files, 'id');
        var stillProcessing = false;
        _.each($scope.files, function (localFile) {
          if (localFile.id && remoteMap[localFile.id]) {
            var remoteFile = remoteMap[localFile.id];
            if (localFile.processing && !remoteFile.processing) {
              localFile.processing = false;
              localFile.thumbTimestamp = new Date().getTime();
            } else if (remoteFile.processing) {
              localFile.processing = true;
              stillProcessing = true;
            }
          }
        });
        if (!stillProcessing) {
          stopProcessingPoll();
        }
      });
    }, 1500);
  };

  $scope.$on('$destroy', function () {
    stopProcessingPoll();
  });

  // Load user audit log
  Restangular.one('auditlog').get().then(function (data) {
    $scope.logs = data.logs;
  });

  // Load unlinked files
  $scope.loadFiles = function () {
    Restangular.one('file/list').get().then(function (data) {
      var now = new Date().getTime();
      _.each(data.files, function (f) {
        f.thumbTimestamp = now;
      });
      $scope.files = data.files;
      if (_.some($scope.files, { processing: true })) {
        startProcessingPoll();
      }
    });
  };
  $scope.loadFiles();

  // File has been drag & dropped
  $scope.fileDropped = function (files) {
    if (files && files.length) {
      $scope.files = $scope.files || [];
      // Adding files to the UI
      var newfiles = [];
      _.each(files, function (file) {
        var newfile = {
          progress: 0,
          name: file.name,
          create_date: new Date().getTime(),
          mimetype: file.type,
          status: $translate.instant('document.default.upload_pending')
        };
        $scope.files.push(newfile);
        newfiles.push(newfile);
      });

      // Uploading files sequentially
      var key = 0;
      var then = function () {
        if (files[key]) {
          $scope.uploadFile(files[key], newfiles[key++]).then(then);
        }
      };
      then();
    }
  };

  // Upload a file
  $scope.uploadFile = function (file, newfile) {
    // Upload the file
    newfile.status = $translate.instant('document.default.upload_progress');
    return Upload.upload({
      method: 'PUT',
      url: '../api/file',
      file: file
    })
        .progress(function (e) {
          newfile.progress = parseInt(100.0 * e.loaded / e.total);
        })
        .success(function (data) {
          // Update local model with real data
          newfile.id = data.id;
          newfile.size = data.size;
          newfile.processing = true;
          newfile.thumbTimestamp = new Date().getTime();
          startProcessingPoll();
          // New file uploaded, increase used quota
          $rootScope.userInfo.storage_current += data.size;
        })
        .error(function (data) {
          newfile.status = $translate.instant('document.default.upload_error');
          if (data.type === 'QuotaReached') {
            newfile.status += ' - ' + $translate.instant('document.default.upload_error_quota');
          }else if (data.type=== 'FileNameDuplicate'){
            newfile.status += ' - ' + $translate.instant('document.default.upload_error_duplicate');
          }
        });
  };

  // Navigate to the selected file
  $scope.openFile = function (file) {
    $state.go('document.default.file', { fileId: file.id })
  };

  // Delete a file
  $scope.deleteFile = function ($event, file) {
    $event.stopPropagation();

    Restangular.one('file', file.id).remove().then(function () {
      // File deleted, decrease used quota
      $rootScope.userInfo.storage_current -= file.size;

      // Update local data
      $scope.loadFiles();
    });
    return false;
  };

  // Returns checked files
  $scope.checkedFiles = function () {
    return _.where($scope.files, { checked: true });
  };

  // Change checked status
  $scope.changeChecked = function (checked) {
    _.each($scope.files, function (file) {
      file.checked = checked;
    })
  };

  // Add a document with checked files
  $scope.addDocument = function () {
    $state.go('document.add', { files: _.pluck($scope.checkedFiles(), 'id') });
  };

  // Load active routes
  Restangular.one('document/list').get({
    asc: false,
    sort_column: 3,
    limit: 10,
    search: 'workflow:me'
  }).then(function (data) {
    $scope.documentsWorkflow = data.documents;
  });

  // Onboarding
  $translate('onboarding.step1.title').then(function () {
    User.userInfo().then(function(userData) {
      if (!userData.onboarding || $(window).width() < 1000) {
        return;
      }
      Restangular.one('user').post('onboarded');
      $rootScope.userInfo.onboarding = false;

      $rootScope.onboardingEnabled = true;

      $rootScope.onboardingSteps = [
        {
          title: $translate.instant('onboarding.step1.title'),
          description: $translate.instant('onboarding.step1.description'),
          position: 'centered',
          width: 300
        },
        {
          title: $translate.instant('onboarding.step2.title'),
          description: $translate.instant('onboarding.step2.description'),
          attachTo: '#document-add-btn',
          position: 'right',
          width: 300
        },
        {
          title: $translate.instant('onboarding.step3.title'),
          description: $translate.instant('onboarding.step3.description'),
          attachTo: '#quick-upload-zone',
          position: 'left',
          width: 300
        },
        {
          title: $translate.instant('onboarding.step4.title'),
          description: $translate.instant('onboarding.step4.description'),
          attachTo: '#search-box',
          position: 'right',
          width: 300
        },
        {
          title: $translate.instant('onboarding.step5.title'),
          description: $translate.instant('onboarding.step5.description'),
          attachTo: '#navigation-tag',
          position: "right",
          width: 300
        }
      ];
    });
  });
});