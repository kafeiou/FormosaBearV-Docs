'use strict';

/**
 * ACL edit directive.
 */
angular.module('docs').directive('aclEdit', function() {
  return {
    restrict: 'E',
    templateUrl: 'partial/docs/directive.acledit.html',
    replace: true,
    scope: {
      source: '=',
      acls: '=',
      writable: '=',
      creator: '='
    },
    controller: function($scope, Restangular, $q) {
      // Watch for ACLs change and group them for easy displaying
      $scope.$watch('acls', function(acls) {
        $scope.groupedAcls = _.groupBy(acls, function(acl) {
          return acl.id;
        });
      });
      
      // Initialize add ACL
      $scope.acl = { perm: 'READ' };
      $scope.allTargets = [];
      $scope.targetDropdownOpen = false;

      /**
       * Load all available ACL targets (users and groups) for dropdown selection.
       */
      $scope.loadAllTargets = function() {
        Restangular.one('acl/target/search')
          .get({ search: '' })
          .then(function(data) {
            var output = [];
            output.push.apply(output, _.map(data.users, function(user) {
              user.type = 'USER';
              return user;
            }));
            output.push.apply(output, _.map(data.groups, function(group) {
              group.type = 'GROUP';
              return group;
            }));
            $scope.allTargets = output;
          });
      };
      $scope.loadAllTargets();

      /**
       * Select a target from the dropdown list.
       */
      $scope.selectTarget = function(target, $event) {
        if ($event) {
          $event.preventDefault();
          $event.stopPropagation();
        }
        $scope.acl.target = angular.copy(target);
        $scope.targetDropdownOpen = false;
      };

      /**
       * Filter targets in the dropdown list based on current input.
       */
      $scope.filterTargetItem = function(item) {
        if (!$scope.acl || !$scope.acl.target) {
          return true;
        }
        if (angular.isString($scope.acl.target)) {
          var q = $scope.acl.target.trim().toLowerCase();
          if (!q) {
            return true;
          }
          return item.name && item.name.toLowerCase().indexOf(q) !== -1;
        }
        return true;
      };

      /**
       * Delete an ACL.
       */
      $scope.deleteAcl = function(acl) {
        Restangular.one('acl/' + $scope.source + '/' + acl.perm + '/' + acl.id, null).remove().then(function () {
          $scope.acls = _.reject($scope.acls, function(s) {
            return angular.equals(acl, s);
          });
        });
      };

      /**
       * Add an ACL.
       */
      $scope.addAcl = function() {
        // Compute ACLs to add
        $scope.acl.source = $scope.source;
        var acls = [];
        if ($scope.acl.perm === 'READWRITE') {
          acls = [{
            source: $scope.source,
            target: $scope.acl.target.name,
            perm: 'READ',
            type: $scope.acl.target.type
          }, {
            source: $scope.source,
            target: $scope.acl.target.name,
            perm: 'WRITE',
            type: $scope.acl.target.type
          }];
        } else {
          acls = [{
            source: $scope.source,
            target: $scope.acl.target.name,
            perm: $scope.acl.perm,
            type: $scope.acl.target.type
          }];
        }

        // Add ACLs
        _.each(acls, function(acl) {
          Restangular.one('acl').put(acl).then(function(acl) {
            if (_.isUndefined(acl.id)) {
              return;
            }
            $scope.acls.push(acl);
            $scope.acls = angular.copy($scope.acls);
          });
        });

        // Reset form
        $scope.acl = { perm: 'READ' };
      };

      /**
       * Auto-complete on ACL target.
       */
      $scope.getTargetAclTypeahead = function($viewValue) {
        var deferred = $q.defer();
        Restangular.one('acl/target/search')
          .get({
            search: $viewValue || ''
          }).then(function(data) {
          var output = [];

          // Add the type to use later
          output.push.apply(output,  _.map(data.users, function(user) {
            user.type = 'USER';
            return user;
          }));
          output.push.apply(output, _.map(data.groups, function(group) {
            group.type = 'GROUP';
            return group;
          }));

          // Send the data to the typeahead directive
          deferred.resolve(output, true);
        });
        return deferred.promise;
      };
    },
    link: function(scope, element, attr, ctrl) {
    }
  }
});