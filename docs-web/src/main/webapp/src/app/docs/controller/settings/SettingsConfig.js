'use strict';

/**
 * Settings config page controller.
 */
angular.module('docs').controller('SettingsConfig', function($scope, $rootScope, Restangular, $dialog, $translate) {
  // Get the app configuration
  Restangular.one('app').get().then(function (data) {
    $rootScope.app = data;
    $scope.general = {
      default_language: data.default_language,
      base_url: data.base_url || 'http://localhost:8080'
    };
  });

  // Enable/disable guest login
  $scope.changeGuestLogin = function (enabled) {
    Restangular.one('app').post('guest_login', {
      enabled: enabled
    }).then(function () {
      $scope.app.guest_login = enabled;
    });
  };

  // Enable/disable OCR
  $scope.changeOcrEnabled = function (enabled) {
    Restangular.one('app').post('ocr', {
      enabled: enabled
    }).then(function () {
      $scope.app.ocr_enabled = enabled;
    });
  };

  // Enable/disable permanent file deletion on disk (file.delete)
  $scope.changeFileDelete = function (enabled) {
    Restangular.one('app').post('file_delete', {
      enabled: enabled
    }).then(function () {
      $scope.app.file_delete = enabled;
    });
  };

  // Enable/disable duplicate filenames (file.duplicate)
  
  // Enable/disable file download (file.download)
  $scope.changeFileDownload = function (enabled) {
    Restangular.one('app').post('file_download', {
      enabled: enabled
    }).then(function () {
      $scope.app.file_download = enabled;
      $rootScope.app.file_download = enabled;
    });
  };

  $scope.changeFileDuplicate = function (enabled) {
    Restangular.one('app').post('file_duplicate', {
      enabled: enabled
    }).then(function () {
      $scope.app.file_duplicate = enabled;
    });
  };

  // One-time lock for file encryption mode (file.encrypt)
  $scope.lockFileEncrypt = function (enabled) {
    var title = $translate.instant('settings.config.file_encrypt_confirm_title');
    var modeText = enabled ?
      $translate.instant('settings.config.file_encrypt_mode_encrypted') :
      $translate.instant('settings.config.file_encrypt_mode_unencrypted');
    var msg = $translate.instant('settings.config.file_encrypt_confirm_message', { mode: modeText });
    var btns = [
      { result: 'cancel', label: $translate.instant('cancel') },
      { result: 'ok', label: $translate.instant('ok'), cssClass: 'btn-primary' }
    ];

    $dialog.messageBox(title, msg, btns, function (result) {
      if (result === 'ok') {
        Restangular.one('app').post('file_encrypt', {
          enabled: enabled
        }).then(function () {
          $scope.app.file_encrypt = enabled;
          $scope.app.file_encrypt_locked = true;
        });
      }
    });
  };

  // Fetch the current theme configuration
  Restangular.one('theme').get().then(function (data) {
    $scope.theme = data;
    $rootScope.appName = $scope.theme.name;
  });

  // Update the theme
  $scope.update = function () {
    $scope.theme.name = $scope.theme.name.length === 0 ? 'FormosaBearV-docs' : $scope.theme.name;
    Restangular.one('theme').post('', $scope.theme).then(function () {
      var stylesheet = $('#theme-stylesheet')[0];
      stylesheet.href = stylesheet.href.replace(/\?.*|$/, '?' + new Date().getTime());
      $rootScope.appName = $scope.theme.name;
    });
  };

  // Send an image
  $scope.sendingImage = false;
  $scope.sendImage = function (type, image) {
    // Build the payload
    var formData = new FormData();
    formData.append('image', image);

    // Send the file
    var done = function() {
      $scope.$apply(function() {
        $scope.sendingImage = false;
        $scope[type] = null;
      });
    };
    $scope.sendingImage = true;
    $.ajax({
      type: 'PUT',
      url: '../api/theme/image/' + type,
      data: formData,
      cache: false,
      contentType: false,
      processData: false,
      success: function() {
        done();
      },
      error: function() {
        done();
      }
    });
  };

  // Load SMTP config
  Restangular.one('app/config_smtp').get().then(function (data) {
    $scope.smtp = data;
    if (!$scope.smtp.from_name) {
      $scope.smtp.from_name = 'FormosaBearV';
    }
  });

  // Edit SMTP config
  $scope.editSmtpConfig = function () {
    Restangular.one('app').post('config_smtp', $scope.smtp);
  };

  // Edit general config
  $scope.editGeneralConfig = function () {
    Restangular.one('app').post('config', $scope.general);
  };

  // Get the webhooks
  $scope.loadWebhooks = function () {
    Restangular.one('webhook').get().then(function (data) {
      $scope.webhooks = data.webhooks;
    });
  };

  $scope.loadWebhooks();

  // Add a webhook
  $scope.webhook = {
    event: 'DOCUMENT_CREATED'
  };
  $scope.addWebhook = function () {
    Restangular.one('webhook').put($scope.webhook).then(function () {
      $scope.loadWebhooks();
    })
  };

  // Delete a webhook
  $scope.deleteWebhook = function (webhook) {
    Restangular.one('webhook', webhook.id).remove().then(function () {
      $scope.loadWebhooks();
    });
  };
});
