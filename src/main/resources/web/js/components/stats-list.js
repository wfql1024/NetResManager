/**
 * stats-list.js - Statistics summary and list rendering.
 */
NRM.components = NRM.components || {};

NRM.components.statsList = (function() {
    'use strict';

    /**
     * Renders inline summary items beside the toggle buttons.
     */
    function renderSummaryInline(summary) {
        var container = document.getElementById('stats-summary-inline');
        if (!container) return;

        var items = [
            { icon: '📁', label: '项目', value: summary.totalProjects || 0 },
            { icon: '📤', label: '已导出', value: summary.totalExported || 0 },
            { icon: '🗑️', label: '已回收', value: summary.totalRecycled || 0 },
            { icon: '📄', label: '类型', value: summary.uniqueFileTypes || 0 },
            { icon: '🏷️', label: '标签', value: summary.uniqueTags || 0 }
        ];

        container.innerHTML = items.map(function(c) {
            return '<span class="stat-inline-item">'
                + '<span class="stat-icon">' + c.icon + '</span>'
                + '<span class="stat-value">' + c.value + '</span>'
                + '<span>' + c.label + '</span>'
                + '</span>';
        }).join('');
    }

    /**
     * Renders a stats table.
     */
    function renderTable(tableId, data) {
        var tbody = document.querySelector('#' + tableId + ' tbody');
        if (!tbody) return;

        if (!data || data.length === 0) {
            tbody.innerHTML = '<tr><td colspan="3" style="text-align:center;color:var(--text-muted);padding:20px;">暂无数据</td></tr>';
            return;
        }

        tbody.innerHTML = data.map(function(d) {
            return '<tr>'
                + '<td>' + escapeHtml(d.category) + '</td>'
                + '<td class="col-count">' + d.count + '</td>'
                + '<td class="col-size">' + (d.totalSizeFormatted || '0 B') + '</td>'
                + '</tr>';
        }).join('');
    }

    function escapeHtml(str) {
        var div = document.createElement('div');
        div.textContent = str || '';
        return div.innerHTML;
    }

    return {
        renderSummaryInline: renderSummaryInline,
        renderTable: renderTable
    };
})();
