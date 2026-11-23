'use strict';

/**
 * Settings shared links controller.
 */
angular.module('docs').controller('SettingsShare', function($scope, $rootScope, $location, $dialog, $translate, $timeout, Restangular, User) {
  $scope.shares = [];
  $scope.loading = true;
  $scope.scopeMode = 'mine';
  $scope.searchQuery = '';
  $scope.copiedShareId = null;

  User.userInfo().then(function(data) {
    $scope.isAdmin = data.base_functions && data.base_functions.indexOf('ADMIN') !== -1;
  });

  $scope.loadShares = function() {
    $scope.loading = true;
    Restangular.one('share/list').get({ scope: $scope.scopeMode }).then(function(data) {
      $scope.shares = data.shares || [];
      $scope.loading = false;
    }, function() {
      $scope.shares = [];
      $scope.loading = false;
    });
  };

  $scope.setScope = function(mode) {
    if ($scope.scopeMode === mode) {
      return;
    }
    $scope.scopeMode = mode;
    $scope.loadShares();
  };

  $scope.getShareUrl = function(share) {
    var base = $location.absUrl().split('#')[0];
    return base + 'share.html#/share/' + share.document_id + '/' + share.id;
  };

  $scope.copyShareUrl = function(share) {
    var url = $scope.getShareUrl(share);
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(url).then(function() {
        $scope.$applyAsync(function() {
          $scope.copiedShareId = share.id;
          $timeout(function() {
            if ($scope.copiedShareId === share.id) {
              $scope.copiedShareId = null;
            }
          }, 2000);
        });
      });
    } else {
      var ta = document.createElement('textarea');
      ta.value = url;
      ta.style.position = 'fixed';
      ta.style.opacity = '0';
      document.body.appendChild(ta);
      ta.select();
      try {
        document.execCommand('copy');
        $scope.copiedShareId = share.id;
        $timeout(function() {
          if ($scope.copiedShareId === share.id) {
            $scope.copiedShareId = null;
          }
        }, 2000);
      } finally {
        document.body.removeChild(ta);
      }
    }
  };

  $scope.toggleAllowExport = function(share) {
    var nextVal = !share.allow_export;
    Restangular.one('share', share.id).post('', {
      allow_export: nextVal
    }).then(function() {
      share.allow_export = nextVal;
    });
  };

  $scope.deleteShare = function(share) {
    var title = $translate.instant('settings.share.revoke_title');
    var msg = $translate.instant('settings.share.revoke_confirm', {
      title: share.document_title || share.document_id,
      name: share.name || $translate.instant('settings.share.unnamed')
    });
    var btns = [
      { result: 'cancel', label: $translate.instant('cancel') },
      { result: 'ok', label: $translate.instant('ok'), cssClass: 'btn-danger' }
    ];
    $dialog.messageBox(title, msg, btns, function(result) {
      if (result === 'ok') {
        Restangular.one('share', share.id).remove().then(function() {
          $scope.shares = _.reject($scope.shares, function(s) {
            return s.id === share.id;
          });
        });
      }
    });
  };

  $scope.matchesQuery = function(share) {
    if (!$scope.searchQuery || !$scope.searchQuery.trim()) {
      return true;
    }
    var q = $scope.searchQuery.trim().toLowerCase();
    var text = (
      (share.document_title || '') + ' ' +
      (share.name || '') + ' ' +
      (share.owner || '') + ' ' +
      ($rootScope.formatUserFull ? $rootScope.formatUserFull(share.owner) : '')
    ).toLowerCase();
    return text.indexOf(q) !== -1;
  };

  $scope.loadShares();
});
