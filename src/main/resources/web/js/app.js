/**
 * app.js - Application router, global state, and bootstrap.
 */
window.NRM = window.NRM || {};

(function() {
    'use strict';

    // ===== Global State =====
    NRM.state = {
        currentPage: 'manage',
        currentProjectId: null,
        currentDirectory: '',
        projects: [],
        files: [],
        selectedFiles: new Set(),
        lastClickedIndex: 0,
        _themePreference: 'auto'  // 'light' | 'dark' | 'auto'
    };

    // ===== UI Helpers =====
    NRM.ui = {
        showProgress: function(msg) {
            var bar = document.getElementById('progress-bar');
            var fill = bar.querySelector('.progress-bar-fill');
            var text = bar.querySelector('.progress-bar-text');
            if (bar) {
                bar.style.display = 'block';
                fill.style.width = '0%';
                text.textContent = msg || '处理中...';
                // Animate
                setTimeout(function() { fill.style.width = '70%'; }, 50);
            }
        },
        hideProgress: function() {
            var bar = document.getElementById('progress-bar');
            var fill = bar.querySelector('.progress-bar-fill');
            if (bar) {
                fill.style.width = '100%';
                setTimeout(function() { bar.style.display = 'none'; fill.style.width = '0%'; }, 300);
            }
        },
        showError: function(msg) {
            var text = msg || '操作失败';
            console.error(text);
            var toast = document.createElement('div');
            toast.textContent = text;
            toast.style.cssText = 'position:fixed;bottom:20px;right:20px;background:#d13438;color:#fff;padding:12px 20px;border-radius:6px;z-index:9999;font-size:14px;max-width:400px;word-wrap:break-word;box-shadow:0 4px 12px rgba(0,0,0,0.3);';
            document.body.appendChild(toast);
            setTimeout(function() {
                toast.style.opacity = '0';
                toast.style.transition = 'opacity 0.3s';
                setTimeout(function() {
                    if (toast.parentNode) toast.parentNode.removeChild(toast);
                }, 300);
            }, 4000);
        },
        showToast: function(msg) {
            // Simple toast notification
            var toast = document.createElement('div');
            toast.textContent = msg;
            toast.style.cssText = 'position:fixed;bottom:20px;right:20px;background:#333;color:#fff;padding:10px 20px;border-radius:6px;z-index:9999;font-size:14px;transition:opacity 0.3s;';
            document.body.appendChild(toast);
            setTimeout(function() {
                toast.style.opacity = '0';
                setTimeout(function() { document.body.removeChild(toast); }, 300);
            }, 2000);
        }
    };

    // ===== Blocking Operation Overlay =====
    NRM.ui.showBlocking = function(msg) {
        var overlay = document.getElementById('blocking-overlay');
        if (!overlay) {
            overlay = document.createElement('div');
            overlay.id = 'blocking-overlay';
            overlay.style.cssText = 'position:fixed;inset:0;z-index:5000;background:rgba(0,0,0,0.35);display:flex;align-items:center;justify-content:center;';
            overlay.innerHTML = '<div style="background:var(--bg-card, #fff);padding:28px 48px;border-radius:8px;box-shadow:0 4px 24px rgba(0,0,0,0.3);text-align:center;">'
                + '<p id="blocking-msg" style="font-size:16px;color:var(--text-primary, #1a1a1a);margin:0;"></p></div>';
            document.body.appendChild(overlay);
        }
        document.getElementById('blocking-msg').textContent = msg;
        overlay.style.display = 'flex';
    };

    NRM.ui.hideBlocking = function() {
        var overlay = document.getElementById('blocking-overlay');
        if (overlay) overlay.style.display = 'none';
    };

    // ===== Async Folder Size Callback =====
    // Called by Java via webEngine.executeScript when a folder size calc completes.
    // Parameters: path (string), size (number), sizeFormatted (string)
    NRM.onFolderSize = function(path, size, sizeFormatted) {
        // Populate the file-table's cache so sizes survive re-renders (sort, etc.)
        if (NRM.components.fileTable._folderSizeCache) {
            NRM.components.fileTable._folderSizeCache[path] = {size: size, formatted: sizeFormatted};
        }
        // Escape backslashes for CSS selector
        var escapedPath = path.replace(/\\/g, '\\\\');
        // Update size cell in manage page file table
        var row = document.querySelector('#manage-files-container tr.file-row[data-path="' + escapedPath + '"]');
        if (row) {
            var sizeCell = row.querySelector('.col-size');
            if (sizeCell) {
                sizeCell.textContent = sizeFormatted || '0 B';
            }
            // Update the data-size attribute for sorting
            row.setAttribute('data-size', size);
        }
        // Also update in history page if visible
        var histRow = document.querySelector('#history-container tr.history-data-row[data-source-path="' + escapedPath + '"]');
        if (histRow) {
            var histSize = histRow.querySelector('.h-col-size');
            if (histSize) {
                histSize.textContent = sizeFormatted || '0 B';
            }
        }
    };

    // ===== Folder size tracking =====
    NRM._folderSizeTimer = null;

    /** Requests async folder size calculation for all folder entries in the current view. */
    NRM.requestFolderSizes = function() {
        // Cancel any pending first
        NRM.cancelFolderSizes();
        // Collect folder paths from the DOM (manage page)
        var folderRows = document.querySelectorAll('#manage-files-container tr.file-row[data-isdir="true"]');
        var folderPaths = [];
        folderRows.forEach(function(row) {
            var path = row.getAttribute('data-path');
            if (path) folderPaths.push(path);
        });
        if (folderPaths.length > 0) {
            NRM.bridge.startFolderSizeCalculation(folderPaths);
        }
    };

    /** Cancels pending folder size calculations. */
    NRM.cancelFolderSizes = function() {
        if (NRM._folderSizeTimer) {
            clearTimeout(NRM._folderSizeTimer);
            NRM._folderSizeTimer = null;
        }
        NRM.bridge.cancelFolderSizeCalculations();
    };

    // ===== Router =====
    NRM.router = {
        navigate: function(page) {
            // Cancel folder size calculations when leaving manage page
            if (NRM.state.currentPage === 'manage' && page !== 'manage') {
                NRM.cancelFolderSizes();
            }
            NRM.state.currentPage = page;
            NRM.bridge.saveLastPage(page);  // persist for next launch
            NRM.state.selectedFiles.clear();

            // Show/hide pages
            document.querySelectorAll('.page').forEach(function(p) {
                p.classList.remove('active');
            });
            var pageEl = document.getElementById('page-' + page);
            if (pageEl) pageEl.classList.add('active');

            // Settings page has no top bar or sub-header
            var topBar = document.getElementById('top-bar');
            var subHeader = document.getElementById('sub-header');
            if (topBar) topBar.style.display = (page === 'settings') ? 'none' : '';
            if (subHeader) subHeader.style.display = (page === 'settings') ? 'none' : '';

            // Show/hide page-specific sub-header actions
            var histActions = document.getElementById('history-top-actions');
            if (histActions) histActions.style.display = (page === 'history') ? '' : 'none';
            var histActionsLeft = document.getElementById('history-top-actions-left');
            if (histActionsLeft) histActionsLeft.style.display = (page === 'history') ? '' : 'none';
            var manageActions = document.getElementById('manage-top-actions');
            if (manageActions) manageActions.style.display = 'none';  // hidden by default, shown on selection
            var homeBtn = document.getElementById('btn-manage-home');
            if (homeBtn) homeBtn.style.display = (page === 'manage') ? '' : 'none';

            // Update nav sidebar
            NRM.components.navSidebar.setActive(page);

            // Render project tabs (manage/history/statistics show "全部" tab, settings hides)
            NRM.components.projectTabs.render(page);

            // Initialize page
            switch (page) {
                case 'manage':
                    NRM.pages.manage.init();
                    break;
                case 'history':
                    NRM.pages.history.init();
                    break;
                case 'statistics':
                    NRM.pages.statistics.init();
                    break;
                case 'settings':
                    NRM.pages.settings.init();
                    break;
            }
        },
        refresh: function() {
            // Cancel folder size calculations before refresh
            NRM.cancelFolderSizes();
            NRM.state.selectedFiles.clear();
            switch (NRM.state.currentPage) {
                case 'manage':
                    NRM.pages.manage.init();
                    break;
                case 'history':
                    NRM.pages.history.init();
                    break;
                case 'statistics':
                    NRM.pages.statistics.loadStats();
                    break;
                case 'settings':
                    NRM.pages.settings.init();
                    break;
            }
        }
    };

    // ===== Theme =====

    /**
     * Applies a theme to the document and saves the preference.
     * @param {string} theme - 'light', 'dark', or 'auto' (follow system)
     */
    NRM.applyTheme = function(theme) {
        // Save preference in memory (always works)
        NRM.state._themePreference = theme;
        // Best-effort persistence to localStorage
        try { localStorage.setItem('netresmanager-theme', theme); } catch (e) {}

        var resolved = theme;
        if (theme === 'auto') {
            // Use Java bridge to read Windows registry (reliable in JavaFX WebView)
            try {
                var sysTheme = NRM.bridge.getSystemTheme();
                resolved = (sysTheme === 'dark') ? 'dark' : 'light';
            } catch (e) {
                resolved = 'light';
            }
        }
        var html = document.documentElement;
        if (resolved === 'dark') {
            html.setAttribute('data-theme', 'dark');
        } else {
            html.removeAttribute('data-theme');
        }
    };

    /** Initialize theme from saved preference. */
    function initTheme() {
        // Try localStorage as initial seed, fall back to default 'auto'
        try {
            if (typeof localStorage !== 'undefined') {
                var stored = localStorage.getItem('netresmanager-theme');
                if (stored === 'light' || stored === 'dark' || stored === 'auto') {
                    NRM.state._themePreference = stored;
                }
            }
        } catch (e) {
            // localStorage not available, keep default 'auto'
        }
        NRM.applyTheme(NRM.state._themePreference);
    }

    // ===== Boot =====
    function boot() {
        // Apply saved theme before rendering
        initTheme();
        // Initialize components
        NRM.components.navSidebar.init();
        NRM.components.projectTabs.init();
        NRM.components.fileTable.init();
        NRM.components.contextMenu.init();

        // Load projects
        var projects = NRM.bridge.getAllProjects();
        NRM.state.projects = projects || [];

        // Render project tabs
        NRM.components.projectTabs.render('manage');

        // Restore last visited page (or default to manage)
        var lastPage = NRM.bridge.getLastPage() || 'manage';
        if (lastPage !== 'manage' && lastPage !== 'history'
            && lastPage !== 'statistics' && lastPage !== 'settings') {
            lastPage = 'manage';
        }
        NRM.router.navigate(lastPage);
    }

    // Wait for DOM and bridge to be ready
    function waitForBridge() {
        if (typeof window.javaObject !== 'undefined' && window.javaObject !== null) {
            boot();
        } else {
            // Retry after a short delay (JavaFX WebView bridge might not be ready immediately)
            setTimeout(waitForBridge, 200);
        }
    }

    window.addEventListener('load', function() {
        waitForBridge();
    });

})();
