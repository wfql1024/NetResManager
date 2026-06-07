/**
 * history.js - History page: operation records grouped by batch.
 *
 * Layout per record: [checkbox] filename [tags] | type | size
 *   - filename fills remaining width; type/size are fixed-width right-aligned.
 * Hover actions:
 *   - Group header: batch action buttons appear next to type label.
 *   - Record row:    action buttons appear at right of filename cell.
 * Top bar: batch actions for all currently checked records.
 * Rolled-back records show grey filenames; only 隐藏/删除 remain.
 */
NRM.pages = NRM.pages || {};

NRM.pages.history = (function() {
    'use strict';

    var selectedRecords = {};   // recordId -> true
    var allRecordsFlat = [];    // flat list of all record objects currently displayed

    // ==================== Init ====================

    function init() {
        var projectId = NRM.state.currentProjectId; // null = "全部"
        var container = document.getElementById('history-container');
        if (!container) return;

        selectedRecords = {};
        allRecordsFlat = [];

        var records = NRM.bridge.getHistory(projectId) || [];

        if (records.length === 0) {
            container.innerHTML = '<p class="placeholder-text">暂无操作记录</p>';
            updateTopButtons();
            return;
        }

        // Refresh projects list for "全部" mode (used by showDetails)
        if (projectId === null) {
            var fresh = NRM.bridge.getAllProjects();
            if (fresh) NRM.state.projects = fresh;
        }

        // Flatten for selection tracking
        allRecordsFlat = records;

        // Group by operation_time
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
            var firstRec = group.records[0];
            var isExport = firstRec && firstRec.operationType === 'export';
            var typeLabel = isExport ? '&#128228; 导出' : '&#9852; 回收';

            // Compute batch-level states for conditional buttons
            var allExcluded = group.records.every(function(r) { return r.excludeFromStats; });
            var allNotRollbackable = group.records.every(function(r) {
                var rb = r.rollbackFailureReason || '';
                return (!r.destPath || r.destPath === '') || rb !== '';
            });
            var idsStr = group.records.map(function(r) { return "'" + r.recordId + "'"; }).join(',');

            // Batch header
            html += '<div class="history-group">';
            html += '<div class="history-group-header">';
            html += '<span class="history-time">' + timeLabel + '</span>';
            html += '<span class="history-type">' + typeLabel + '</span>';
            // Batch hover actions
            html += '<span class="batch-actions">';
            html += '<button class="btn btn-sm" onclick="NRM.pages.history.batchOp(\'hide\',[' +
                idsStr + '])">隐藏</button>';
            html += '<button class="btn btn-sm" onclick="NRM.pages.history.batchOp(\'delete\',[' +
                idsStr + '])">删除</button>';
            // Exclude: show "计入" if all are excluded, otherwise "不计入"
            html += '<button class="btn btn-sm' + (allExcluded ? ' btn-exclude-toggled' : '') +
                '" onclick="NRM.pages.history.batchOp(\'exclude\',[' + idsStr + '])">' +
                (allExcluded ? '计入' : '不计入') + '</button>';
            // Rollback: disabled if all are not rollbackable
            if (allNotRollbackable) {
                html += '<button class="btn btn-sm btn-rollback-disabled" disabled title="该批次无记录可撤销">撤销</button>';
            } else {
                html += '<button class="btn btn-sm" onclick="NRM.pages.history.batchOp(\'rollback\',[' +
                    idsStr + '])">撤销</button>';
            }
            html += '</span>';
            html += '<span class="history-count">' + group.records.length + ' 项</span>';
            html += '</div>';

            // Table
            html += '<table class="history-table"><thead><tr>';
            html += '<th class="h-col-check"><input type="checkbox" class="group-checkbox" data-ids="' +
                group.records.map(function(r) { return r.recordId; }).join(',') + '"></th>';
            html += '<th class="h-col-name">文件名</th>';
            html += '<th class="h-col-type">类型</th>';
            html += '<th class="h-col-size">大小</th>';
            html += '</tr></thead><tbody>';

            group.records.forEach(function(r) {
                // Rollback status from rollbackFailureReason field (V3 semantics):
                //   ""         → not yet attempted
                //   "success"  → rollback succeeded
                //   other text → rollback failed (the text is the reason)
                var rbReason = r.rollbackFailureReason || '';
                var rbSuccess = (rbReason === 'success');
                var rbFailed = (rbReason !== '' && rbReason !== 'success');
                var isFailed = (!r.destPath || r.destPath === '');

                // Row class (must combine into single class attribute)
                var rowClass = 'history-data-row';
                if (rbSuccess || isFailed) {
                    rowClass += ' history-row-grey';
                }
                if (selectedRecords[r.recordId]) {
                    rowClass += ' history-row-selected';
                }

                // Tags inline after filename
                var tagsHtml = '';
                if (r.tags && r.tags.length > 0) {
                    tagsHtml = '<span class="history-inline-tags">' +
                        r.tags.map(function(t) {
                            return '<span class="tag-badge">' + escapeHtml(t) + '</span>';
                        }).join('') + '</span>';
                }

                html += '<tr class="' + rowClass + '" data-record-id="' + r.recordId + '">';

                // Checkbox
                html += '<td class="h-col-check">';
                html += '<input type="checkbox" class="row-checkbox" data-id="' + r.recordId + '"' +
                    (selectedRecords[r.recordId] ? ' checked' : '') + '>';
                html += '</td>';

                // Filename + tags + hover actions
                html += '<td class="h-col-name">';
                html += '<div class="history-name-cell">';
                html += '<span class="history-name-text' + (rbSuccess ? ' rolled-back' : '') + '">' +
                    escapeHtml(r.originalName) + '</span>';
                html += tagsHtml;
                // Row hover actions
                html += '<span class="history-row-actions">';

                // Rollback button logic:
                if (rbSuccess) {
                    // Rollback succeeded: no rollback button, no exclude button
                    // (buttons below: only details, hide, delete)
                } else if (rbFailed) {
                    // Rollback failed: disabled button with failure reason tooltip
                    html += '<button class="btn btn-sm btn-rollback-disabled" title="' +
                        escapeAttr(rbReason) + '" disabled>撤销</button>';
                } else if (r.destPath && r.destPath !== '') {
                    // Not yet attempted, operation was successful → active rollback
                    html += '<button class="btn btn-sm" onclick="NRM.pages.history.singleOp(\'rollback\',\'' +
                        r.recordId + '\')" title="撤销">撤销</button>';
                }

                // Details button
                html += '<button class="btn btn-sm" onclick="NRM.pages.history.showDetails(\'' +
                    r.recordId + '\')" title="详情">详情</button>';
                // Hide
                html += '<button class="btn btn-sm" onclick="NRM.pages.history.singleOp(\'hide\',\'' +
                    r.recordId + '\')">隐藏</button>';
                // Delete
                html += '<button class="btn btn-sm" onclick="NRM.pages.history.singleOp(\'delete\',\'' +
                    r.recordId + '\')">删除</button>';
                // Exclude from stats (hidden for successfully rolled-back records)
                if (!rbSuccess) {
                    var exclClass = r.excludeFromStats ? 'btn-exclude-toggled' : 'btn-exclude-off';
                    html += '<button class="btn btn-sm ' + exclClass + '" onclick="NRM.pages.history.singleOp(\'exclude\',\'' +
                        r.recordId + '\')">' + (r.excludeFromStats ? '计' : '不计') + '</button>';
                }
                html += '</span>';
                html += '</div>';
                html += '</td>';

                // Type (fixed width)
                html += '<td class="h-col-type">' + escapeHtml(r.fileType || '') + '</td>';

                // Size (fixed width)
                html += '<td class="h-col-size">' + (r.fileSizeFormatted || '0 B') + '</td>';

                html += '</tr>';
            });

            html += '</tbody></table></div>';
        });

        container.innerHTML = html;

        // Attach event listeners
        attachCheckboxListeners();
        updateTopButtons();
    }

    // ==================== Selection ====================

    function attachCheckboxListeners() {
        // Row checkboxes
        var rowCbs = document.querySelectorAll('#history-container .row-checkbox');
        rowCbs.forEach(function(cb) {
            cb.addEventListener('click', function(e) {
                e.stopPropagation();
                var id = this.getAttribute('data-id');
                toggleSelection(id);
            });
        });

        // Row click: toggle selection when clicking anywhere on the row (except buttons)
        var rows = document.querySelectorAll('#history-container tr.history-data-row');
        rows.forEach(function(row) {
            row.addEventListener('click', function(e) {
                // Don't toggle if clicking a button, checkbox, or inside actions
                var tag = e.target.tagName.toLowerCase();
                if (tag === 'button' || tag === 'input') return;
                // Check if click is inside action buttons
                var el = e.target;
                while (el && el !== row) {
                    if (el.classList.contains('history-row-actions') ||
                        el.classList.contains('batch-actions')) return;
                    el = el.parentElement;
                }
                var id = row.getAttribute('data-record-id');
                if (id) toggleSelection(id);
            });
        });

        // Group checkboxes
        var groupCbs = document.querySelectorAll('#history-container .group-checkbox');
        groupCbs.forEach(function(cb) {
            cb.addEventListener('click', function(e) {
                e.stopPropagation();
                var ids = (this.getAttribute('data-ids') || '').split(',');
                if (this.checked) {
                    ids.forEach(function(id) { selectedRecords[id] = true; });
                } else {
                    ids.forEach(function(id) { delete selectedRecords[id]; });
                }
                // Update all row checkboxes in this group
                ids.forEach(function(id) { updateRowCheckbox(id); updateRowHighlight(id); });
                updateTopButtons();
            });
        });
    }

    function toggleSelection(id) {
        if (selectedRecords[id]) {
            delete selectedRecords[id];
        } else {
            selectedRecords[id] = true;
        }
        updateRowCheckbox(id);
        updateRowHighlight(id);
        updateGroupCheckboxes();
        updateTopButtons();
    }

    function updateRowCheckbox(id) {
        var cb = document.querySelector('#history-container .row-checkbox[data-id="' + id + '"]');
        if (cb) cb.checked = !!selectedRecords[id];
    }

    function updateRowHighlight(id) {
        var row = document.querySelector('#history-container tr.history-data-row[data-record-id="' + id + '"]');
        if (row) {
            row.classList.toggle('history-row-selected', !!selectedRecords[id]);
        }
    }

    function updateGroupCheckboxes() {
        var groupCbs = document.querySelectorAll('#history-container .group-checkbox');
        groupCbs.forEach(function(gcb) {
            var ids = (gcb.getAttribute('data-ids') || '').split(',');
            var allChecked = ids.every(function(id) { return !!selectedRecords[id]; });
            var noneChecked = ids.every(function(id) { return !selectedRecords[id]; });
            gcb.checked = allChecked;
            gcb.indeterminate = !allChecked && !noneChecked;
        });
    }

    function updateTopButtons() {
        var ids = getSelectedIds();
        var count = ids.length;
        var label = document.getElementById('history-top-label');
        var btnHide = document.getElementById('btn-top-hide');
        var btnDelete = document.getElementById('btn-top-delete');
        var btnExclude = document.getElementById('btn-top-exclude');
        var btnRollback = document.getElementById('btn-top-rollback');

        if (count === 0) {
            // No selection: hide all action buttons, show label
            if (label) label.textContent = '';
            if (btnHide) btnHide.style.display = 'none';
            if (btnDelete) btnDelete.style.display = 'none';
            if (btnExclude) btnExclude.style.display = 'none';
            if (btnRollback) btnRollback.style.display = 'none';
            return;
        }

        // Has selection: show buttons with conditional states
        if (label) label.textContent = '已选 ' + count + ' 项';

        // Hide and Delete always shown
        if (btnHide) btnHide.style.display = '';
        if (btnDelete) btnDelete.style.display = '';

        // Exclude: compute if all selected are excluded
        var allSelectedExcluded = ids.every(function(id) {
            var r = findRecord(id);
            return r && r.excludeFromStats;
        });
        if (btnExclude) {
            btnExclude.style.display = '';
            btnExclude.textContent = allSelectedExcluded ? '计入' : '不计入';
            btnExclude.className = 'btn btn-sm' + (allSelectedExcluded ? ' btn-exclude-toggled' : '');
        }

        // Rollback: disable if all selected are not rollbackable
        var allSelectedNotRollbackable = ids.every(function(id) {
            var r = findRecord(id);
            if (!r) return true;
            var rb = r.rollbackFailureReason || '';
            return (!r.destPath || r.destPath === '') || rb !== '';
        });
        if (btnRollback) {
            btnRollback.style.display = '';
            btnRollback.disabled = allSelectedNotRollbackable;
            btnRollback.className = 'btn btn-sm' + (allSelectedNotRollbackable ? ' btn-rollback-disabled' : '');
            btnRollback.title = allSelectedNotRollbackable ? '所选记录均不可撤销' : '撤销选中的记录';
        }
    }

    // ==================== Operations ====================

    function singleOp(op, recordId) {
        switch (op) {
            case 'hide':
                NRM.bridge.setRecordHidden(recordId, true);
                NRM.ui.showToast('记录已隐藏');
                break;
            case 'delete':
                NRM.components.modal.confirm('确认删除', '删除后可在设置页面恢复',
                    function() {
                        NRM.bridge.setRecordDeleted(recordId, true);
                        NRM.ui.showToast('记录已删除');
                        init();
                    });
                return; // don't refresh immediately
            case 'exclude':
                var rec = findRecord(recordId);
                var newVal = !(rec && rec.excludeFromStats);
                NRM.bridge.setRecordExcludeFromStats(recordId, newVal);
                NRM.ui.showToast(newVal ? '已设为不计入统计' : '已恢复计入统计');
                break;
            case 'rollback':
                NRM.components.modal.confirm('确认撤销', '确定要撤销此操作吗？',
                    function() {
                        var ok = NRM.bridge.rollbackRecord(recordId);
                        if (ok !== null) NRM.ui.showToast('操作已撤销');
                        // Error toast already shown by bridge on failure
                        init();
                    });
                return; // don't refresh immediately
        }
        delete selectedRecords[recordId];
        init();
    }

    function batchOp(op, ids) {
        if (!ids || ids.length === 0) return;
        switch (op) {
            case 'hide':
                ids.forEach(function(id) { NRM.bridge.setRecordHidden(id, true); });
                NRM.ui.showToast('已隐藏 ' + ids.length + ' 条记录');
                break;
            case 'delete':
                NRM.components.modal.confirm('确认删除', '确定要删除这 ' + ids.length + ' 条记录吗？',
                    function() {
                        ids.forEach(function(id) { NRM.bridge.setRecordDeleted(id, true); });
                        NRM.ui.showToast('已删除 ' + ids.length + ' 条记录');
                        ids.forEach(function(id) { delete selectedRecords[id]; });
                        init();
                    });
                return;
            case 'exclude':
                // Toggle: if all are excluded, un-exclude them; otherwise exclude all
                var allExcluded = ids.every(function(id) {
                    var r = findRecord(id);
                    return r && r.excludeFromStats;
                });
                var newVal = !allExcluded;
                ids.forEach(function(id) { NRM.bridge.setRecordExcludeFromStats(id, newVal); });
                NRM.ui.showToast(newVal ? '已设为不计入统计' : '已恢复计入统计');
                break;
            case 'rollback':
                NRM.components.modal.confirm('确认撤销', '确定要撤销这 ' + ids.length + ' 条记录吗？',
                    function() {
                        var ok = 0, fail = 0;
                        ids.forEach(function(id) {
                            var res = NRM.bridge.rollbackRecord(id);
                            if (res !== null) { ok++; } else { fail++; }
                        });
                        var msg = '撤销完成: ' + ok + ' 成功';
                        if (fail > 0) msg += ', ' + fail + ' 失败';
                        NRM.ui.showToast(msg);
                        ids.forEach(function(id) { delete selectedRecords[id]; });
                        init();
                    });
                return;
        }
        ids.forEach(function(id) { delete selectedRecords[id]; });
        init();
    }

    // ==================== Export / Import ====================

    function exportData() {
        var data = NRM.bridge.exportAllRecords();
        if (!data) {
            NRM.ui.showError('导出失败');
            return;
        }
        var jsonStr = JSON.stringify(data, null, 2);
        var recordCount = Array.isArray(data) ? data.length : 0;

        // Generate default filename: "2026-06-07_18-30-00.json"
        var now = new Date();
        var defaultName = now.getFullYear() + '-' +
            pad(now.getMonth() + 1) + '-' + pad(now.getDate()) + '_' +
            pad(now.getHours()) + '-' + pad(now.getMinutes()) + '-' + pad(now.getSeconds()) + '.json';

        // Capture jsonStr in closure for the save button callback
        var jsonToSave = jsonStr;
        NRM.components.modal.show('导出数据预览',
            '<textarea readonly style="width:100%;height:300px;font-family:monospace;font-size:11px;">' +
            escapeHtml(jsonStr) + '</textarea>' +
            '<p style="margin-top:8px;color:var(--text-muted);font-size:12px;">共 ' + recordCount + ' 条记录</p>',
            [
                { text: '关闭', cls: '' },
                { text: '导出到文件', cls: 'btn-primary', callback: function() {
                    var result = NRM.bridge.saveExportFile(defaultName, jsonToSave);
                    if (result) NRM.ui.showToast(result);
                }}
            ]);
    }

    function importData() {
        var content = NRM.bridge.openImportFile();
        if (!content) return; // user cancelled
        try {
            JSON.parse(content);
        } catch(e) {
            NRM.ui.showError('JSON 格式错误: ' + e.message);
            return;
        }
        var result = NRM.bridge.importRecords(content);
        if (result) {
            NRM.ui.showToast(result);
            init();
        }
    }

    function pad(n) {
        return n < 10 ? '0' + n : '' + n;
    }

    // ==================== Top-bar batch ops ====================

    function batchHide() {
        var ids = getSelectedIds();
        if (ids.length === 0) return;
        batchOp('hide', ids);
    }

    function batchDelete() {
        var ids = getSelectedIds();
        if (ids.length === 0) return;
        batchOp('delete', ids);
    }

    function batchToggleExclude() {
        var ids = getSelectedIds();
        if (ids.length === 0) return;
        batchOp('exclude', ids);
    }

    function batchRollback() {
        var ids = getSelectedIds();
        if (ids.length === 0) return;
        batchOp('rollback', ids);
    }

    function getSelectedIds() {
        return Object.keys(selectedRecords);
    }

    function findRecord(id) {
        for (var i = 0; i < allRecordsFlat.length; i++) {
            if (allRecordsFlat[i].id === id) return allRecordsFlat[i];
        }
        return null;
    }

    // ==================== Details Modal ====================

    function showDetails(recordId) {
        var r = findRecord(recordId);
        if (!r) return;

        // Get project name
        var projName = '';
        (NRM.state.projects || []).forEach(function(p) {
            if (p.id === r.projectId) projName = p.name;
        });

        var opStatus = (r.destPath && r.destPath !== '') ? '成功' : '失败';
        var typeMap = { 'export': '导出', 'recycle': '回收' };

        var rbReason = r.rollbackFailureReason || '';
        var rbLabel = '';
        if (rbReason === 'success') {
            rbLabel = '已撤销';
        } else if (rbReason !== '') {
            rbLabel = '撤销失败';
        } else {
            rbLabel = '未撤销';
        }

        var rows = [
            ['文件名', escapeHtml(r.originalName)],
            ['新名称', escapeHtml(r.newName || '-')],
            ['源路径', escapeHtml(r.sourcePath || '-')],
            ['目标路径', escapeHtml(r.destPath || '-')],
            ['类型', escapeHtml(r.fileType || '-')],
            ['大小', r.fileSizeFormatted || '0 B'],
            ['操作', typeMap[r.operationType] || r.operationType],
            ['项目', escapeHtml(projName || ('#' + r.projectId))],
            ['操作时间', r.operationTime || '-'],
            ['完成时间', r.successTime || '-'],
            ['状态', opStatus],
            ['撤销状态', rbLabel],
            ['标签', r.tags && r.tags.length > 0 ? r.tags.join(', ') : '(无)']
        ];

        if (rbReason !== '' && rbReason !== 'success') {
            rows.push(['撤销失败原因', escapeHtml(rbReason)]);
        }

        var html = '<div class="details-grid">';
        rows.forEach(function(pair) {
            html += '<span class="dl">' + pair[0] + '</span>';
            html += '<span class="dv">' + pair[1] + '</span>';
        });
        html += '</div>';

        NRM.components.modal.show('操作详情', html, null);
    }

    // ==================== Helpers ====================

    function escapeHtml(str) {
        var div = document.createElement('div');
        div.textContent = str || '';
        return div.innerHTML;
    }

    function escapeAttr(str) {
        return (str || '').replace(/&/g, '&amp;').replace(/"/g, '&quot;')
            .replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/'/g, '&#39;');
    }

    return {
        init: init,
        singleOp: singleOp,
        batchOp: batchOp,
        batchHide: batchHide,
        batchDelete: batchDelete,
        batchToggleExclude: batchToggleExclude,
        batchRollback: batchRollback,
        showDetails: showDetails,
        exportData: exportData,
        importData: importData
    };
})();
