/**
 * context-menu.js - Right-click context menu for file operations.
 */
NRM.components = NRM.components || {};

NRM.components.contextMenu = (function() {
    'use strict';

    var menu = null;

    function init() {
        menu = document.getElementById('context-menu');
        if (!menu) return;

        // Hide on click elsewhere
        document.addEventListener('click', function() {
            hide();
        });

        // Menu item click handlers
        menu.querySelectorAll('li[data-action]').forEach(function(item) {
            item.addEventListener('click', function() {
                var action = this.getAttribute('data-action');
                handleAction(action);
                hide();
            });
        });
    }

    function show(x, y) {
        if (!menu) init();
        if (!menu) return;

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

    function handleAction(action) {
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

        var tagNameAdded = false;

        function doAddTags() {
            var input = document.getElementById('tag-input');
            if (!input) return;
            var tagName = input.value.trim();
            if (!tagName) return;
            filePaths.forEach(function(fp) {
                NRM.bridge.addTag(fp, tagName, projectId);
            });
            tagNameAdded = true;
            input.value = '';
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

    return { init: init, show: show, hide: hide };
})();
