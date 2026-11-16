'use strict';

/**
 * File modal view controller.
 */
angular.module('docs').controller('FileModalView', function ($uibModalInstance, $scope, $state, $stateParams, $sce, Restangular, $transitions, $rootScope) {
  $scope.Math = window.Math;

  $scope.canDownload = function(file, document) {
    var f = file || $scope.file;
    var d = document || $scope.document || $rootScope.currentDocument;
    return $rootScope.canDownload(f, d);
  };

  $scope.canSelectText = function () {
    return !!$scope.canDownload($scope.file, $scope.document);
  };

  // Block right-click context menu and save/print shortcut keys (Ctrl+S, Ctrl+P), plus Ctrl+C/Ctrl+A when download is disallowed
  var onKeyDown = function (e) {
    if (e.ctrlKey || e.metaKey) {
      var key = (e.key || '').toLowerCase();
      if (key === 's' || key === 'p') {
        e.preventDefault();
        e.stopPropagation();
        return false;
      }
      if (!$scope.canSelectText() && (key === 'c' || key === 'a')) {
        e.preventDefault();
        e.stopPropagation();
        return false;
      }
    }
  };
  window.addEventListener('keydown', onKeyDown, true);

  var onContextMenu = function (e) {
    var target = e.target;
    if (target) {
      if (target.id === 'pdf-canvas' || (target.closest && (target.closest('.pdf-container') || target.tagName === 'IMG'))) {
        e.preventDefault();
        e.stopPropagation();
        return false;
      }
      if (!$scope.canSelectText() && target.closest && target.closest('.text-reader-container')) {
        e.preventDefault();
        e.stopPropagation();
        return false;
      }
    }
  };
  window.addEventListener('contextmenu', onContextMenu, true);

  var onCopy = function (e) {
    if (!$scope.canSelectText() && e.target && e.target.closest && e.target.closest('.text-reader-container')) {
      e.preventDefault();
      e.stopPropagation();
      return false;
    }
  };
  window.addEventListener('copy', onCopy, true);

  var cleanupListeners = function () {
    window.removeEventListener('keydown', onKeyDown, true);
    window.removeEventListener('contextmenu', onContextMenu, true);
    window.removeEventListener('copy', onCopy, true);
  };
  $scope.$on('$destroy', cleanupListeners);

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
      ? $rootScope.formatUserFull($rootScope.userInfo.username, $rootScope.userInfo.name)
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

  // Safe Markdown-to-HTML renderer (escapes HTML first to prevent XSS)
  var renderInlineMarkdown = function (text) {
    var s = escapeXml(text);
    // Inline code `code`
    s = s.replace(/`([^`]+)`/g, '<code>$1</code>');
    // Bold **text** or __text__
    s = s.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
    s = s.replace(/__([^_]+)__/g, '<strong>$1</strong>');
    // Italic *text* or _text_
    s = s.replace(/\*([^*]+)\*/g, '<em>$1</em>');
    // Strikethrough ~~text~~
    s = s.replace(/~~([^~]+)~~/g, '<del>$1</del>');
    // Safe links [label](https://...)
    s = s.replace(/\[([^\]]+)\]\((https?:\/\/[^\s)]+|mailto:[^\s)]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>');
    return s;
  };

  var renderMarkdown = function (md) {
    if (!md) {
      return '';
    }
    var lines = String(md).replace(/\r\n/g, '\n').split('\n');
    var html = [];
    var inCodeBlock = false;
    var codeLines = [];
    var inUl = false;
    var inOl = false;
    var inTable = false;
    var tableRows = [];

    var closeListsAndTables = function () {
      if (inUl) { html.push('</ul>'); inUl = false; }
      if (inOl) { html.push('</ol>'); inOl = false; }
      if (inTable) {
        if (tableRows.length > 0) {
          html.push('<table class="table table-bordered table-striped" style="margin: 12px 0;">');
          for (var r = 0; r < tableRows.length; r++) {
            var cells = tableRows[r];
            // Skip separator row like |---|---|
            if (r === 1 && cells.every(function (c) { return /^[:\-]+$/.test(c.trim()); })) {
              continue;
            }
            var tag = (r === 0) ? 'th' : 'td';
            html.push(r === 0 ? '<thead><tr>' : (r === 1 || (r === 2 && html[html.length - 1].indexOf('</thead>') !== -1) ? '<tbody><tr>' : '<tr>'));
            for (var c = 0; c < cells.length; c++) {
              html.push('<' + tag + '>' + renderInlineMarkdown(cells[c].trim()) + '</' + tag + '>');
            }
            html.push(r === 0 ? '</tr></thead>' : '</tr>');
          }
          if (tableRows.length > 1) {
            html.push('</tbody>');
          }
          html.push('</table>');
        }
        tableRows = [];
        inTable = false;
      }
    };

    for (var i = 0; i < lines.length; i++) {
      var line = lines[i];

      if (/^```/.test(line.trim())) {
        closeListsAndTables();
        if (!inCodeBlock) {
          inCodeBlock = true;
          codeLines = [];
        } else {
          html.push('<pre style="background: rgba(128,128,128,0.12); padding: 12px; border-radius: 4px; overflow-x: auto;"><code>' + escapeXml(codeLines.join('\n')) + '</code></pre>');
          inCodeBlock = false;
        }
        continue;
      }

      if (inCodeBlock) {
        codeLines.push(line);
        continue;
      }

      // Table row detection: | a | b |
      if (/^\|(.+)\|$/.test(line.trim())) {
        if (inUl) { html.push('</ul>'); inUl = false; }
        if (inOl) { html.push('</ol>'); inOl = false; }
        inTable = true;
        var inner = line.trim().slice(1, -1);
        tableRows.push(inner.split('|'));
        continue;
      } else if (inTable) {
        closeListsAndTables();
      }

      // Blank line
      if (line.trim() === '') {
        closeListsAndTables();
        continue;
      }

      // Headings # .. ######
      var hMatch = line.match(/^(#{1,6})\s+(.*)$/);
      if (hMatch) {
        closeListsAndTables();
        var level = hMatch[1].length;
        html.push('<h' + level + ' style="margin-top: 16px; margin-bottom: 8px; border-bottom: ' + (level <= 2 ? '1px solid rgba(128,128,128,0.25); padding-bottom: 4px;' : 'none;') + '">' + renderInlineMarkdown(hMatch[2]) + '</h' + level + '>');
        continue;
      }

      // Horizontal rule
      if (/^(\*{3,}|-{3,}|_{3,})$/.test(line.trim())) {
        closeListsAndTables();
        html.push('<hr />');
        continue;
      }

      // Blockquote
      var bqMatch = line.match(/^>\s?(.*)$/);
      if (bqMatch) {
        closeListsAndTables();
        html.push('<blockquote style="margin: 8px 0; padding: 6px 12px; border-left: 4px solid #3a87ad;">' + renderInlineMarkdown(bqMatch[1]) + '</blockquote>');
        continue;
      }

      // Unordered list
      var ulMatch = line.match(/^\s*[-*+]\s+(.*)$/);
      if (ulMatch) {
        if (inOl) { html.push('</ol>'); inOl = false; }
        if (!inUl) { html.push('<ul style="margin: 8px 0; padding-left: 24px;">'); inUl = true; }
        html.push('<li>' + renderInlineMarkdown(ulMatch[1]) + '</li>');
        continue;
      }

      // Ordered list
      var olMatch = line.match(/^\s*\d+\.\s+(.*)$/);
      if (olMatch) {
        if (inUl) { html.push('</ul>'); inUl = false; }
        if (!inOl) { html.push('<ol style="margin: 8px 0; padding-left: 24px;">'); inOl = true; }
        html.push('<li>' + renderInlineMarkdown(olMatch[1]) + '</li>');
        continue;
      }

      closeListsAndTables();
      html.push('<p style="margin: 8px 0; line-height: 1.6;">' + renderInlineMarkdown(line) + '</p>');
    }

    if (inCodeBlock) {
      html.push('<pre style="background: rgba(128,128,128,0.12); padding: 12px; border-radius: 4px; overflow-x: auto;"><code>' + escapeXml(codeLines.join('\n')) + '</code></pre>');
    }
    closeListsAndTables();
    return html.join('\n');
  };

  // Text / Markdown / HTML reader state
  $scope.textLoading = false;
  $scope.textError = false;
  $scope.textContent = '';
  $scope.renderedMarkdownHtml = '';
  $scope.textCurrentPage = 1;
  $scope.textTotalPages = 1;
  $scope.textFontSize = 15;
  $scope.showRawSource = false;
  $scope.showOcrText = false;

  $scope.isMarkdownFile = function () {
    if (!$scope.file) return false;
    var mime = ($scope.file.mimetype || '').toLowerCase();
    var name = ($scope.file.name || '').toLowerCase();
    return mime === 'text/markdown' || mime === 'text/x-markdown' || name.endsWith('.md') || name.endsWith('.markdown');
  };

  $scope.isHtmlFile = function () {
    if (!$scope.file) return false;
    var mime = ($scope.file.mimetype || '').toLowerCase();
    var name = ($scope.file.name || '').toLowerCase();
    return mime === 'text/html' || name.endsWith('.html') || name.endsWith('.htm');
  };

  $scope.isTextReaderFile = function () {
    if (!$scope.file) return false;
    var mime = ($scope.file.mimetype || '').toLowerCase();
    var name = ($scope.file.name || '').toLowerCase();
    return mime === 'text/plain' ||
           mime === 'text/csv' ||
           $scope.isMarkdownFile() ||
           $scope.isHtmlFile() ||
           name.endsWith('.txt') ||
           name.endsWith('.csv') ||
           name.endsWith('.log');
  };

  var updateHtmlIframe = function () {
    setTimeout(function () {
      var iframe = document.getElementById('html-preview-iframe');
      if (!iframe) return;
      var selectCss = $scope.canSelectText()
        ? 'user-select: text; -webkit-user-select: text;'
        : 'user-select: none !important; -webkit-user-select: none !important;';
      var guardStyle = '<style>html, body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Noto Sans TC", sans-serif; font-size: ' + $scope.textFontSize + 'px; margin: 16px; color: #222; background: transparent; ' + selectCss + ' }</style>';
      iframe.srcdoc = guardStyle + ($scope.textContent || '');
    }, 30);
  };

  $scope.loadTextPage = function (page) {
    if (!$stateParams.fileId) return;
    $scope.textLoading = true;
    $scope.textError = false;
    var mode = $scope.showOcrText ? 'ocr' : 'raw';
    Restangular.one('file', $stateParams.fileId).one('text').get({ page: page || 1, mode: mode }).then(function (data) {
      $scope.textContent = data.content || '';
      $scope.textCurrentPage = data.page || 1;
      $scope.textTotalPages = data.total_pages || 1;
      $scope.textLoading = false;
      if ($scope.isMarkdownFile() && !$scope.showOcrText) {
        $scope.renderedMarkdownHtml = $sce.trustAsHtml(renderMarkdown($scope.textContent));
      } else if ($scope.isHtmlFile() && !$scope.showOcrText && !$scope.showRawSource) {
        updateHtmlIframe();
      }
    }, function () {
      $scope.textLoading = false;
      $scope.textError = true;
    });
  };

  $scope.prevTextPage = function () {
    if ($scope.textCurrentPage <= 1) return;
    $scope.loadTextPage($scope.textCurrentPage - 1);
  };

  $scope.nextTextPage = function () {
    if ($scope.textCurrentPage >= $scope.textTotalPages) return;
    $scope.loadTextPage($scope.textCurrentPage + 1);
  };

  $scope.changeTextFontSize = function (delta) {
    var nextSize = $scope.textFontSize + delta;
    if (nextSize >= 12 && nextSize <= 24) {
      $scope.textFontSize = nextSize;
      if ($scope.isHtmlFile() && !$scope.showRawSource && !$scope.showOcrText) {
        updateHtmlIframe();
      }
    }
  };

  // PDF reader state
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
    if (pdfDoc) {
      try { pdfDoc.destroy(); } catch (e) {}
      pdfDoc = null;
    }
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
      cMapPacked: true,
      isEvalSupported: false
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
        $scope.showRawSource = false;
        $scope.showOcrText = false;
        $scope.trustedFileUrl = $sce.trustAsResourceUrl('../api/file/' + $stateParams.fileId + '/data');
        if ($scope.isTextReaderFile()) {
          $scope.loadTextPage(1);
        } else if ($scope.canPreviewAsPdf()) {
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
      if ($scope.isHtmlFile() && !$scope.showRawSource && !$scope.showOcrText && $scope.textContent) {
        updateHtmlIframe();
      }
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
   * Toggle in-modal raw source view (for .md / .html) or OCR extracted text view (for PDF / images / Office),
   * keeping all content inside the watermarked modal instead of opening an unwatermarked browser tab.
   */
  $scope.openFileContent = function () {
    if ($scope.isTextReaderFile()) {
      $scope.showRawSource = !$scope.showRawSource;
      if ($scope.isHtmlFile() && !$scope.showRawSource) {
        updateHtmlIframe();
      }
    } else {
      $scope.showOcrText = !$scope.showOcrText;
      if ($scope.showOcrText) {
        $scope.loadTextPage(1);
      } else if ($scope.canPreviewAsPdf()) {
        setTimeout(function () {
          loadPdf('../api/file/' + $stateParams.fileId + '/pdf');
        }, 50);
      }
    }
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
    cleanupListeners();
    if (pdfDoc) {
      try { pdfDoc.destroy(); } catch (e) {}
      pdfDoc = null;
    }
    $uibModalInstance.dismiss();
  };

  // Close the modal when the user exits this state
  var off = $transitions.onStart({}, function (transition) {
    if (!$uibModalInstance.closed) {
      cleanupListeners();
      if (pdfDoc) {
        try { pdfDoc.destroy(); } catch (e) {}
        pdfDoc = null;
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
    return $scope.file &&
           !$scope.isTextReaderFile() &&
           !$scope.showOcrText &&
           (!$scope.file.mimetype || ($scope.file.mimetype.indexOf('video/') !== 0 && $scope.file.mimetype.indexOf('audio/') !== 0));
  };

  /**
   * Return true if the current file is a PDF or convertible Office document (Word, PowerPoint).
   */
  $scope.canPreviewAsPdf = function () {
    if (!$scope.file || !$scope.file.mimetype || $scope.isTextReaderFile() || $scope.showOcrText) return false;
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
