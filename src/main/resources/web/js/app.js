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
        lastClickedIndex: 0
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

    // ===== Router =====
    NRM.router = {
        navigate: function(page) {
            NRM.state.currentPage = page;
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

            // Show/hide breadcrumb (only manage page uses it)
            var breadcrumb = document.getElementById('breadcrumb');
            if (breadcrumb) breadcrumb.style.display = (page === 'manage') ? '' : 'none';

            // Show/hide page-specific sub-header actions
            var histActions = document.getElementById('history-top-actions');
            if (histActions) histActions.style.display = (page === 'history') ? '' : 'none';
            var histActionsLeft = document.getElementById('history-top-actions-left');
            if (histActionsLeft) histActionsLeft.style.display = (page === 'history') ? '' : 'none';

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

    // ===== Boot =====
    function boot() {
        // Initialize components
        NRM.components.navSidebar.init();
        NRM.components.projectTabs.init();
        NRM.components.breadcrumb.init();
        NRM.components.fileTable.init();
        NRM.components.contextMenu.init();

        // Load projects
        var projects = NRM.bridge.getAllProjects();
        NRM.state.projects = projects || [];

        // Render project tabs
        NRM.components.projectTabs.render('manage');

        // Navigate to manage page
        NRM.router.navigate('manage');
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
