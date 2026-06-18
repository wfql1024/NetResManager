/**
 * settings.js - Settings page: dual-pane layout.
 *
 * Left sidebar: settings items list.
 * Right content: detail for selected setting.
 *
 * Current items:
 *   - 记录管理: sub-sections for hidden records and deleted records.
 */
NRM.pages = NRM.pages || {};

NRM.pages.settings = (function() {
    'use strict';

    var initialized = false;

    function init() {
        if (!initialized) {
            attachNavListeners();
            initialized = true;
        }
        // Load content for the active setting
        var activeItem = document.querySelector('#settings-nav li.active');
        var setting = activeItem ? activeItem.getAttribute('data-setting') : 'records';
        showSetting(setting);
        if (setting === 'records') {
            loadHidden();
            loadDeleted();
        }
    }

    function attachNavListeners() {
        var items = document.querySelectorAll('#settings-nav li[data-setting]');
        items.forEach(function(li) {
            li.addEventListener('click', function() {
                items.forEach(function(item) { item.classList.remove('active'); });
                this.classList.add('active');
                var setting = this.getAttribute('data-setting');
                showSetting(setting);
            });
        });
    }

    function showSetting(setting) {
        // Hide all setting sections
        var sections = document.querySelectorAll('#settings-content .settings-section-content');
        sections.forEach(function(s) { s.style.display = 'none'; });

        // Show the selected section
        var el = document.getElementById('settings-section-' + setting);
        if (el) el.style.display = '';

        // Load data for the section
        switch (setting) {
            case 'records':
                loadHidden();
                loadDeleted();
                break;
            case 'appearance':
                loadAppearance();
                break;
        }
    }

    // ==================== Appearance ====================

    function loadAppearance() {
        var currentTheme = NRM.state._themePreference || 'auto';

        var buttons = document.querySelectorAll('#theme-segmented .theme-seg-btn');
        buttons.forEach(function(btn) {
            var btnTheme = btn.getAttribute('data-theme');
            if (btnTheme === currentTheme) {
                btn.classList.add('active');
            } else {
                btn.classList.remove('active');
            }
        });

        // Attach click listeners (only once)
        if (!loadAppearance._attached) {
            loadAppearance._attached = true;
            buttons.forEach(function(btn) {
                btn.addEventListener('click', function() {
                    var theme = this.getAttribute('data-theme');
                    // Update active state
                    buttons.forEach(function(b) { b.classList.remove('active'); });
                    this.classList.add('active');
                    // Save and apply (NRM.applyTheme handles both memory + localStorage)
                    NRM.applyTheme(theme);
                });
            });
        }
    }

    // ==================== Hidden Records ====================

    function loadHidden() {
        var container = document.getElementById('settings-hidden-content');
        if (!container) return;

        var records = NRM.bridge.getHiddenRecords(null) || [];

        if (records.length === 0) {
            container.innerHTML = '<p class="placeholder-text">暂无已隐藏的记录</p>';
            return;
        }

        container.innerHTML = renderGroupedRecords(records, 'unhide');
    }

    // ==================== Deleted Records ====================

    function loadDeleted() {
        var container = document.getElementById('settings-deleted-content');
        if (!container) return;

        var records = NRM.bridge.getDeletedRecords(null) || [];

        if (records.length === 0) {
            container.innerHTML = '<p class="placeholder-text">暂无已删除的记录</p>';
            return;
        }

        container.innerHTML = renderGroupedRecords(records, 'restore');
    }

    // ==================== Shared Rendering ====================

    function renderGroupedRecords(records, actionType) {
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
            var idsStr = group.records.map(function(r) { return "'" + r.recordId + "'"; }).join(',');

            html += '<div class="history-group">';
            html += '<div class="history-group-header">';
            html += '<span class="history-time">' + timeLabel + '</span>';
            html += '<span class="history-type">' + typeLabel + '</span>';
            // Batch hover actions (same as history page)
            html += '<span class="batch-actions">';
            if (actionType === 'unhide') {
                html += '<button class="btn btn-sm" onclick="NRM.pages.settings.batchUnhide([' + idsStr + '])">全部取消隐藏</button>';
            } else {
                html += '<button class="btn btn-sm" onclick="NRM.pages.settings.batchRestore([' + idsStr + '])">全部恢复</button>';
            }
            html += '</span>';
            html += '<span class="history-count">' + group.records.length + ' 项</span>';
            html += '</div>';

            html += '<table class="history-table"><thead><tr>';
            html += '<th class="h-col-name">文件名</th>';
            html += '<th class="h-col-type">类型</th>';
            html += '<th class="h-col-size">大小</th>';
            html += '</tr></thead><tbody>';

            group.records.forEach(function(r) {
                var tagsHtml = '';
                if (r.tags && r.tags.length > 0) {
                    tagsHtml = '<span class="history-inline-tags">' +
                        r.tags.map(function(t) {
                            return '<span class="tag-badge">' + escapeHtml(t) + '</span>';
                        }).join('') + '</span>';
                }

                html += '<tr class="history-data-row">';
                html += '<td class="h-col-name">';
                html += '<div class="history-name-cell">';
                html += '<span class="history-name-text">' + escapeHtml(r.originalName) + '</span>';
                html += tagsHtml;
                // Hover action button at right of filename
                html += '<span class="history-row-actions">';
                if (actionType === 'unhide') {
                    html += '<button class="btn btn-sm" onclick="NRM.pages.settings.unhideRecord(\'' +
                        r.recordId + '\')">取消隐藏</button>';
                } else {
                    html += '<button class="btn btn-sm" onclick="NRM.pages.settings.restoreRecord(\'' +
                        r.recordId + '\')">恢复记录</button>';
                }
                html += '</span>';
                html += '</div>';
                html += '</td>';
                html += '<td class="h-col-type">' + escapeHtml(r.fileType || '') + '</td>';
                html += '<td class="h-col-size">' + (r.fileSizeFormatted || '0 B') + '</td>';
                html += '</tr>';
            });

            html += '</tbody></table></div>';
        });

        return html;
    }

    function batchUnhide(ids) {
        ids.forEach(function(id) { NRM.bridge.setRecordHidden(id, false); });
        NRM.ui.showToast('已取消隐藏 ' + ids.length + ' 条记录');
        loadHidden();
    }

    function batchRestore(ids) {
        ids.forEach(function(id) { NRM.bridge.setRecordDeleted(id, false); });
        NRM.ui.showToast('已恢复 ' + ids.length + ' 条记录');
        loadDeleted();
    }

    // ==================== Operations ====================

    function unhideRecord(recordId) {
        NRM.bridge.setRecordHidden(recordId, false);
        NRM.ui.showToast('记录已取消隐藏');
        loadHidden();
    }

    function restoreRecord(recordId) {
        NRM.bridge.setRecordDeleted(recordId, false);
        NRM.ui.showToast('记录已恢复');
        loadDeleted();
    }

    // ==================== Helpers ====================

    function escapeHtml(str) {
        var div = document.createElement('div');
        div.textContent = str || '';
        return div.innerHTML;
    }

    return {
        init: init,
        unhideRecord: unhideRecord,
        restoreRecord: restoreRecord,
        batchUnhide: batchUnhide,
        batchRestore: batchRestore
    };
})();
