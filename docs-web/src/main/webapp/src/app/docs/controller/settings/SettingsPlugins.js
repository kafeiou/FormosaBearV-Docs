'use strict';

/**
 * Settings plugins management controller.
 */
angular.module('docs').controller('SettingsPlugins', function($scope, $rootScope, $http, Restangular, $dialog, $translate) {
  $scope.plugins = [];
  $scope.loading = true;
  $scope.selectedFile = null;
  $scope.uploading = false;
  $scope.uploadError = null;
  $scope.uploadSuccess = null;

  $scope.loadPlugins = function() {
    $scope.loading = true;
    Restangular.one('plugin/list').get().then(function(data) {
      $scope.loading = false;
      $scope.plugins = data.plugins || [];
    }, function() {
      $scope.loading = false;
    });
  };
  $scope.loadPlugins();

  $scope.onPluginFileSelected = function(files) {
    $scope.$applyAsync(function() {
      $scope.selectedFile = files && files.length > 0 ? files[0] : null;
      $scope.uploadError = null;
      $scope.uploadSuccess = null;
    });
  };

  $scope.uploadPlugin = function() {
    if (!$scope.selectedFile) return;
    $scope.uploading = true;
    $scope.uploadError = null;
    $scope.uploadSuccess = null;

    var fd = new FormData();
    fd.append('file', $scope.selectedFile);
    $http.post('../api/plugin/upload', fd, {
      transformRequest: angular.identity,
      headers: { 'Content-Type': undefined }
    }).then(function(resp) {
      $scope.uploading = false;
      $scope.selectedFile = null;
      var inputEl = document.getElementById('inputPluginZip');
      if (inputEl) inputEl.value = '';
      var p = resp.data;
      $scope.uploadSuccess = $translate.instant('settings.plugins.upload_success', {
        name: p.name,
        version: p.version
      });
      $scope.loadPlugins();
      $rootScope.$broadcast('pluginsUpdated');
    }, function(err) {
      $scope.uploading = false;
      $scope.uploadError = (err && err.data && err.data.message) || $translate.instant('settings.plugins.upload_failed');
    });
  };

  $scope.toggleEnable = function(plugin, enabled) {
    var action = enabled ? 'enable' : 'disable';
    Restangular.one('plugin', plugin.id).post(action, {}).then(function() {
      plugin.enabled = enabled;
      $rootScope.$broadcast('pluginsUpdated');
    });
  };

  $scope.uninstallPlugin = function(plugin) {
    var title = $translate.instant('settings.plugins.uninstall_title');
    var msg = $translate.instant('settings.plugins.uninstall_message', { name: plugin.name, version: plugin.version });
    var btns = [
      { result: 'cancel', label: $translate.instant('cancel') },
      { result: 'ok', label: $translate.instant('ok'), cssClass: 'btn-danger' }
    ];
    $dialog.messageBox(title, msg, btns, function(result) {
      if (result === 'ok') {
        Restangular.one('plugin', plugin.id).remove().then(function() {
          $scope.loadPlugins();
          $rootScope.$broadcast('pluginsUpdated');
        });
      }
    });
  };
});
