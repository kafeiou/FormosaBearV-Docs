'use strict';

/**
 * FormosaBearV application.
 */
angular.module('docs',
    // Dependencies
    ['ui.router', 'ui.bootstrap', 'dialog', 'ngProgress', 'monospaced.qrcode', 'yaru22.angular-timeago', 'ui.validate',
      'ui.sortable', 'restangular', 'ngSanitize', 'ngTouch', 'colorpicker.module', 'ngFileUpload', 'pascalprecht.translate',
      'tmh.dynamicLocale', 'ngOnboarding']
  )

/**
 * Configuring modules.
 */
.config(function($locationProvider, $urlRouterProvider, $stateProvider, $httpProvider, $qProvider,
                 RestangularProvider, $translateProvider, timeAgoSettings, tmhDynamicLocaleProvider) {
  $locationProvider.hashPrefix('');

  // Configuring UI Router
  $stateProvider
    .state('main', {
      url: '',
      views: {
        'page': {
          templateUrl: 'partial/docs/main.html',
          controller: 'Main'
        }
      }
    })
    .state('passwordreset', {
      url: '/passwordreset/:key',
      views: {
        'page': {
          templateUrl: 'partial/docs/passwordreset.html',
          controller: 'PasswordReset'
        }
      }
    })
    .state('tag', {
      url: '/tag',
      abstract: true,
      views: {
        'page': {
          templateUrl: 'partial/docs/tag.html',
          controller: 'Tag'
        }
      }
    })
    .state('tag.default', {
      url: '',
      views: {
        'tag': {
          templateUrl: 'partial/docs/tag.default.html'
        }
      }
    })
    .state('tag.view', {
      url: '/:id',
      views: {
        'tag': {
          templateUrl: 'partial/docs/tag.view.html',
          controller: 'TagView'
        }
      }
    })
    .state('tag.view.file', {
      url: '/document/:docId/file/:fileId',
      views: {
        'file': {
          controller: 'FileView'
        }
      }
    })
    .state('tagadmin', {
      url: '/tagadmin',
      abstract: true,
      views: {
        'page': {
          templateUrl: 'partial/docs/tag.admin.html',
          controller: 'Tag'
        }
      }
    })
    .state('tagadmin.default', {
      url: '',
      views: {
        'tag': {
          templateUrl: 'partial/docs/tag.admin.default.html'
        }
      }
    })
    .state('tagadmin.edit', {
      url: '/:id',
      views: {
        'tag': {
          templateUrl: 'partial/docs/tag.edit.html',
          controller: 'TagEdit'
        }
      }
    })
    .state('settings', {
      url: '/settings',
      abstract: true,
      views: {
        'page': {
          templateUrl: 'partial/docs/settings.html',
          controller: 'Settings'
        }
      }
    })
    .state('settings.default', {
      url: '',
      views: {
        'settings': {
          templateUrl: 'partial/docs/settings.default.html',
          controller: 'SettingsDefault'
        }
      }
    })
    .state('settings.account', {
      url: '/account',
      views: {
        'settings': {
          templateUrl: 'partial/docs/settings.account.html',
          controller: 'SettingsAccount'
        }
      }
    })
    .state('settings.security', {
      url: '/security',
      views: {
        'settings': {
          templateUrl: 'partial/docs/settings.security.html',
          controller: 'SettingsSecurity'
        }
      }
    })
    .state('settings.session', {
      url: '/session',
      views: {
        'settings': {
          templateUrl: 'partial/docs/settings.session.html',
          controller: 'SettingsSession'
        }
      }
    })
    .state('settings.monitoring', {
      url: '/monitoring',
      views: {
        'settings': {
          templateUrl: 'partial/docs/settings.monitoring.html',
          controller: 'SettingsMonitoring'
        }
      }
    })
    .state('settings.backup', {
      url: '/backup',
      views: {
        'settings': {
          templateUrl: 'partial/docs/settings.backup.html',
          controller: 'SettingsBackup'
        }
      }
    })
    .state('settings.config', {
      url: '/config',
      views: {
        'settings': {
          templateUrl: 'partial/docs/settings.config.html',
          controller: 'SettingsConfig'
        }
      }
    })
    .state('settings.inbox', {
      url: '/inbox',
      views: {
        'settings': {
          templateUrl: 'partial/docs/settings.inbox.html',
          controller: 'SettingsInbox'
        }
      }
    })
    .state('settings.metadata', {
      url: '/metadata',
      views: {
        'settings': {
          templateUrl: 'partial/docs/settings.metadata.html',
          controller: 'SettingsMetadata'
        }
      }
    })
    .state('settings.user', {
      url: '/user',
      views: {
        'settings': {
          templateUrl: 'partial/docs/settings.user.html',
          controller: 'SettingsUser'
        }
      }
    })
    .state('settings.user.edit', {
      url: '/edit/:username',
      views: {
        'user': {
          templateUrl: 'partial/docs/settings.user.edit.html',
          controller: 'SettingsUserEdit'
        }
      }
    })
    .state('settings.user.add', {
      url: '/add',
      views: {
        'user': {
          templateUrl: 'partial/docs/settings.user.edit.html',
          controller: 'SettingsUserEdit'
        }
      }
    })
    .state('settings.workflow', {
      url: '/workflow',
      views: {
        'settings': {
          templateUrl: 'partial/docs/settings.workflow.html',
          controller: 'SettingsWorkflow'
        }
      }
    })
    .state('settings.workflow.edit', {
      url: '/edit/:id',
      views: {
        'workflow': {
          templateUrl: 'partial/docs/settings.workflow.edit.html',
          controller: 'SettingsWorkflowEdit'
        }
      }
    })
    .state('settings.workflow.add', {
      url: '/add',
      views: {
        'workflow': {
          templateUrl: 'partial/docs/settings.workflow.edit.html',
          controller: 'SettingsWorkflowEdit'
        }
      }
    })
    .state('settings.group', {
      url: '/group',
      views: {
        'settings': {
          templateUrl: 'partial/docs/settings.group.html',
          controller: 'SettingsGroup'
        }
      }
    })
    .state('settings.group.edit', {
      url: '/edit/:name',
      views: {
        'group': {
          templateUrl: 'partial/docs/settings.group.edit.html',
          controller: 'SettingsGroupEdit'
        }
      }
    })
    .state('settings.group.add', {
      url: '/add',
      views: {
        'group': {
          templateUrl: 'partial/docs/settings.group.edit.html',
          controller: 'SettingsGroupEdit'
        }
      }
    })
    .state('settings.vocabulary', {
      url: '/vocabulary',
      views: {
        'settings': {
          templateUrl: 'partial/docs/settings.vocabulary.html',
          controller: 'SettingsVocabulary'
        }
      }
    })
    .state('settings.ldap', {
      url: '/ldap',
      views: {
        'settings': {
          templateUrl: 'partial/docs/settings.ldap.html',
          controller: 'SettingsLdap'
        }
      }
    })
    .state('document', {
      url: '/document',
      abstract: true,
      views: {
        'page': {
          templateUrl: 'partial/docs/document.html',
          controller: 'Document'
        }
      }
    })
    .state('document.default', {
      url: '',
      views: {
        'document': {
          templateUrl: 'partial/docs/document.default.html',
          controller: 'DocumentDefault'
        }
      }
    })
    .state('document.default.search', {
      url: '/search/:search'
    })
    .state('document.default.file', {
      url: '/file/:fileId',
      views: {
        'file': {
          controller: 'FileView'
        }
      }
    })
    .state('document.add', {
      url: '/add?files',
      views: {
        'document': {
          templateUrl: 'partial/docs/document.edit.html',
          controller: 'DocumentEdit'
        }
      }
    })
    .state('document.edit', {
      url: '/edit/:id?files',
      views: {
        'document': {
          templateUrl: 'partial/docs/document.edit.html',
          controller: 'DocumentEdit'
        }
      }
    })
    .state('document.view', {
      url: '/view/:id',
      redirectTo: 'document.view.content',
      views: {
        'document': {
          templateUrl: 'partial/docs/document.view.html',
          controller: 'DocumentView'
        }
      }
    })
    .state('document.view.content', {
      url: '/content',
      views: {
        'tab': {
          templateUrl: 'partial/docs/document.view.content.html',
          controller: 'DocumentViewContent'
        }
      }
    })
    .state('document.view.workflow', {
      url: '/workflow',
      views: {
        'tab': {
          templateUrl: 'partial/docs/document.view.workflow.html',
          controller: 'DocumentViewWorkflow'
        }
      }
    })
    .state('document.view.content.file', {
      url: '/file/:fileId',
      views: {
        'file': {
          controller: 'FileView'
        }
      }
    })
    .state('document.view.permissions', {
      url: '/permissions',
      views: {
        'tab': {
          templateUrl: 'partial/docs/document.view.permissions.html',
          controller: 'DocumentViewPermissions'
        }
      }
    })
    .state('document.view.activity', {
      url: '/activity',
      views: {
        'tab': {
          templateUrl: 'partial/docs/document.view.activity.html',
          controller: 'DocumentViewActivity'
        }
      }
    })
    .state('login', {
      url: '/login?redirectState&redirectParams',
      views: {
        'page': {
          templateUrl: 'partial/docs/login.html',
          controller: 'Login'
        }
      }
    })
    .state('user', {
      url: '/user',
      abstract: true,
      views: {
        'page': {
          templateUrl: 'partial/docs/usergroup.html',
          controller: 'UserGroup'
        }
      }
    })
    .state('user.default', {
      url: '',
      views: {
        'sub': {
          templateUrl: 'partial/docs/usergroup.default.html'
        }
      }
    })
    .state('user.profile', {
      url: '/:username',
      views: {
        'sub': {
          templateUrl: 'partial/docs/user.profile.html',
          controller: 'UserProfile'
        }
      }
    })
    .state('group', {
      url: '/group',
      abstract: true,
      views: {
        'page': {
          templateUrl: 'partial/docs/usergroup.html',
          controller: 'UserGroup'
        }
      }
    })
    .state('group.default', {
      url: '',
      views: {
        'sub': {
          templateUrl: 'partial/docs/usergroup.default.html'
        }
      }
    })
    .state('group.profile', {
      url: '/:name',
      views: {
        'sub': {
          templateUrl: 'partial/docs/group.profile.html',
          controller: 'GroupProfile'
        }
      }
    });

  // Configuring Restangular
  RestangularProvider.setBaseUrl('../api');

  // Configuring Angular Translate
  $translateProvider
    .useSanitizeValueStrategy('escapeParameters')
    .useStaticFilesLoader({
      prefix: 'locale/',
      suffix: '.json?@build.date@'
    })
    .registerAvailableLanguageKeys(['en', 'es', 'pt', 'fr', 'de', 'el', 'ru', 'it', 'pl', 'zh_CN', 'zh_TW', 'sq_AL', 'ja', 'vi'], {
      'en_*': 'en',
      'es_*': 'es',
      'pt_*': 'pt',
      'fr_*': 'fr',
      'de_*': 'de',
      'el_*': 'el',
      'ru_*': 'ru',
      'it_*': 'it',
      'pl_*': 'pl',
      'ja_*': 'ja',
      'vi_*': 'vi',
      '*': 'en'
    })
    .fallbackLanguage('en');

  if (!_.isUndefined(localStorage.overrideLang)) {
    // Set the current language if an override is saved in local storage
    $translateProvider.use(localStorage.overrideLang);
  } else {
    // Or else determine the language based on the user's browser
    $translateProvider.determinePreferredLanguage();
    if (!$translateProvider.use()) {
      $translateProvider.use('en');
    }
  }

  // Configuring Timago
  timeAgoSettings.fullDateAfterSeconds = 60 * 60 * 24 * 30; // 30 days

  // Configuring tmhDynamicLocale
  tmhDynamicLocaleProvider.localeLocationPattern('locale/angular-locale_{{locale}}.js');

  // Configuring $http to act like jQuery.ajax
  $httpProvider.defaults.headers.post['Content-Type'] = 'application/x-www-form-urlencoded;charset=utf-8';
  $httpProvider.defaults.headers.put['Content-Type'] = 'application/x-www-form-urlencoded;charset=utf-8';
  $httpProvider.defaults.headers.delete = {
    'Content-Type': 'application/x-www-form-urlencoded;charset=utf-8'
  };
  $httpProvider.defaults.transformRequest = [function(data) {
    var param = function(obj) {
      var query = '';
      var name, value, fullSubName, subName, subValue, innerObj, i;
      
      for(name in obj) {
        value = obj[name];
        
        if(value instanceof Array) {
          for(i=0; i<value.length; ++i) {
            subValue = value[i];
            fullSubName = name;
            innerObj = {};
            innerObj[fullSubName] = subValue;
            query += param(innerObj) + '&';
          }
        } else if(value instanceof Object) {
          for(subName in value) {
            subValue = value[subName];
            fullSubName = name + '[' + subName + ']';
            innerObj = {};
            innerObj[fullSubName] = subValue;
            query += param(innerObj) + '&';
          }
        }
        else if(value !== undefined && value !== null) {
          query += encodeURIComponent(name) + '=' + encodeURIComponent(value) + '&';
        }
      }
      
      return query.length ? query.substr(0, query.length - 1) : query;
    };
    
    return angular.isObject(data) && String(data) !== '[object File]' ? param(data) : data;
  }];

  // Silence unhandled rejections
  $qProvider.errorOnUnhandledRejections(false);
})

/**
 * Application initialization.
 */
.run(function($rootScope, $state, $stateParams, Restangular) {
  $rootScope.$state = $state;
  $rootScope.$stateParams = $stateParams;

  // Fetch the current theme configuration
  $rootScope.appName = '';
  Restangular.one('theme').get().then(function(data) {
    $rootScope.appName = data.name;
  });

  // Initialize dark/light theme mode
  var applyDarkMode = function(isDark) {
    $rootScope.darkMode = !!isDark;
    if ($rootScope.darkMode) {
      angular.element(document.body).addClass('dark-mode');
    } else {
      angular.element(document.body).removeClass('dark-mode');
    }
    try {
      localStorage.formosaThemeMode = $rootScope.darkMode ? 'dark' : 'light';
    } catch (e) {}
  };
  applyDarkMode(localStorage.formosaThemeMode === 'dark');
  $rootScope.setDarkMode = function(isDark) {
    applyDarkMode(isDark);
  };
  $rootScope.toggleDarkMode = function($event) {
    if ($event) {
      $event.preventDefault();
    }
    applyDarkMode(!$rootScope.darkMode);
  };

  // Initialize font size scaling (12px to 22px, default 14px)
  var applyFontSize = function(sizePx) {
    var parsed = parseInt(sizePx, 10);
    if (isNaN(parsed)) {
      parsed = 14;
    }
    if (parsed < 12) parsed = 12;
    if (parsed > 22) parsed = 22;
    $rootScope.fontSize = parsed;
    $rootScope.fontScalePercent = Math.round((parsed / 14) * 100);
    document.documentElement.style.setProperty('--app-font-size', parsed + 'px');
    document.body.style.fontSize = parsed + 'px';
    try {
      localStorage.formosaFontSize = String(parsed);
    } catch (e) {}
  };
  applyFontSize(localStorage.formosaFontSize ? parseInt(localStorage.formosaFontSize, 10) : 14);
  $rootScope.setFontSize = function(sizePx, $event) {
    if ($event) {
      $event.preventDefault();
    }
    applyFontSize(sizePx);
  };
  $rootScope.changeFontSize = function(delta, $event) {
    if ($event) {
      $event.preventDefault();
    }
    applyFontSize(($rootScope.fontSize || 14) + delta);
  };
  $rootScope.resetFontSize = function($event) {
    if ($event) {
      $event.preventDefault();
    }
    applyFontSize(14);
  };

  // Initialize version history column visibility (default hidden: false)
  var applyShowVersionMimetype = function(show) {
    $rootScope.showVersionMimetype = !!show;
    try {
      localStorage.formosaShowVersionMimetype = $rootScope.showVersionMimetype ? 'true' : 'false';
    } catch (e) {}
  };
  var applyShowVersionCreateDate = function(show) {
    $rootScope.showVersionCreateDate = !!show;
    try {
      localStorage.formosaShowVersionCreateDate = $rootScope.showVersionCreateDate ? 'true' : 'false';
    } catch (e) {}
  };
  applyShowVersionMimetype(localStorage.formosaShowVersionMimetype === 'true');
  applyShowVersionCreateDate(localStorage.formosaShowVersionCreateDate === 'true');
  $rootScope.setShowVersionMimetype = function(show) {
    applyShowVersionMimetype(show);
  };
  $rootScope.setShowVersionCreateDate = function(show) {
    applyShowVersionCreateDate(show);
  };

  // Languages
  $rootScope.acceptedLanguages = [
    { key: 'eng', label: 'English' },
    { key: 'fra', label: 'Français' },
    { key: 'ita', label: 'Italiano' },
    { key: 'deu', label: 'Deutsch' },
    { key: 'spa', label: 'Español' },
    { key: 'por', label: 'Português' },
    { key: 'pol', label: 'Polski' },
    { key: 'rus', label: 'русский' },
    { key: 'ukr', label: 'українська' },
    { key: 'ara', label: 'العربية' },
    { key: 'hin', label: 'हिन्दी' },
    { key: 'chi_sim', label: '简体中文' },
    { key: 'chi_tra', label: '繁体中文' },
    { key: 'jpn', label: '日本語' },
    { key: 'tha', label: 'ภาษาไทย' },
    { key: 'kor', label: '한국어' },
    { key: 'nld', label: 'Nederlands' },
    { key: 'tur', label: 'Türkçe' },
    { key: 'heb', label: 'עברית' },
    { key: 'hun', label: 'Magyar' },
    { key: 'fin', label: 'Suomi' },
    { key: 'swe', label: 'Svenska' },
    { key: 'lav', label: 'Latviešu' },
    { key: 'dan', label: 'Dansk' },
    { key: 'nor', label: 'Norsk' },
    { key: 'vie', label: 'Tiếng Việt' },
    { key: 'ces', label: 'Czech' },
    { key: 'sqi', label: 'Shqip' }
  ];
})
/**
 * Initialize ngProgress.
 */
.run (function ($rootScope, ngProgressFactory, $http) {
  $rootScope.ngProgress = ngProgressFactory.createInstance();

  // Watch for the number of XHR running
  $rootScope.$watch(function() {
    return $http.pendingRequests.length > 0;
  }, function(loading) {
    if (!loading) {
      $rootScope.ngProgress.complete();
    } else {
      $rootScope.ngProgress.start();
    }
  });
})
/**
 * Initialize ngOnboarding.
 */
.run (function ($rootScope) {
  $rootScope.onboardingEnabled = false;
})

/**
 * Global download permission helper.
 */
.run(function ($rootScope, Restangular, $translate) {
  $rootScope.canDownload = function(file, document) {
    if (!$rootScope.app || $rootScope.app.file_download !== false) {
      return true;
    }
    if ($rootScope.userInfo && $rootScope.userInfo.base_functions && $rootScope.userInfo.base_functions.indexOf('ADMIN') !== -1) {
      return true;
    }
    var doc = document || $rootScope.currentDocument;
    if (doc) {
      if (doc.can_download !== undefined) {
        return doc.can_download;
      }
      if ($rootScope.userInfo && doc.creator && $rootScope.userInfo.username === doc.creator) {
        return true;
      }
      if (doc.allow_download) {
        return true;
      }
    }
    if (file) {
      if ($rootScope.userInfo && file.creator && $rootScope.userInfo.username === file.creator) {
        return true;
      }
    }
    return false;
  };

  if (!$rootScope.app) {
    Restangular.one('app').get().then(function(appData) {
      $rootScope.app = appData;
    });
  }

  /**
   * Global tag tooltip helper.
   */
  $rootScope.getTagTooltip = function(tag) {
    if (!tag || !tag.creator) {
      return '';
    }
    return $translate.instant('tag.created_by', { creator: tag.creator });
  };
})
;

if (location.search.indexOf("protractor") > -1) {
  window.name = 'NG_DEFER_BOOTSTRAP!';
}
