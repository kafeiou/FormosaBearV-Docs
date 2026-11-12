'use strict';

/**
 * File modal view controller.
 */
angular.module('docs').controller('FileModalView', function ($uibModalInstance, $scope, $state, $stateParams, $sce, Restangular, $transitions, $rootScope) {
  $scope.Math = window.Math;

  // Block right-click context menu and save/print shortcut keys (Ctrl+S, Ctrl+P)
  var onKeyDown = function (e) {
    if ((e.ctrlKey || e.metaKey) && (e.key === 's' || e.key === 'p' || e.key === 'S' || e.key === 'P')) {
      e.preventDefault();
      e.stopPropagation();
      return false;
    }
  };
  window.addEventListener('keydown', onKeyDown, true);

  var onContextMenu = function (e) {
    var target = e.target;
    if (target && (target.id === 'pdf-canvas' || (target.closest && (target.closest('.pdf-container') || target.tagName === 'IMG')))) {
      e.preventDefault();
      e.stopPropagation();
      return false;
    }
  };
  window.addEventListener('contextmenu', onContextMenu, true);

  var cleanupListeners = function () {
    window.removeEventListener('keydown', onKeyDown, true);
    window.removeEventListener('contextmenu', onContextMenu, true);
  };


  $scope.canDownload = function(file, document) {
    var f = file || $scope.file;
    var d = document || $scope.document || $rootScope.currentDocument;
    return $rootScope.canDownload(f, d);
  };

  if (!$rootScope.app) {
    Restangular.one('app').get().then(function(appData) {
      $rootScope.app = appData;
    });
  }

  var EN_MONTHS_SHORT = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
  var EN_MONTHS_FULL = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
  var EN_DAYS_SHORT = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
  var EN_DAYS_FULL = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];

  var formatEnglishDate = function (date, pattern) {
    var d = date || new Date();
    var fmt = (pattern && angular.isString(pattern) && pattern.trim().length > 0) ? pattern.trim() : 'dd MMM yyyy';
    var pad2 = function (n) { return n < 10 ? '0' + n : '' + n; };
    return fmt.replace(/yyyy|YYYY|yy|YY|MMMM|MMM|MM|M|dddd|ddd|EEEE|EEE|dd|DD|d|D/g, function (token) {
      switch (token) {
        case 'yyyy':
        case 'YYYY':
          return '' + d.getFullYear();
        case 'yy':
        case 'YY':
          return pad2(d.getFullYear() % 100);
        case 'MMMM':
          return EN_MONTHS_FULL[d.getMonth()];
        case 'MMM':
          return EN_MONTHS_SHORT[d.getMonth()];
        case 'MM':
          return pad2(d.getMonth() + 1);
        case 'M':
          return '' + (d.getMonth() + 1);
        case 'dddd':
        case 'EEEE':
          return EN_DAYS_FULL[d.getDay()];
        case 'ddd':
        case 'EEE':
          return EN_DAYS_SHORT[d.getDay()];
        case 'dd':
        case 'DD':
          return pad2(d.getDate());
        case 'd':
        case 'D':
          return '' + d.getDate();
        default:
          return token;
      }
    });
  };

  var escapeXml = function (str) {
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&apos;');
  };

  $scope.isWatermarkEnabled = function () {
    return !!($rootScope.app && $rootScope.app.preview_watermark_enabled);
  };

  $scope.getWatermarkStyle = function () {
    if (!$scope.isWatermarkEnabled()) {
      return {};
    }
    var username = ($rootScope.userInfo && $rootScope.userInfo.username && !$rootScope.userInfo.anonymous)
      ? $rootScope.userInfo.username
      : 'Guest';
    var dateStr = formatEnglishDate(new Date(), $rootScope.app.preview_watermark_date_format);
    var text = escapeXml(username + ' \u00B7 ' + dateStr);
    var svg = '<svg xmlns="http://www.w3.org/2000/svg" width="320" height="200">' +
      '<text x="50%" y="50%" dominant-baseline="middle" text-anchor="middle" ' +
      'fill="rgba(128, 128, 128, 0.24)" font-family="Arial, Helvetica, sans-serif" font-size="16" font-weight="bold" ' +
      'transform="rotate(-30 160 100)">' + text + '</text>' +
      '</svg>';
    return {
      'background-image': 'url("data:image/svg+xml;utf8,' + encodeURIComponent(svg) + '")',
      'background-repeat': 'repeat',
      'background-position': 'center center'
    };
  };
  $scope.pdfLoading = false;
  $scope.pdfError = false;
  $scope.pdfCurrentPage = 1;
  $scope.pdfTotalPages = 1;
  $scope.pdfScale = 1.0;
  $scope.pdfRotation = 0;

  var pdfDoc = null;
  var pageRendering = false;
  var pageNumPending = null;

  var renderPdfPage = function (num) {
    if (!pdfDoc) {
      return;
    }
    pageRendering = true;
    $scope.pdfLoading = true;

    pdfDoc.getPage(num).then(function (page) {
      var canvas = document.getElementById('pdf-canvas');
      if (!canvas) {
        pageRendering = false;
        $scope.$applyAsync(function () {
          $scope.pdfLoading = false;
        });
        return;
      }
      var ctx = canvas.getContext('2d');
      var viewport = page.getViewport({ scale: $scope.pdfScale, rotation: $scope.pdfRotation });

      var outputScale = window.devicePixelRatio || 1;
      canvas.width = Math.floor(viewport.width * outputScale);
      canvas.height = Math.floor(viewport.height * outputScale);
      canvas.style.width = Math.floor(viewport.width) + 'px';
      canvas.style.height = Math.floor(viewport.height) + 'px';

      var transform = outputScale !== 1 ? [outputScale, 0, 0, outputScale, 0, 0] : null;

      var renderContext = {
        canvasContext: ctx,
        transform: transform,
        viewport: viewport
      };

      var renderTask = page.render(renderContext);
      renderTask.promise.then(function () {
        pageRendering = false;
        if (pageNumPending !== null) {
          renderPdfPage(pageNumPending);
          pageNumPending = null;
        }
        $scope.$applyAsync(function () {
          $scope.pdfLoading = false;
        });
      }, function () {
        pageRendering = false;
        $scope.$applyAsync(function () {
          $scope.pdfLoading = false;
        });
      });
    }, function () {
      pageRendering = false;
      $scope.$applyAsync(function () {
        $scope.pdfLoading = false;
      });
    });

    $scope.pdfCurrentPage = num;
  };

  var queueRenderPage = function (num) {
    if (pageRendering) {
      pageNumPending = num;
    } else {
      renderPdfPage(num);
    }
  };

  $scope.prevPdfPage = function () {
    if ($scope.pdfCurrentPage <= 1) {
      return;
    }
    $scope.pdfCurrentPage--;
    queueRenderPage($scope.pdfCurrentPage);
  };

  $scope.nextPdfPage = function () {
    if ($scope.pdfCurrentPage >= $scope.pdfTotalPages) {
      return;
    }
    $scope.pdfCurrentPage++;
    queueRenderPage($scope.pdfCurrentPage);
  };

  $scope.zoomPdf = function (delta) {
    var newScale = Math.round(($scope.pdfScale + delta) * 10) / 10;
    if (newScale >= 0.5 && newScale <= 3.0) {
      $scope.pdfScale = newScale;
      queueRenderPage($scope.pdfCurrentPage);
    }
  };

  $scope.rotatePdf = function () {
    $scope.pdfRotation = ($scope.pdfRotation + 90) % 360;
    queueRenderPage($scope.pdfCurrentPage);
  };

  $scope.fitPdfWidth = function () {
    if (!pdfDoc) {
      return;
    }
    pdfDoc.getPage($scope.pdfCurrentPage).then(function (page) {
      var container = document.querySelector('.pdf-container');
      var containerWidth = container ? container.clientWidth - 50 : 800;
      var unscaledViewport = page.getViewport({ scale: 1.0, rotation: $scope.pdfRotation });
      var targetScale = Math.round((containerWidth / unscaledViewport.width) * 10) / 10;
      if (targetScale < 0.5) {
        targetScale = 0.5;
      }
      if (targetScale > 2.5) {
        targetScale = 2.5;
      }
      $scope.pdfScale = targetScale;
      queueRenderPage($scope.pdfCurrentPage);
    });
  };

  var loadPdf = function (url) {
    if (!window.pdfjsLib) {
      $scope.pdfError = true;
      return;
    }
    $scope.pdfLoading = true;
    $scope.pdfError = false;
    window.pdfjsLib.GlobalWorkerOptions.workerSrc = 'lib/pdfjs/pdf.worker.min.js';

    var loadingTask = window.pdfjsLib.getDocument({
      url: url,
      withCredentials: true,
      cMapUrl: 'lib/pdfjs/cmaps/',
      cMapPacked: true
    });

    loadingTask.promise.then(function (pdf) {
      pdfDoc = pdf;
      $scope.$applyAsync(function () {
        $scope.pdfTotalPages = pdf.numPages;
        $scope.pdfCurrentPage = 1;
        $scope.fitPdfWidth();
      });
    }, function () {
      $scope.$applyAsync(function () {
        $scope.pdfLoading = false;
        $scope.pdfError = true;
      });
    });
  };

  var setFile = function (files) {
    // Search current file
    _.each(files, function (value) {
      if (value.id === $stateParams.fileId) {
        $scope.file = value;
        $scope.trustedFileUrl = $sce.trustAsResourceUrl('../api/file/' + $stateParams.fileId + '/data');
        if ($scope.canPreviewAsPdf()) {
          setTimeout(function () {
            loadPdf('../api/file/' + $stateParams.fileId + '/pdf');
          }, 100);
        }
      }
    });
  };

  // Load files
  var documentId = $stateParams.docId || $stateParams.id;
  if (documentId) {
    Restangular.one('document', documentId).get().then(function (doc) {
      $scope.document = doc;
    });
  }
  Restangular.one('file/list').get({ id: documentId }).then(function (data) {
    $scope.files = data.files;
    setFile(data.files);

    // File not found, maybe it's a version
    if (!$scope.file) {
      Restangular.one('file/' + $stateParams.fileId + '/versions').get().then(function (data) {
        setFile(data.files);
      });
    }
  });

  /**
   * Return the next file.
   */
  $scope.nextFile = function () {
    var next = undefined;
    _.each($scope.files, function (value, key) {
      if (value.id === $stateParams.fileId) {
        next = $scope.files[key + 1];
      }
    });
    return next;
  };

  /**
   * Return the previous file.
   */
  $scope.previousFile = function () {
    var previous = undefined;
    _.each($scope.files, function (value, key) {
      if (value.id === $stateParams.fileId) {
        previous = $scope.files[key - 1];
      }
    });
    return previous;
  };

  /**
   * Navigate to the next file.
   */
  $scope.goNextFile = function () {
    var next = $scope.nextFile();
    if (next) {
      $state.go('^.file', { id: $stateParams.id, docId: $stateParams.docId, fileId: next.id });
    }
  };

  /**
   * Navigate to the previous file.
   */
  $scope.goPreviousFile = function () {
    var previous = $scope.previousFile();
    if (previous) {
      $state.go('^.file', { id: $stateParams.id, docId: $stateParams.docId, fileId: previous.id });
    }
  };

  /**
   * Open the file in a new window (Download).
   */
  $scope.openFile = function () {
    window.open('../api/file/' + $stateParams.fileId + '/data');
  };

  /**
   * Open the file content a new window.
   */
  $scope.openFileContent = function () {
    window.open('../api/file/' + $stateParams.fileId + '/data?size=content');
  };

  /**
   * Print the file.
   */
  $scope.printFile = function () {
    var popup = window.open('../api/file/' + $stateParams.fileId + '/data', '_blank');
    popup.onload = function () {
      popup.print();
      popup.close();
    };
  };

  /**
   * Close the file preview.
   */
  $scope.closeFile = function () {
    if (pdfDoc) {
      try { pdfDoc.destroy(); } catch (e) {}
    }
    $uibModalInstance.dismiss();
  };

  // Close the modal when the user exits this state
  var off = $transitions.onStart({}, function (transition) {
    if (!$uibModalInstance.closed) {
      if (pdfDoc) {
        try { pdfDoc.destroy(); } catch (e) {}
      }
      if (transition.to().name === $state.current.name) {
        $uibModalInstance.close();
      } else {
        $uibModalInstance.dismiss();
      }
    }
    off();
  });

  /**
   * Return true if we can display the preview image.
   */
  $scope.canDisplayPreview = function () {
    return $scope.file && (!$scope.file.mimetype || ($scope.file.mimetype.indexOf('video/') !== 0 && $scope.file.mimetype.indexOf('audio/') !== 0));
  };

  /**
   * Return true if the current file is a PDF or convertible Office document (Word, PowerPoint).
   */
  $scope.canPreviewAsPdf = function () {
    if (!$scope.file || !$scope.file.mimetype) return false;
    var mime = $scope.file.mimetype;
    return mime === 'application/pdf' ||
           mime === 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' ||
           mime === 'application/vnd.openxmlformats-officedocument.presentationml.presentation' ||
           mime === 'application/vnd.oasis.opendocument.text' ||
           mime === 'application/msword' ||
           mime === 'application/vnd.ms-powerpoint';
  };

  /**
   * Return true if the current file is previewable via PDF.js reader.
   */
  $scope.isPdf = function () {
    return $scope.canPreviewAsPdf();
  };
});
