/**
 * history.js - History page: operation records grouped by operation time (batch).
 */
NRM.pages = NRM.pages || {};

NRM.pages.history = (function() {
    'use strict';

    function init() {
        var projectId = NRM.state.currentProjectId;
        var container = document.getElementById('history-container');
        if (!container) return;

        if (!projectId) {
            container.innerHTML = '<p class="placeholder-text">请选择一个项目查看操作历史</p>';
            return;
        }

        var records = NRM.bridge.getHistory(projectId) || [];

        if (records.length === 0) {
            container.innerHTML = '<p class="placeholder-text">暂无操作记录</p>';
            return;
        }

        // Group records by operation_time (batch)
        var groups = [];
        var groupMap = {};
        records.forEach(function(r) {
            var key = r.operationTime;
            if (!groupMap[key]) {
                groupMap[key] = { time: key, records: [] };
                groups.push(groupMap[key]);
            }
            groupMap[key].records.push(r);
        });

        var html = '';
        groups.forEach(function(group) {
            var timeLabel = group.time || '未知时间';
            var typeLabel = (group.records[0] && group.records[0].operationType === 'export') ? '📤 导出' : '♻ 回收';

            html += '<div class="history-group">';
            html += '<div class="history-group-header">';
            html += '<span class="history-time">' + timeLabel + '</span>';
            html += '<span class="history-type">' + typeLabel + '</span>';
            html += '<span class="history-count">' + group.records.length + ' 项</span>';
            html += '</div>';

            html += '<table class="history-table"><thead><tr>';
            html += '<th>文件名</th><th>类型</th><th>大小</th><th>标签</th><th>状态</th><th>操作</th>';
            html += '</tr></thead><tbody>';

            group.records.forEach(function(r) {
                var isGrey = r.rollbackFailureReason && r.rollbackFailureReason !== '';
                var rowClass = isGrey ? ' class="history-row-grey"' : '';
                var statusText = r.status === 'done' ? '✓' : (r.status === 'failed' ? '✗' : '↩');
                var statusTitle = r.status === 'done' ? '成功' : (r.status === 'failed' ? '失败' :
                                  (r.rollbackFailureReason ? '已撤回(' + r.rollbackFailureReason + ')' : '已撤回'));

                html += '<tr' + rowClass + '>';
                html += '<td>' + escapeHtml(r.originalName) + '</td>';
                html += '<td>' + escapeHtml(r.fileType || '') + '</td>';
                html += '<td>' + (r.fileSizeFormatted || '0 B') + '</td>';
                html += '<td>' + (r.tags && r.tags.length > 0
                    ? r.tags.map(function(t) { return '<span class="tag-badge">' + escapeHtml(t) + '</span>'; }).join('')
                    : '') + '</td>';
                html += '<td title="' + statusTitle + '">' + statusText + '</td>';
                html += '<td class="history-actions">';

                // Rollback button (only if done and no rollback failure)
                if (r.status === 'done' && !isGrey) {
                    html += '<button class="btn btn-sm" onclick="NRM.pages.history.rollback(' + r.id + ')">撤回</button>';
                }
                // Hide button
                html += '<button class="btn btn-sm" onclick="NRM.pages.history.hideRecord(' + r.id + ')">隐藏</button>';

                html += '</td></tr>';
            });

            html += '</tbody></table></div>';
        });

        container.innerHTML = html;
    }

    function rollback(recordId) {
        NRM.components.modal.confirm('确认撤回', '确定要撤回此操作吗？',
            function() {
                var result = NRM.bridge.rollbackRecord(recordId);
                if (result && result.error) {
                    NRM.ui.showError('撤回失败: ' + result.error);
                } else {
                    NRM.ui.showToast('操作已撤回');
                }
                init(); // refresh
            }
        );
    }

    function hideRecord(recordId) {
        NRM.components.modal.confirm('隐藏记录', '隐藏后该记录将不会在历史中显示（仍计入统计）',
            function() {
                NRM.bridge.setRecordHidden(recordId, true);
                NRM.ui.showToast('记录已隐藏');
                init();
            }
        );
    }

    function escapeHtml(str) {
        var div = document.createElement('div');
        div.textContent = str || '';
        return div.innerHTML;
    }

    return { init: init, rollback: rollback, hideRecord: hideRecord };
})();
