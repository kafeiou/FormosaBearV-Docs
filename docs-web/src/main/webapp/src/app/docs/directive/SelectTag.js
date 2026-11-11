'use strict';

/**
 * Tag selection directive.
 */
angular.module('docs').directive('selectTag', function($templateCache) {
  $templateCache.put('selectTagNodeRenderer.html',
    '<div class="select-tag-node-row" ng-class="{ \'selected\': isSelected(node) }">' +
      '<span class="select-tag-toggle" ng-if="node.children && node.children.length > 0" ng-click="toggleExpand(node, $event)">' +
        '<i class="fas" ng-class="isExpanded(node) ? \'fa-caret-down\' : \'fa-caret-right\'"></i>' +
      '</span>' +
      '<span class="select-tag-toggle-placeholder" ng-if="!node.children || node.children.length === 0"></span>' +
      '<span class="select-tag-item" ng-click="toggleTagSelection(node, $event)" uib-tooltip="{{ getTagTooltip(node) }}" tooltip-append-to-body="true">' +
        '<i class="far" ng-class="isSelected(node) ? \'fa-check-square text-primary\' : \'fa-square text-muted\'" style="margin-right: 6px;"></i>' +
        '<span class="label label-info" ng-style="{ \'background\': node.color }" invert-text-color="{{ node.color }}">{{ node.name }}</span>' +
        '<span class="text-muted small" style="margin-left: 6px;" ng-if="node.children && node.children.length > 0">' +
          '(<i class="far fa-folder"></i> {{ node.children.length }})' +
        '</span>' +
      '</span>' +
    '</div>' +
    '<ul class="list-unstyled select-tag-children" ng-if="node.children && node.children.length > 0" ng-show="isExpanded(node)">' +
      '<li ng-repeat="node in node.children" ng-show="matchesFilter(node)" ng-include="\'selectTagNodeRenderer.html\'"></li>' +
    '</ul>'
  );

  $templateCache.put('selectTagTypeaheadTemplate.html',
    '<a href="javascript:void(0);" style="display: flex; align-items: center; justify-content: space-between; padding: 4px 10px;">' +
      '<span class="label label-info" ng-style="{ \'background\': match.model.color }" invert-text-color="{{ match.model.color }}">{{ match.model.name }}</span>' +
      '<span class="text-muted small" ng-if="match.model.creator" style="margin-left: 12px;">' +
        '<i class="fas fa-user" style="font-size: 85%;"></i> {{ match.model.creator }}' +
      '</span>' +
    '</a>'
  );

  return {
    restrict: 'E',
    templateUrl: 'partial/docs/directive.selecttag.html',
    replace: true,
    scope: {
      tags: '=',
      ref: '@',
      ngDisabled: '='
    },
    controller: function($scope, Restangular) {
      $scope.showTree = false;
      $scope.expandedTags = {};
      $scope.rootTags = [];
      $scope.allTags = [];

      /**
       * Build hierarchical tag tree and full path labels for search/typeahead.
       */
      var buildTagHierarchy = function(tags) {
        var tagMap = {};
        _.each(tags, function(tag) {
          tag.children = [];
          tagMap[tag.id] = tag;
        });

        // Compute full hierarchical path (e.g. "Parent > Child")
        _.each(tags, function(tag) {
          var parts = [tag.name];
          var curr = tag;
          var visited = {};
          visited[curr.id] = true;
          while (curr.parent && tagMap[curr.parent] && !visited[curr.parent]) {
            curr = tagMap[curr.parent];
            visited[curr.id] = true;
            parts.unshift(curr.name);
          }
          tag.fullLabel = parts.join(' > ');
        });

        var roots = [];
        _.each(tags, function(tag) {
          if (tag.parent && tagMap[tag.parent]) {
            tagMap[tag.parent].children.push(tag);
          } else {
            roots.push(tag);
          }
        });

        var sortNodes = function(nodes) {
          nodes.sort(function(a, b) {
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
          });
          _.each(nodes, function(n) {
            if (n.children && n.children.length > 0) {
              sortNodes(n.children);
            }
          });
        };
        sortNodes(roots);
        $scope.rootTags = roots;
      };

      // Retrieve tags
      Restangular.one('tag/list').get().then(function(data) {
        $scope.allTags = data.tags || [];
        buildTagHierarchy($scope.allTags);
      });

      /**
       * Toggle tag tree panel visibility.
       */
      $scope.toggleTagTree = function() {
        $scope.showTree = !$scope.showTree;
      };

      /**
       * Toggle expand/collapse of a single parent tag node.
       */
      $scope.toggleExpand = function(node, $event) {
        if ($event) {
          $event.stopPropagation();
          $event.preventDefault();
        }
        $scope.expandedTags[node.id] = !$scope.expandedTags[node.id];
      };

      /**
       * Check if a tag node is expanded (auto-expands when user is typing a filter).
       */
      $scope.isExpanded = function(node) {
        if ($scope.input && angular.isString($scope.input) && $scope.input.trim().length > 0) {
          return true;
        }
        return !!$scope.expandedTags[node.id];
      };

      /**
       * Expand all parent tags in the picker tree.
       */
      $scope.expandAll = function($event) {
        if ($event) {
          $event.stopPropagation();
          $event.preventDefault();
        }
        _.each($scope.allTags, function(tag) {
          if (tag.children && tag.children.length > 0) {
            $scope.expandedTags[tag.id] = true;
          }
        });
      };

      /**
       * Collapse all parent tags in the picker tree.
       */
      $scope.collapseAll = function($event) {
        if ($event) {
          $event.stopPropagation();
          $event.preventDefault();
        }
        $scope.expandedTags = {};
      };

      /**
       * Check whether a node or any of its descendants matches the current search input.
       */
      $scope.matchesFilter = function(node) {
        if (!$scope.input || !angular.isString($scope.input) || $scope.input.trim().length === 0) {
          return true;
        }
        var q = $scope.input.trim().toLowerCase();
        var checkNode = function(n) {
          if (n.name.toLowerCase().indexOf(q) !== -1 ||
              (n.fullLabel && n.fullLabel.toLowerCase().indexOf(q) !== -1)) {
            return true;
          }
          if (n.children && n.children.length > 0) {
            return _.some(n.children, checkNode);
          }
          return false;
        };
        return checkNode(node);
      };

      /**
       * Check if a tag is currently selected on the document.
       */
      $scope.isSelected = function(tag) {
        if (!$scope.tags || !tag) {
          return false;
        }
        return _.some($scope.tags, function(t) {
          return t.id === tag.id;
        });
      };

      /**
       * Toggle selection of a tag from the tree picker.
       */
      $scope.toggleTagSelection = function(tag, $event) {
        if ($event) {
          $event.stopPropagation();
          $event.preventDefault();
        }
        if ($scope.ngDisabled || !tag) {
          return;
        }
        if ($scope.isSelected(tag)) {
          $scope.deleteTag(tag);
        } else {
          if (!$scope.tags) {
            $scope.tags = [];
          }
          $scope.tags.push({
            id: tag.id,
            name: tag.name,
            color: tag.color,
            creator: tag.creator
          });
        }
      };

      /**
       * Add a tag from typeahead selection or input.
       */
      $scope.addTag = function($item, $event) {
        var tag = ($item && $item.id) ? $item : _.find($scope.allTags, function(t) {
          return t.name === $scope.input || t.fullLabel === $scope.input;
        });

        if (tag) {
          if (!$scope.isSelected(tag)) {
            if (!$scope.tags) {
              $scope.tags = [];
            }
            $scope.tags.push({
              id: tag.id,
              name: tag.name,
              color: tag.color,
            creator: tag.creator
            });
          }
          $scope.input = '';
        }

        if ($event && $event.preventDefault) {
          $event.preventDefault();
        }
      };

      /**
       * Remove a tag.
       */
      $scope.deleteTag = function(deleteTag) {
        $scope.tags = _.reject($scope.tags, function(tag) {
          return tag.id === deleteTag.id;
        });
      };
    },
    link: function(scope, element, attr, ctrl) {
    }
  };
});
