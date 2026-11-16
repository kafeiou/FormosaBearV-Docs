'use strict';

/**
 * Trash / Recycle Bin controller.
 */
angular.module('docs').controller('Trash', function($scope, Restangular, $dialog, $translate) {
  $scope.activeTab = 'documents';
  $scope.showAll = false;
  $scope.searchText = '';
  $scope.loading = true;
  $scope.documents = [];
  $scope.files = [];
  $scope.isAdmin = false;
  $scope.fileDeleteMode = false;

  /**
   * Load trash items from the server.
   */
  $scope.loadTrash = function() {
    $scope.loading = true;
    Restangular.one('trash').get({ all: $scope.showAll }).then(function(data) {
      $scope.documents = data.documents || [];
      $scope.files = data.files || [];
      $scope.isAdmin = !!data.is_admin;
      $scope.fileDeleteMode = !!data.file_delete;
      $scope.loading = false;
    }, function() {
      $scope.loading = false;
    });
  };

  $scope.loadTrash();

  /**
   * Toggle admin "show all users" view.
   */
  $scope.toggleShowAll = function() {
    $scope.showAll = !$scope.showAll;
    $scope.loadTrash();
  };

  /**
   * Handle restore error dialog.
   */
  var handleRestoreError = function(resp) {
    var errorType = resp && resp.data && resp.data.type ? resp.data.type : '';
    var titleKey = 'trash.error_restore_title';
    var msgKey = 'trash.error_restore_message';
    if (errorType === 'QuotaReached') {
      titleKey = 'trash.error_quota_title';
      msgKey = 'trash.error_quota_message';
    } else if (errorType === 'FileNameDuplicate') {
      titleKey = 'trash.error_duplicate_title';
      msgKey = 'trash.error_duplicate_message';
    } else if (errorType === 'ParentDocumentInTrash') {
      titleKey = 'trash.error_parent_in_trash_title';
      msgKey = 'trash.error_parent_in_trash_message';
    }
    var title = $translate.instant(titleKey);
    var msg = $translate.instant(msgKey);
    var btns = [{ result: 'ok', label: $translate.instant('ok'), cssClass: 'btn-primary' }];
    $dialog.messageBox(title, msg, btns);
  };

  /**
   * Restore a soft-deleted document.
   */
  $scope.restoreDocument = function(doc) {
    Restangular.one('trash/document', doc.id).post('restore', {}).then(function() {
      $scope.loadTrash();
    }, handleRestoreError);
  };

  /**
   * Permanently purge a soft-deleted document from the Trash.
   */
  $scope.purgeDocument = function(doc) {
    var title = $translate.instant('trash.purge_document_title');
    var msg = $translate.instant('trash.purge_document_message', { title: doc.title });
    var btns = [
      { result: 'cancel', label: $translate.instant('cancel') },
      { result: 'ok', label: $translate.instant('trash.purge'), cssClass: 'btn-danger' }
    ];
    $dialog.messageBox(title, msg, btns, function(result) {
      if (result === 'ok') {
        Restangular.one('trash/document', doc.id).remove().then(function() {
          $scope.loadTrash();
        });
      }
    });
  };

  /**
   * Restore an individually soft-deleted file.
   */
  $scope.restoreFile = function(file) {
    Restangular.one('trash/file', file.id).post('restore', {}).then(function() {
      $scope.loadTrash();
    }, handleRestoreError);
  };

  /**
   * Permanently purge an individually soft-deleted file from the Trash.
   */
  $scope.purgeFile = function(file) {
    var title = $translate.instant('trash.purge_file_title');
    var msg = $translate.instant('trash.purge_file_message', { name: file.name || file.id });
    var btns = [
      { result: 'cancel', label: $translate.instant('cancel') },
      { result: 'ok', label: $translate.instant('trash.purge'), cssClass: 'btn-danger' }
    ];
    $dialog.messageBox(title, msg, btns, function(result) {
      if (result === 'ok') {
        Restangular.one('trash/file', file.id).remove().then(function() {
          $scope.loadTrash();
        });
      }
    });
  };

  /**
   * Empty all items in the Trash.
   */
  $scope.emptyTrash = function() {
    var title = $translate.instant('trash.empty_trash_title');
    var msg = $translate.instant('trash.empty_trash_message');
    var btns = [
      { result: 'cancel', label: $translate.instant('cancel') },
      { result: 'ok', label: $translate.instant('trash.empty_trash'), cssClass: 'btn-danger' }
    ];
    $dialog.messageBox(title, msg, btns, function(result) {
      if (result === 'ok') {
        Restangular.one('trash/empty').remove({ all: $scope.showAll }).then(function() {
          $scope.loadTrash();
        });
      }
    });
  };
});
