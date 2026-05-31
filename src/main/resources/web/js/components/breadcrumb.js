/**
 * breadcrumb.js - Breadcrumb navigation component.
 */
NRM.components = NRM.components || {};

NRM.components.breadcrumb = (function() {
    'use strict';

    var container = null;

    function init() {
        container = document.getElementById('breadcrumb');
    }

    /**
     * Renders breadcrumb from current directory string.
     * @param {string} currentDir - Absolute path or empty string
     */
    function render(currentDir) {
        if (!container) init();

        if (!currentDir || currentDir === '') {
            container.innerHTML = '';
            return;
        }

        var parts = currentDir.split(/[\\\/]/).filter(function(p) { return p.length > 0; });
        var html = '';

        var accumulated = '';
        parts.forEach(function(part, idx) {
            accumulated += (idx === 0 && /^[A-Za-z]:$/.test(part)) ? part + '\\' : '\\' + part;
            if (idx > 0) {
                html += '<span class="breadcrumb-sep"> &gt; </span>';
            }
            var isLast = (idx === parts.length - 1);
            if (isLast) {
                html += '<span class="breadcrumb-item current">' + escapeHtml(part) + '</span>';
            } else {
                html += '<span class="breadcrumb-item" data-path="' + escapeHtml(accumulated) + '">'
                     + escapeHtml(part) + '</span>';
            }
        });

        container.innerHTML = html;

        // Add click handlers
        container.querySelectorAll('.breadcrumb-item[data-path]').forEach(function(el) {
            el.addEventListener('click', function() {
                var path = this.getAttribute('data-path');
                NRM.state.currentDirectory = path;
                NRM.router.refresh();
            });
        });
    }

    function escapeHtml(str) {
        var div = document.createElement('div');
        div.textContent = str;
        return div.innerHTML;
    }

    return { init: init, render: render };
})();
