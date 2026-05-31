/**
 * statistics.js - Statistics page: pie charts, stats lists, export/recycle toggle.
 */
NRM.pages = NRM.pages || {};

NRM.pages.statistics = (function() {
    'use strict';

    var currentView = 'export'; // 'export' | 'recycle'
    var initialized = false;

    function init() {
        // Only bind events once
        if (!initialized) {
            var toggleBtns = document.querySelectorAll('#page-statistics .stats-btn');
            toggleBtns.forEach(function(btn) {
                btn.addEventListener('click', function() {
                    toggleBtns.forEach(function(b) { b.classList.remove('active'); });
                    this.classList.add('active');
                    currentView = this.getAttribute('data-stats-view');
                    loadStats();
                });
            });

            var hideZero = document.getElementById('stats-hide-zero');
            if (hideZero) {
                hideZero.addEventListener('change', function() {
                    loadStats();
                });
            }
            initialized = true;
        }

        // Load stats (every time page is shown)
        loadStats();
    }

    function loadStats() {
        var projectId = NRM.state.currentProjectId || null;
        var hideZeroEl = document.getElementById('stats-hide-zero');
        var hideZero = hideZeroEl ? hideZeroEl.checked : false;

        // Get stats based on current view
        var byType, byTag;
        if (currentView === 'export') {
            byType = NRM.bridge.getExportStatsByType(projectId) || [];
            byTag = NRM.bridge.getExportStatsByTag(projectId) || [];
        } else {
            byType = NRM.bridge.getRecycleStatsByType(projectId) || [];
            byTag = NRM.bridge.getRecycleStatsByTag(projectId) || [];
        }

        // Filter if hide-zero
        if (hideZero) {
            byType = byType.filter(function(d) { return d.count > 0; });
            byTag = byTag.filter(function(d) { return d.count > 0; });
        }

        // Render charts
        NRM.components.pieChart.render('chart-by-type', byType, '按类型', true);
        NRM.components.pieChart.render('chart-by-tag', byTag, '按标签', true);

        // Render tables
        NRM.components.statsList.renderTable('table-stats-type', byType);
        NRM.components.statsList.renderTable('table-stats-tag', byTag);

        // Render inline summary
        var summary = NRM.bridge.getStatsSummary(projectId);
        if (summary) {
            NRM.components.statsList.renderSummaryInline(summary);
        }
    }

    return { init: init, loadStats: loadStats };
})();
