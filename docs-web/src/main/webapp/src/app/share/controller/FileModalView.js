'use strict';

/**
 * File modal view controller.
 */
angular.module('share').controller('FileModalView', function($uibModalInstance, $scope, $state, $stateParams, Restangular, $transitions, $rootScope) {
  if (!$rootScope.app) {
    Restangular.one('app').get().then(function(appData) {
      $rootScope.app = appData;
    });
  }
  if (!$rootScope.userInfo) {
    Restangular.one('user').get().then(function(userData) {
      $rootScope.userInfo = userData;
    }, function() {});
  }

  var EN_MONTHS_SHORT = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
  var EN_MONTHS_FULL = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
  var EN_DAYS_SHORT = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
  var EN_DAYS_FULL = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];

  var formatEnglishDate = function (date, pattern) {
    var d = date || new Date();
    var fmt = (pattern && angular.isString(pattern) && pattern.trim().length > 0) ? pattern.trim() : 'dd MMM yyyy';
    var pad2 = function (n) { return n < 10 ? '0' + n : '' + n; };
    return fmt.replace(/yyyy|YYYY|yy|YY|MMMM|MMM|MM|M|dddd|ddd|EEEE|EEE|dd|DD|d|D/g, function (token) {
      switch (token) {
        case 'yyyy':
        case 'YYYY':
          return '' + d.getFullYear();
        case 'yy':
        case 'YY':
          return pad2(d.getFullYear() % 100);
        case 'MMMM':
          return EN_MONTHS_FULL[d.getMonth()];
        case 'MMM':
          return EN_MONTHS_SHORT[d.getMonth()];
        case 'MM':
          return pad2(d.getMonth() + 1);
        case 'M':
          return '' + (d.getMonth() + 1);
        case 'dddd':
        case 'EEEE':
          return EN_DAYS_FULL[d.getDay()];
        case 'ddd':
        case 'EEE':
          return EN_DAYS_SHORT[d.getDay()];
        case 'dd':
        case 'DD':
          return pad2(d.getDate());
        case 'd':
        case 'D':
          return '' + d.getDate();
        default:
          return token;
      }
    });
  };

  var escapeXml = function (str) {
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&apos;');
  };

  $scope.isWatermarkEnabled = function () {
    return !!($rootScope.app && $rootScope.app.preview_watermark_enabled);
  };

  $scope.getWatermarkStyle = function () {
    if (!$scope.isWatermarkEnabled()) {
      return {};
    }
    var username = ($rootScope.userInfo && $rootScope.userInfo.username && !$rootScope.userInfo.anonymous)
      ? $rootScope.userInfo.username
      : 'Guest';
    var dateStr = formatEnglishDate(new Date(), $rootScope.app.preview_watermark_date_format);
    var text = escapeXml(username + ' \u00B7 ' + dateStr);
    var svg = '<svg xmlns="http://www.w3.org/2000/svg" width="320" height="200">' +
      '<text x="50%" y="50%" dominant-baseline="middle" text-anchor="middle" ' +
      'fill="rgba(128, 128, 128, 0.24)" font-family="Arial, Helvetica, sans-serif" font-size="16" font-weight="bold" ' +
      'transform="rotate(-30 160 100)">' + text + '</text>' +
      '</svg>';
    return {
      'background-image': 'url("data:image/svg+xml;utf8,' + encodeURIComponent(svg) + '")',
      'background-repeat': 'repeat',
      'background-position': 'center center'
    };
  };

  // Load files
  Restangular.one('file/list').get({ id: $stateParams.documentId, share: $stateParams.shareId }).then(function(data) {
    $scope.files = data.files;

    // Search current file
    _.each($scope.files, function(value) {
      if (value.id === $stateParams.fileId) {
        $scope.file = value;
      }
    });
  });

  /**
   * Navigate to the next file.
   */
  $scope.nextFile = function() {
    _.each($scope.files, function(value, key) {
      if (value.id === $stateParams.fileId) {
        var next = $scope.files[key + 1];
        if (next) {
          $state.go('share.file', { documentId: $stateParams.documentId, shareId: $stateParams.shareId, fileId: next.id });
        }
      }
    });
  };

  /**
   * Navigate to the previous file.
   */
  $scope.previousFile = function() {
    _.each($scope.files, function(value, key) {
      if (value.id === $stateParams.fileId) {
        var previous = $scope.files[key - 1];
        if (previous) {
          $state.go('share.file', { documentId: $stateParams.documentId, shareId: $stateParams.shareId,  fileId: previous.id });
        }
      }
    });
  };

  /**
   * Open the file in a new window.
   */
  $scope.openFile = function() {
    window.open('../api/file/' + $stateParams.fileId + '/data?share=' + $stateParams.shareId);
  };

  /**
   * Print the file.
   */
  $scope.printFile = function() {
    var popup = window.open('../api/file/' + $stateParams.fileId + '/data', '_blank');
    popup.onload = function () {
      popup.print();
      popup.close();
    }
  };

  /**
   * Close the file preview.
   */
  $scope.closeFile = function () {
    $uibModalInstance.dismiss();
  };

  // Close the modal when the user exits this state
  var off = $transitions.onStart({}, function(transition) {
    if (!$uibModalInstance.closed) {
      if (transition.to().name === $state.current.name) {
        $uibModalInstance.close();
      } else {
        $uibModalInstance.dismiss();
      }
    }
    off();
  });
});