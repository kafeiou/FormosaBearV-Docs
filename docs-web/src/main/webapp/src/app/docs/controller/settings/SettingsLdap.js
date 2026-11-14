'use strict';

/**
 * Settings LDAP page controller supporting multiple LDAP directory servers.
 */
angular.module('docs').controller('SettingsLdap', function($scope, Restangular, $translate, $timeout) {
  $scope.servers = [];

  var syncSecurityMode = function (srv) {
    srv.usessl = !!srv.usessl;
    srv.usestarttls = !srv.usessl && !!srv.usestarttls;
    if (srv.usessl) {
      srv.security = 'SSL';
    } else if (srv.usestarttls) {
      srv.security = 'STARTTLS';
    } else {
      srv.security = 'NONE';
    }
  };

  var createDefaultServer = function (index) {
    return {
      name: 'LDAP #' + (index + 1),
      enabled: index === 0 ? false : true,
      host: '',
      port: 389,
      usessl: false,
      usestarttls: false,
      security: 'NONE',
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
          syncSecurityMode(srv);
          if (srv.default_storage) {
            srv.default_storage = Math.round(srv.default_storage / 1000000);
          } else {
            srv.default_storage = 100;
          }
        });
      } else if (data.host) {
        var singleSrv = {
          name: 'Primary LDAP',
          enabled: !!data.enabled,
          host: data.host || '',
          port: data.port || 389,
          usessl: !!data.usessl,
          usestarttls: !!data.usestarttls,
          admin_dn: data.admin_dn || '',
          admin_password: data.admin_password || '',
          base_dn: data.base_dn || '',
          filter: data.filter || '(uid=USERNAME)',
          default_email: data.default_email || '',
          default_storage: data.default_storage ? Math.round(data.default_storage / 1000000) : 100,
          default_group: data.default_group || '',
          default_groupmembership: data.default_groupmembership || ''
        };
        syncSecurityMode(singleSrv);
        $scope.servers = [singleSrv];
      } else {
        $scope.servers = [createDefaultServer(0)];
      }
    });
  };

  loadLdapConfig();

  /**
   * Update usessl/usestarttls flags and default port when security protocol changes.
   */
  $scope.onSecurityChange = function (srv) {
    if (srv.security === 'SSL') {
      srv.usessl = true;
      srv.usestarttls = false;
      if (srv.port === 389) {
        srv.port = 636;
      }
    } else if (srv.security === 'STARTTLS') {
      srv.usessl = false;
      srv.usestarttls = true;
      if (srv.port === 636) {
        srv.port = 389;
      }
    } else {
      srv.usessl = false;
      srv.usestarttls = false;
      if (srv.port === 636) {
        srv.port = 389;
      }
    }
  };

  /**
   * Test connection to a single LDAP server.
   */
  $scope.testConnection = function (srv) {
    srv.testLoading = true;
    srv.testStatus = undefined;
    srv.testMessage = undefined;

    Restangular.one('app').post('test_ldap', {
      host: srv.host,
      port: srv.port,
      usessl: srv.security === 'SSL',
      usestarttls: srv.security === 'STARTTLS',
      admin_dn: srv.admin_dn,
      admin_password: srv.admin_password,
      base_dn: srv.base_dn
    }).then(function () {
      srv.testLoading = false;
      srv.testStatus = 'success';
      srv.testMessage = $translate.instant('settings.ldap.test_success');
    }, function (resp) {
      srv.testLoading = false;
      srv.testStatus = 'error';
      var detail = (resp && resp.data && resp.data.message) ? resp.data.message : '';
      srv.testMessage = $translate.instant('settings.ldap.test_error') + (detail ? ': ' + detail : '');
    });
  };

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
      copy.usessl = copy.security === 'SSL';
      copy.usestarttls = copy.security === 'STARTTLS';
      delete copy.security;
      delete copy.testLoading;
      delete copy.testStatus;
      delete copy.testMessage;
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
