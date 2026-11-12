'use strict';

/**
 * Settings LDAP page controller supporting multiple LDAP directory servers.
 */
angular.module('docs').controller('SettingsLdap', function($scope, Restangular, $translate, $timeout) {
  $scope.servers = [];

  var createDefaultServer = function (index) {
    return {
      name: 'LDAP #' + (index + 1),
      enabled: index === 0 ? false : true,
      host: '',
      port: 389,
      usessl: false,
      admin_dn: '',
      admin_password: '',
      base_dn: '',
      filter: '(uid=USERNAME)',
      default_email: '',
      default_storage: 100,
      default_group: '',
      default_groupmembership: ''
    };
  };

  var loadLdapConfig = function () {
    Restangular.one('app/config_ldap').get().then(function (data) {
      if (data.servers && angular.isArray(data.servers) && data.servers.length > 0) {
        $scope.servers = angular.copy(data.servers);
        angular.forEach($scope.servers, function (srv) {
          if (srv.default_storage) {
            srv.default_storage = Math.round(srv.default_storage / 1000000);
          } else {
            srv.default_storage = 100;
          }
        });
      } else if (data.host) {
        $scope.servers = [{
          name: 'Primary LDAP',
          enabled: !!data.enabled,
          host: data.host || '',
          port: data.port || 389,
          usessl: !!data.usessl,
          admin_dn: data.admin_dn || '',
          admin_password: data.admin_password || '',
          base_dn: data.base_dn || '',
          filter: data.filter || '(uid=USERNAME)',
          default_email: data.default_email || '',
          default_storage: data.default_storage ? Math.round(data.default_storage / 1000000) : 100,
          default_group: data.default_group || '',
          default_groupmembership: data.default_groupmembership || ''
        }];
      } else {
        $scope.servers = [createDefaultServer(0)];
      }
    });
  };

  loadLdapConfig();

  /**
   * Load groups from server.
   */
  $scope.loadGroups = function() {
    Restangular.one('group').get().then(function(data) {
      $scope.groups = data.groups;
    });
  };

  $scope.loadGroups();

  /**
   * Add a new LDAP server entry.
   */
  $scope.addServer = function () {
    $scope.servers.push(createDefaultServer($scope.servers.length));
  };

  /**
   * Remove an LDAP server entry.
   */
  $scope.removeServer = function (index) {
    $scope.servers.splice(index, 1);
  };

  /**
   * Move an LDAP server higher in priority.
   */
  $scope.moveServerUp = function (index) {
    if (index <= 0) {
      return;
    }
    var temp = $scope.servers[index - 1];
    $scope.servers[index - 1] = $scope.servers[index];
    $scope.servers[index] = temp;
  };

  /**
   * Move an LDAP server lower in priority.
   */
  $scope.moveServerDown = function (index) {
    if (index >= $scope.servers.length - 1) {
      return;
    }
    var temp = $scope.servers[index + 1];
    $scope.servers[index + 1] = $scope.servers[index];
    $scope.servers[index] = temp;
  };

  // Save all LDAP servers
  $scope.saveResult = undefined;
  $scope.save = function () {
    var payload = [];
    angular.forEach($scope.servers, function (srv, idx) {
      var copy = angular.copy(srv);
      copy.order = idx;
      if (copy.default_storage) {
        copy.default_storage = Number(copy.default_storage) * 1000000;
      } else {
        copy.default_storage = 100000000;
      }
      payload.push(copy);
    });

    Restangular.one('app').post('config_ldap', {
      servers_json: JSON.stringify(payload)
    }).then(function () {
      loadLdapConfig();
      $scope.saveResult = $translate.instant('settings.ldap.saved');
      $timeout(function() {
        $scope.saveResult = undefined;
      }, 5000);
    });
  };
});
