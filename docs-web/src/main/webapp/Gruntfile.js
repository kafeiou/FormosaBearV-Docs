module.exports = function(grunt) {

  // Project configuration.
  grunt.initConfig({
    pkg: grunt.file.readJSON('package.json'),
    clean: {
      init: ['dist'],
      after: ['dist/style.css', 'dist/docs.js', 'dist/share.js', 'dist/less.css', 'dist/app', 'dist/partial']
    },
    ngAnnotate: {
      options: {
        singleQuotes: true
      },
      dist: {
        files: [{
          expand: true,
          cwd: 'src',
          src: ['app/**/*.js'],
          dest: 'dist'
        }]
      }
    },
    concat: {
      docs: {
        options: {
          separator: ';'
        },
        src: ['src/lib/jquery.js','src/lib/jquery.ui.js','src/lib/underscore.js','src/lib/colorpicker.js', 'src/lib/pell.js', 'src/lib/angular.js', 'src/lib/angular.*.js',
          'dist/app/docs/app.js', 'dist/app/docs/controller/**/*.js', 'dist/app/docs/directive/*.js', 'dist/app/docs/filter/*.js', 'dist/app/docs/service/*.js'],
        dest: 'dist/docs.js'
      },
      share: {
        options: {
          separator: ';'
        },
        src: ['src/lib/jquery.js','src/lib/jquery.ui.js','src/lib/underscore.js','src/lib/colorpicker.js', 'src/lib/pell.js', 'src/lib/angular.js', 'src/lib/angular.*.js',
          'dist/app/share/app.js', 'dist/app/share/controller/*.js', 'dist/app/share/directive/*.js', 'dist/app/share/filter/*.js', 'dist/app/share/service/*.js'],
        dest: 'dist/share.js'
      },
      css: {
        src: ['src/style/*.css', 'dist/less.css'],
        dest: 'dist/style.css'
      }
    },
    less: {
      dist: {
        src: ['src/style/*.less'],
        dest: 'dist/less.css'
      }
    },
    cssmin: {
      dist: {
        src: 'dist/style.css',
        dest: 'dist/style/style.min.css'
      }
    },
    uglify: {
      docs: {
        src: 'dist/docs.js',
        dest: 'dist/docs.min.js'
      },
      share: {
        src: 'dist/share.js',
        dest: 'dist/share.min.js'
      }
    },
    ngtemplates: {
      docs: {
        cwd: 'src',
        src: 'partial/docs/*.html',
        dest: 'dist/docs.min.js',
        options: {
          append: true,
          htmlmin: {
            collapseBooleanAttributes: false,
            collapseWhitespace: true,
            removeAttributeQuotes: false,
            removeComments: true,
            removeEmptyAttributes: false,
            removeRedundantAttributes: false,
            removeScriptTypeAttributes: true,
            removeStyleLinkTypeAttributes: true
          }
        }
      },
      share: {
        cwd: 'src',
        src: 'partial/share/*.html',
        dest: 'dist/share.min.js',
        options: {
          append: true,
          htmlmin: {
            collapseBooleanAttributes: false,
            collapseWhitespace: true,
            removeAttributeQuotes: false,
            removeComments: true,
            removeEmptyAttributes: false,
            removeRedundantAttributes: false,
            removeScriptTypeAttributes: true,
            removeStyleLinkTypeAttributes: true
          }
        }
      }
    },
    copy: {
      dist: {
        expand: true,
        cwd: 'src/',
        src: ['**', '!**/*.js', '!*.html', '!**/*.less', '!**/*.css', 'locale/**'],
        dest: 'dist/'
      },
      pdfjs: {
        expand: true,
        cwd: 'src/lib/pdfjs/',
        src: ['**'],
        dest: 'dist/lib/pdfjs/'
      }
    },
    htmlrefs: {
      index: {
        src: 'src/index.html',
        dest: 'dist/index.html'
      },
      share: {
        src: 'src/share.html',
        dest: 'dist/share.html'
      }
    },
    cleanempty: {
      options: {
        files: false,
        folders: true
      },
      src: ['dist/**']
    },
    replace: {
      dist: {
        src: ['dist/docs.min.js', 'dist/share.min.js', 'dist/**/*.html', 'dist/style/style.min.css'],
        overwrite: true,
        replacements: [{
          from: '../api',
          to: grunt.option('apiurl') || '../api'
        }, {
          from: '@build.date@',
          to: new Date().getTime()
        }]
      }
    },
    apidoc: {
      generate: {
        src: '../java/',
        dest: 'dist/apidoc/'
      }
    }
  });

  grunt.loadNpmTasks('grunt-contrib-clean');
  grunt.loadNpmTasks('grunt-contrib-concat');
  grunt.loadNpmTasks('grunt-contrib-uglify');
  grunt.loadNpmTasks('grunt-contrib-copy');
  grunt.loadNpmTasks('grunt-cleanempty');
  grunt.loadNpmTasks('grunt-htmlrefs');
  grunt.loadNpmTasks('grunt-css');
  grunt.loadNpmTasks('grunt-contrib-less');
  grunt.loadNpmTasks('grunt-ng-annotate');
  grunt.loadNpmTasks('grunt-text-replace');
  grunt.loadNpmTasks('grunt-apidoc');
  grunt.loadNpmTasks('grunt-angular-templates');

  grunt.registerTask('sbomFrontend', 'Generate dist/sbom-frontend.json from bundled frontend libraries', function () {
    var fs = require('fs');
    var path = require('path');
    var items = [];
    var libDir = path.join(__dirname, 'src', 'lib');

    function extractVersion(head) {
      var patterns = [
        /pdfjsVersion\s*=\s*['"]([0-9]+\.[0-9]+\.[0-9A-Za-z._-]*)['"]/,
        /version\s*[:=]\s*['"]v?([0-9]+\.[0-9]+(?:\.[0-9A-Za-z._-]*)?)['"]/i,
        /\bv([0-9]+\.[0-9]+\.[0-9A-Za-z._-]*)/,
        /Version\s+([0-9]+\.[0-9]+(?:\.[0-9A-Za-z._-]*)?)/i,
        /AngularJS\s+v?([0-9]+\.[0-9]+\.[0-9A-Za-z._-]*)/i,
        /jQuery(?:\s+UI)?\s+v?([0-9]+\.[0-9]+\.[0-9A-Za-z._-]*)/i,
        /Bootstrap\s+v?([0-9]+\.[0-9]+\.[0-9A-Za-z._-]*)/i,
        /Font Awesome(?:\s+Free)?\s+([0-9]+\.[0-9]+\.[0-9A-Za-z._-]*)/i
      ];
      for (var i = 0; i < patterns.length; i++) {
        var m = head.match(patterns[i]);
        if (m && m[1]) {
          return m[1];
        }
      }
      return 'unknown';
    }

    if (fs.existsSync(libDir)) {
      fs.readdirSync(libDir).sort().forEach(function (entry) {
        var full = path.join(libDir, entry);
        var stat = fs.statSync(full);
        if (stat.isFile() && entry.endsWith('.js')) {
          var content = fs.readFileSync(full, 'utf8').slice(0, 4096);
          items.push({
            group: 'frontend/js',
            name: entry.replace(/\.js$/, ''),
            version: extractVersion(content)
          });
        } else if (stat.isDirectory() && entry === 'pdfjs') {
          var pdfJsFile = path.join(full, 'pdf.min.js');
          var ver = 'unknown';
          if (fs.existsSync(pdfJsFile)) {
            var pdfContent = fs.readFileSync(pdfJsFile, 'utf8').slice(0, 65536);
            ver = extractVersion(pdfContent);
          }
          items.push({
            group: 'frontend/js',
            name: 'pdf.js',
            version: ver
          });
        }
      });
    }

    var styleDir = path.join(__dirname, 'src', 'style');
    if (fs.existsSync(styleDir)) {
      ['bootstrap.css', 'fontawesome.css'].forEach(function (cssFile) {
        var full = path.join(styleDir, cssFile);
        if (fs.existsSync(full)) {
          var head = fs.readFileSync(full, 'utf8').slice(0, 4096);
          items.push({
            group: 'frontend/css',
            name: cssFile.replace(/\.css$/, ''),
            version: extractVersion(head)
          });
        }
      });
    }

    var outPath = path.join(__dirname, 'dist', 'sbom-frontend.json');
    fs.writeFileSync(outPath, JSON.stringify(items, null, 2), 'utf8');
    grunt.log.writeln('Generated ' + outPath + ' (' + items.length + ' libraries).');
  });

  // Default tasks.
  grunt.registerTask('default', ['clean:init', 'ngAnnotate', 'concat:docs', 'concat:share', 'less', 'concat:css',
    'cssmin', 'uglify:docs', 'uglify:share', 'ngtemplates:docs', 'ngtemplates:share', 'copy', 'clean:after',
    'cleanempty', 'htmlrefs:index', 'htmlrefs:share', 'replace', 'sbomFrontend', 'apidoc']);

};