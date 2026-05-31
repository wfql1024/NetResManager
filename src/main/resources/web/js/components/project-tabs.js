/**
 * project-tabs.js - Horizontal project tab bar component.
 */
NRM.components = NRM.components || {};

NRM.components.projectTabs = (function() {
    'use strict';

    var isStatsPage = false;

    function init() {
        // nothing special needed for init
    }

    /**
     * Renders project tabs from NRM.state.projects.
     * If on statistics page, includes an "全部" tab.
     */
    function render(isStats) {
        isStatsPage = !!isStats;
        var container = document.querySelector('.tabs-container');
        if (!container) return;

        container.innerHTML = '';

        // "全部" tab on statistics page
        if (isStatsPage) {
            var allTab = createTabEl({ id: 0, name: '全部', isAll: true });
            container.appendChild(allTab);
        }

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

        var label = document.createElement('span');
        label.textContent = proj.name;
        label.style.cursor = 'pointer';
        label.addEventListener('click', function(e) {
            e.stopPropagation();
            selectProject(proj.id);
        });
        tab.appendChild(label);

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
        var activeId = isStatsPage && NRM.state.currentProjectId === null ? 0 : NRM.state.currentProjectId;
        tabs.forEach(function(tab) {
            var tid = parseInt(tab.getAttribute('data-project-id'));
            tab.classList.toggle('active', tid === activeId);
        });
    }

    /**
     * Shows the "create project" modal dialog.
     */
    function showCreateDialog() {
        NRM.router.navigate('settings');
        NRM.pages.settings.showCreateForm();
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
