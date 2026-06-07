/**
 * project-tabs.js - Horizontal project tab bar component.
 *
 * Supports "全部" tab for manage, history, and statistics pages.
 * Settings page hides tabs entirely.
 */
NRM.components = NRM.components || {};

NRM.components.projectTabs = (function() {
    'use strict';

    var currentPage = 'manage';

    function init() {
        // nothing special needed for init
    }

    /**
     * Renders project tabs from NRM.state.projects.
     * @param {string} page - 'manage', 'history', 'statistics', or 'settings'.
     *   'settings' hides tabs; the other three show a "全部" tab first.
     */
    function render(page) {
        currentPage = page || 'manage';
        var container = document.querySelector('.tabs-container');
        if (!container) return;

        container.innerHTML = '';

        // Hide tabs on settings page
        if (currentPage === 'settings') {
            return;
        }

        // "全部" tab for manage, history, statistics
        var allTab = createTabEl({ id: 0, name: '全部', isAll: true });
        container.appendChild(allTab);

        // Project tabs
        if (NRM.state.projects) {
            NRM.state.projects.forEach(function(proj) {
                var tab = createTabEl({ id: proj.id, name: proj.name });
                container.appendChild(tab);
            });
        }

        // Highlight active
        highlightActive();
    }

    function createTabEl(proj) {
        var tab = document.createElement('div');
        tab.className = 'tab-item';
        if (proj.isAll) tab.classList.add('all-tab');
        tab.setAttribute('data-project-id', proj.id);
        tab.style.cursor = 'pointer';

        var label = document.createElement('span');
        label.textContent = proj.name;
        tab.appendChild(label);

        // Entire tab area is clickable
        tab.addEventListener('click', function() {
            selectProject(proj.id);
        });

        return tab;
    }

    function selectProject(projectId) {
        if (projectId === 0) {
            NRM.state.currentProjectId = null; // "全部"
        } else {
            NRM.state.currentProjectId = projectId;
        }
        highlightActive();
        NRM.router.refresh();
    }

    function highlightActive() {
        var tabs = document.querySelectorAll('.tabs-container .tab-item');
        var activeId = (NRM.state.currentProjectId === null) ? 0 : NRM.state.currentProjectId;
        tabs.forEach(function(tab) {
            var tid = parseInt(tab.getAttribute('data-project-id'));
            tab.classList.toggle('active', tid === activeId);
        });
    }

    /**
     * Shows the "create project" form — now on manage "全部" tab.
     */
    function showCreateDialog() {
        // Select "全部" tab first
        NRM.state.currentProjectId = null;
        highlightActive();
        // Navigate to manage page to show project list
        if (NRM.state.currentPage !== 'manage') {
            NRM.router.navigate('manage');
        } else {
            NRM.router.refresh();
        }
        // Then show create form
        setTimeout(function() {
            NRM.pages.manage.showCreateForm();
        }, 50);
    }

    function hide() {
        var container = document.querySelector('.tabs-container');
        if (container) container.innerHTML = '';
    }

    return {
        init: init,
        render: render,
        hide: hide,
        highlightActive: highlightActive,
        showCreateDialog: showCreateDialog
    };
})();
