'use strict';

/**
 * Plugin host iframe container controller.
 */
angular.module('docs').controller('PluginView', function($scope, $rootScope, $stateParams, $sce, Restangular, $translate) {
  $scope.plugin = null;
  $scope.trustedEntryUrl = null;
  $scope.pluginError = null;

  Restangular.one('plugin/enabled').get().then(function(data) {
    var list = (data && data.plugins) || [];
    var found = _.findWhere(list, { id: $stateParams.id });
    if (!found) {
      $scope.pluginError = $translate.instant('plugin.not_available');
      return;
    }
    $scope.plugin = found;
    var buildIframeUrl = function() {
      var lang = $translate.use() || found.default_locale || 'en';
      var url = '../api/plugin/' + encodeURIComponent(found.id) + '/assets/' + found.entry +
                '?v=' + encodeURIComponent(found.version) + '&lang=' + encodeURIComponent(lang);
      $scope.trustedEntryUrl = $sce.trustAsResourceUrl(url);
    };
    buildIframeUrl();
    var unbindTranslate = $rootScope.$on('$translateChangeSuccess', buildIframeUrl);
    $scope.$on('$destroy', unbindTranslate);
  }, function() {
    $scope.pluginError = $translate.instant('plugin.not_available');
  });
});
