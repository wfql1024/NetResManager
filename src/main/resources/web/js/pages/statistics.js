/**
 * statistics.js - Statistics page: pie charts, stats lists, export/recycle toggle.
 */
NRM.pages = NRM.pages || {};

NRM.pages.statistics = (function() {
    'use strict';

    var currentView = 'export';
    var initialized = false;

    function init() {
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
                hideZero.addEventListener('change', function() { loadStats(); });
            }
            initialized = true;
        }
        loadStats();
    }

    /** Safe bridge call that catches errors individually */
    function safeCall(label, fn) {
        try {
            var result = fn();
            if (result === null || result === undefined) {
                console.warn('[Stats] ' + label + ' returned null');
            }
            return result;
        } catch(e) {
            console.error('[Stats] ' + label + ' exception: ' + (e.message || e));
            return null;
        }
    }

    function loadStats() {
        var projectId = NRM.state.currentProjectId || null;

        var byType, byTag;
        if (currentView === 'export') {
            byType = safeCall('getExportStatsByType', function() {
                return NRM.bridge.getExportStatsByType(projectId);
            }) || [];
            byTag = safeCall('getExportStatsByTag', function() {
                return NRM.bridge.getExportStatsByTag(projectId);
            }) || [];
        } else {
            byType = safeCall('getRecycleStatsByType', function() {
                return NRM.bridge.getRecycleStatsByType(projectId);
            }) || [];
            byTag = safeCall('getRecycleStatsByTag', function() {
                return NRM.bridge.getRecycleStatsByTag(projectId);
            }) || [];
        }

        var hideZeroEl = document.getElementById('stats-hide-zero');
        var hideZero = hideZeroEl ? hideZeroEl.checked : false;
        if (hideZero) {
            byType = byType.filter(function(d) { return d.count > 0; });
            byTag = byTag.filter(function(d) { return d.count > 0; });
        }

        NRM.components.pieChart.render('chart-by-type', byType, '按类型', true);
        NRM.components.pieChart.render('chart-by-tag', byTag, '按标签', true);
        NRM.components.statsList.renderTable('table-stats-type', byType);
        NRM.components.statsList.renderTable('table-stats-tag', byTag);

        // Summary
        var summary = safeCall('getStatsSummary', function() {
            return NRM.bridge.getStatsSummary(projectId);
        });
        if (summary) {
            console.log('[Stats] summary:', JSON.stringify(summary));
            NRM.components.statsList.renderSummaryInline(summary);
        } else {
            console.warn('[Stats] getStatsSummary returned null/undefined');
            // Fallback: render with zeros
            NRM.components.statsList.renderSummaryInline({
                totalProcessed: 0, totalExported: 0, totalRecycled: 0,
                uniqueFileTypes: 0, uniqueTags: 0
            });
        }
    }

    return { init: init, loadStats: loadStats };
})();
