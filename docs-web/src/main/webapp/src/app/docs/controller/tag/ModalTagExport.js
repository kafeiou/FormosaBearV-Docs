'use strict';

/**
 * Tag report export modal controller.
 */
angular.module('docs').controller('ModalTagExport', function($scope, $uibModalInstance, $window, $translate, Restangular, initialTagId) {
  $scope.tagFilter = '';
  $scope.selectedMap = {};
  $scope.selectableTags = [];

  $scope.options = {
    includeChildren: true,
    format: 'pdf',
    includeDate: true,
    includeDescription: true,
    includeSize: false,
    linkMode: 'preview'
  };

  if (initialTagId) {
    $scope.selectedMap[initialTagId] = true;
  }

  var tagComparator = function(a, b) {
    var orderA = (a.order !== undefined && a.order !== null) ? a.order : 999999999;
    var orderB = (b.order !== undefined && b.order !== null) ? b.order : 999999999;
    if (orderA !== orderB) {
      return orderA - orderB;
    }
    var dateA = a.create_date || 0;
    var dateB = b.create_date || 0;
    if (dateA !== dateB) {
      return dateA - dateB;
    }
    return (a.name || '').localeCompare(b.name || '');
  };

  Restangular.one('tag/list').get().then(function(data) {
    var tags = data.tags || [];
    var byId = {};
    _.each(tags, function(t) {
      byId[t.id] = t;
    });

    var getChildren = function(parentId) {
      return _.filter(tags, function(t) {
        if (!parentId) {
          return !t.parent || !byId[t.parent];
        }
        return t.parent === parentId;
      }).sort(tagComparator);
    };

    var list = [];
    var traverse = function(parentId, prefixCode, pathNames) {
      var children = getChildren(parentId);
      _.each(children, function(child, idx) {
        var code = prefixCode ? (prefixCode + '.' + (idx + 1)) : String(idx + 1);
        var currentPath = pathNames.concat([child.name]);
        if (!child.is_container) {
          list.push({
            id: child.id,
            name: child.name,
            color: child.color || '#3a87ad',
            code: code,
            pathText: currentPath.join(' > ')
          });
        }
        traverse(child.id, code, currentPath);
      });
    };
    traverse(null, '', []);
    $scope.selectableTags = list;

    if (!initialTagId && list.length > 0) {
      _.each(list, function(item) {
        $scope.selectedMap[item.id] = true;
      });
    }
  });

  $scope.selectAllTags = function() {
    _.each($scope.selectableTags, function(item) {
      if (!$scope.tagFilter || item.pathText.toLowerCase().indexOf($scope.tagFilter.toLowerCase()) !== -1) {
        $scope.selectedMap[item.id] = true;
      }
    });
  };

  $scope.clearAllTags = function() {
    _.each($scope.selectableTags, function(item) {
      if (!$scope.tagFilter || item.pathText.toLowerCase().indexOf($scope.tagFilter.toLowerCase()) !== -1) {
        $scope.selectedMap[item.id] = false;
      }
    });
  };

  $scope.hasSelectedTags = function() {
    return _.some($scope.selectableTags, function(item) {
      return !!$scope.selectedMap[item.id];
    });
  };

  $scope.exportReport = function() {
    var selectedIds = [];
    _.each($scope.selectableTags, function(item) {
      if ($scope.selectedMap[item.id]) {
        selectedIds.push(item.id);
      }
    });
    if (selectedIds.length === 0) {
      return;
    }

    var baseUrl = $window.location.origin + $window.location.pathname.replace(/\/index\.html$/, '').replace(/\/$/, '');
    var currentLang = $translate.use() || localStorage.overrideLang || 'zh_TW';
    var params = [
      'tag_id=' + encodeURIComponent(selectedIds.join(',')),
      'format=' + encodeURIComponent($scope.options.format),
      'include_children=' + ($scope.options.includeChildren ? 'true' : 'false'),
      'include_date=' + ($scope.options.includeDate ? 'true' : 'false'),
      'include_description=' + ($scope.options.includeDescription ? 'true' : 'false'),
      'include_size=' + ($scope.options.includeSize ? 'true' : 'false'),
      'link_mode=' + encodeURIComponent($scope.options.linkMode),
      'lang=' + encodeURIComponent(currentLang),
      'base_url=' + encodeURIComponent(baseUrl)
    ];

    var exportUrl = '../api/tag/export?' + params.join('&');
    $window.open(exportUrl, '_blank');
    $uibModalInstance.close();
  };

  $scope.close = function() {
    $uibModalInstance.dismiss('cancel');
  };
});
