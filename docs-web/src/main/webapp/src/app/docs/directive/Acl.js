'use strict';

/**
 * ACL directive.
 */
angular.module('docs').directive('acl', function() {
  return {
    restrict: 'E',
    template: '<span ng-show="data.type" uib-tooltip="{{ (data.type == \'USER\' && !picker) ? $root.getUserTooltip(data.name, data.display_name) : \'\' }}" tooltip-append-to-body="true"><em>{{ \'acl.\' + data.type | translate }}</em> {{ data.type == \'USER\' ? (picker ? $root.formatUserFull(data.name, data.display_name) : $root.formatUserDisplay(data.name, data.display_name)) : data.name }}</span>',
    replace: true,
    scope: {
      data: '=',
      picker: '@'
    }
  }
});