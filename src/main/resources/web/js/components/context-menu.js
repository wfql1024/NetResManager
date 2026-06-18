/**
 * context-menu.js - Right-click context menu, shared by manage and history pages.
 *
 * The menu adapts its items based on currentPage ('manage' or 'history').
 * Manage page: export, recycle, tag, open location, copy path
 * History page: rollback, details, hide, delete, exclude
 */
NRM.components = NRM.components || {};

NRM.components.contextMenu = (function() {
    'use strict';

    var menu = null;
    var currentContext = 'manage'; // 'manage' or 'history'

    function init() {
        menu = document.getElementById('context-menu');
        if (!menu) return;

        // Hide on click elsewhere
        document.addEventListener('click', function() {
            hide();
        });

        // Delegate menu item clicks
        menu.addEventListener('click', function(e) {
            var li = e.target.closest('li[data-action]');
            if (!li) return;
            var action = li.getAttribute('data-action');
            handleAction(action);
            hide();
        });
    }

    function show(x, y, context) {
        if (!menu) init();
        if (!menu) return;

        currentContext = context || NRM.state.currentPage || 'manage';
        renderMenuItems();

        // Position menu
        menu.style.display = 'block';
        menu.style.left = x + 'px';
        menu.style.top = y + 'px';

        // Keep within viewport
        var rect = menu.getBoundingClientRect();
        if (rect.right > window.innerWidth) {
            menu.style.left = (x - rect.width) + 'px';
        }
        if (rect.bottom > window.innerHeight) {
            menu.style.top = (y - rect.height) + 'px';
        }
    }

    function hide() {
        if (menu) menu.style.display = 'none';
    }

    function renderMenuItems() {
        var ul = menu.querySelector('ul');
        if (!ul) return;

        if (currentContext === 'manage') {
            ul.innerHTML = [
                '<li data-action="export" title="重命名后移动到导出目录">📤 导出</li>',
                '<li data-action="recycle" title="重命名后移入回收站">♻ 回收</li>',
                '<li class="menu-sep"></li>',
                '<li data-action="tag" title="编辑标签">📌 标签管理</li>',
                '<li class="menu-sep"></li>',
                '<li data-action="explorer" title="在资源管理器中打开">📂 打开位置</li>',
                '<li data-action="copy-path" title="复制路径">📋 复制路径</li>'
            ].join('');
        } else {
            // History page context menu
            ul.innerHTML = [
                '<li data-action="rollback" title="撤销此操作">↩ 撤销</li>',
                '<li data-action="details" title="查看详情">📋 详情</li>',
                '<li class="menu-sep"></li>',
                '<li data-action="hide" title="隐藏此记录">👁 隐藏</li>',
                '<li data-action="delete" title="删除此记录">🗑 删除</li>',
                '<li data-action="exclude" title="不计入统计">📊 不计入</li>'
            ].join('');
        }
    }

    function handleAction(action) {
        if (currentContext === 'manage') {
            handleManageAction(action);
        } else {
            handleHistoryAction(action);
        }
    }

    function handleManageAction(action) {
        var selected = Array.from(NRM.state.selectedFiles);
        if (selected.length === 0) return;

        var projectId = NRM.state.currentProjectId;
        if (!projectId) {
            NRM.ui.showError('请先选择一个项目');
            return;
        }

        switch (action) {
            case 'export':
                NRM.components.modal.confirm(
                    '确认导出',
                    '确定要导出选中的 ' + selected.length + ' 个文件吗？<br>文件将被重命名后移动到导出目录。',
                    function() {
                        NRM.pages.manage.doExport(selected, projectId);
                    }
                );
                break;

            case 'recycle':
                NRM.components.modal.confirm(
                    '确认回收',
                    '确定要回收选中的 ' + selected.length + ' 个文件吗？<br>文件将被重命名后移入回收站。',
                    function() {
                        NRM.pages.manage.doRecycle(selected, projectId);
                    }
                );
                break;

            case 'tag':
                showTagDialog(selected, projectId);
                break;

            case 'explorer':
                if (selected.length === 1) {
                    NRM.bridge.openFileExplorer(selected[0]);
                } else {
                    selected.forEach(function(p) {
                        NRM.bridge.openFileExplorer(p);
                    });
                }
                break;

            case 'copy-path':
                if (selected.length === 1) {
                    navigator.clipboard.writeText(selected[0]).then(function() {
                        NRM.ui.showToast('路径已复制');
                    });
                } else {
                    navigator.clipboard.writeText(selected.join('\n')).then(function() {
                        NRM.ui.showToast(selected.length + ' 个路径已复制');
                    });
                }
                break;
        }
    }

    function handleHistoryAction(action) {
        // Get selected record IDs from history page
        var ids = NRM.pages.history.getSelectedIds ? NRM.pages.history.getSelectedIds() : [];
        if (ids.length === 0) return;

        switch (action) {
            case 'rollback':
                NRM.pages.history.batchOp('rollback', ids);
                break;
            case 'details':
                if (ids.length === 1) {
                    NRM.pages.history.showDetails(ids[0]);
                }
                break;
            case 'hide':
                NRM.pages.history.batchOp('hide', ids);
                break;
            case 'delete':
                NRM.pages.history.batchOp('delete', ids);
                break;
            case 'exclude':
                NRM.pages.history.batchOp('exclude', ids);
                break;
        }
    }

    // ==================== Tag Dialog (shared) ====================

    function showTagDialog(filePaths, projectId) {
        var firstPath = filePaths[0];
        var tags = NRM.bridge.getTagsForFile(firstPath, projectId) || [];

        var content = document.createElement('div');
        content.innerHTML = [
            '<p>为选中项添加/编辑标签</p>',
            '<div class="form-group">',
            '  <label>当前标签:</label>',
            '  <div id="current-tags" style="margin-top:4px;">',
            (tags.length > 0
                ? tags.map(function(t) {
                    return '<span class="tag-badge">' + escapeHtml(t.tagName)
                         + ' <a href="#" onclick="event.preventDefault();NRM.bridge.removeTag(\''
                         + t.filePath.replace(/\\/g, '\\\\') + '\',\'' + t.tagName.replace(/'/g, "\\'") + '\',' + projectId + ');NRM.components.modal.close();NRM.router.refresh();" style="color:red;text-decoration:none;">&times;</a></span>';
                  }).join(' ')
                : '<span style="color:var(--text-muted)">无</span>'),
            '  </div>',
            '</div>',
            '<div class="form-group">',
            '  <label>添加标签:</label>',
            '  <input type="text" id="tag-input" placeholder="输入标签名">',
            '</div>',
            filePaths.length > 1 ? '<p style="color:var(--text-secondary);font-size:var(--font-size-sm);">将为 ' + filePaths.length + ' 个文件添加标签</p>' : ''
        ].join('');

        function doAddTags() {
            var input = document.getElementById('tag-input');
            if (!input) return;
            var tagName = input.value.trim();
            if (!tagName) return;
            filePaths.forEach(function(fp) {
                NRM.bridge.addTag(fp, tagName, projectId);
            });
            NRM.components.modal.close();
            NRM.router.refresh();
        }

        NRM.components.modal.show('标签管理', content, [
            { text: '关闭', cls: '', callback: function() {} },
            { text: '添加', cls: 'btn-primary', callback: doAddTags }
        ]);

        // Focus input and handle Enter key
        setTimeout(function() {
            var input = document.getElementById('tag-input');
            if (input) {
                input.focus();
                input.addEventListener('keydown', function(e) {
                    if (e.keyCode === 13 || e.key === 'Enter') {
                        e.preventDefault();
                        doAddTags();
                    }
                });
            }
        }, 150);
    }

    function escapeHtml(str) {
        var div = document.createElement('div');
        div.textContent = str || '';
        return div.innerHTML;
    }

    return {
        init: init,
        show: show,
        hide: hide,
        showTagDialog: showTagDialog
    };
})();
