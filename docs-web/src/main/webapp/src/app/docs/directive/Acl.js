'use strict';

/**
 * ACL directive.
 */
angular.module('docs').directive('acl', function() {
  return {
    restrict: 'E',
    template: '<span ng-show="data.type" uib-tooltip="{{ (data.type == \'USER\' && !picker) ? $root.getUserTooltip(data.name, data.display_name) : (data.type == \'GROUP\' ? $root.getGroupTooltip(data.name, data.description) : \'\') }}" tooltip-append-to-body="true" ng-class="{ \'text-muted\': data.type == \'USER\' && data.disabled }" ng-style="(data.type == \'USER\' && data.disabled) ? {\'text-decoration\': \'line-through\'} : {}"><em>{{ \'acl.\' + data.type | translate }}</em> {{ data.type == \'USER\' ? (picker ? $root.formatUserFull(data.name, data.display_name) : $root.formatUserDisplay(data.name, data.display_name)) : (data.type == \'GROUP\' ? $root.formatGroupFull(data.name, data.description) : data.name) }}</span>',
    replace: true,
    scope: {
      data: '=',
      picker: '@'
    }
  }
});