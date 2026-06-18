/**
 * manage.js - Manage page: "全部" project list & file browsing, export, recycle, tag operations.
 *
 * When "全部" tab is active (currentProjectId === null):
 *   Shows the project list + edit form (moved from settings).
 * When a specific project is selected:
 *   Shows the file table with browsing.
 */
NRM.pages = NRM.pages || {};

NRM.pages.manage = (function() {
    'use strict';

    var editingProjectId = null;

    // ==================== Init / Refresh ====================

    function init() {
        var allView = document.getElementById('manage-all-view');
        var projView = document.getElementById('manage-project-view');

        if (NRM.state.currentProjectId === null) {
            // "全部" mode: show project list
            if (allView) allView.classList.remove('hidden');
            if (projView) projView.style.display = 'none';
            // Hide manage top actions
            var mta = document.getElementById('manage-top-actions');
            if (mta) mta.style.display = 'none';
            renderProjectList();
            if (!editingProjectId) {
                var detail = document.getElementById('manage-all-detail');
                if (detail) detail.innerHTML =
                    '<p class="placeholder-text">选择一个项目编辑，或创建新项目</p>';
            }
        } else {
            // Project mode: show file table
            if (allView) allView.classList.add('hidden');
            if (projView) projView.style.display = '';

            var projectId = NRM.state.currentProjectId;
            NRM.ui.showProgress('扫描文件中...');
            var groups = NRM.bridge.scanProject(projectId, NRM.state.currentDirectory);
            NRM.ui.hideProgress();

            if (groups) {
                NRM.state.files = groups;
                NRM.components.fileTable.render(groups);
            } else {
                document.getElementById('file-table-body').innerHTML =
                    '<tr class="empty-row"><td colspan="6">扫描失败或目录不可访问</td></tr>';
            }
        }
    }

    function refresh() {
        NRM.cancelFolderSizes();
        if (NRM.state.currentProjectId === null) {
            renderProjectList();
            return;
        }
        var projectId = NRM.state.currentProjectId;
        NRM.ui.showProgress('刷新中...');
        var groups = NRM.bridge.refreshScan(projectId, NRM.state.currentDirectory);
        NRM.ui.hideProgress();

        if (groups) {
            NRM.state.files = groups;
            NRM.components.fileTable.render(groups);
        }
    }

    // ==================== File Operations ====================

    function doExport(filePaths, projectId) {
        NRM.ui.showBlocking('正在导出 ' + filePaths.length + ' 个文件...');
        var result = NRM.bridge.exportFiles(filePaths, projectId);
        NRM.ui.hideBlocking();

        if (result) {
            var msg = '导出完成: ' + result.successCount + ' 成功';
            if (result.failCount > 0) {
                msg += ', ' + result.failCount + ' 失败';
                if (result.rolledBack) {
                    msg += ' (已回滚)';
                }
            }
            if (result.errors && result.errors.length > 0) {
                msg += '\n\n错误:';
                result.errors.forEach(function(e) {
                    msg += '\n' + e.path + ': ' + e.message;
                });
            }
            NRM.components.modal.alert('导出结果', msg.replace(/\n/g, '<br>'));
            NRM.state.selectedFiles.clear();
            refresh();
        }
    }

    function doRecycle(filePaths, projectId) {
        NRM.ui.showBlocking('正在回收 ' + filePaths.length + ' 个文件...');
        var result = NRM.bridge.recycleFiles(filePaths, projectId);
        NRM.ui.hideBlocking();

        if (result) {
            var msg = '回收完成: ' + result.successCount + ' 成功';
            if (result.failCount > 0) {
                msg += ', ' + result.failCount + ' 失败';
            }
            if (result.errors && result.errors.length > 0) {
                msg += '\n\n错误:';
                result.errors.forEach(function(e) {
                    msg += '\n' + e.path + ': ' + e.message;
                });
            }
            NRM.components.modal.alert('回收结果', msg.replace(/\n/g, '<br>'));
            NRM.state.selectedFiles.clear();
            refresh();
        }
    }

    // ==================== Project CRUD (全部 mode) ====================

    function renderProjectList() {
        var projects = NRM.bridge.getAllProjects();
        NRM.state.projects = projects || [];

        var list = document.getElementById('project-list-manage');
        if (!list) return;

        if (!projects || projects.length === 0) {
            list.innerHTML = '<li style="color:var(--text-muted);padding:8px;">暂无项目</li>';
            return;
        }

        list.innerHTML = projects.map(function(p) {
            var active = (editingProjectId === p.id) ? ' class="active"' : '';
            return '<li' + active + ' data-id="' + p.id + '">' + escapeHtml(p.name) + '</li>';
        }).join('');

        // Click handlers
        list.querySelectorAll('li[data-id]').forEach(function(li) {
            li.addEventListener('click', function() {
                editingProjectId = parseInt(this.getAttribute('data-id'));
                showEditForm(editingProjectId);
                renderProjectList();
            });
        });
    }

    function showCreateForm() {
        editingProjectId = null;
        renderProjectList();
        showEditForm(null);
    }

    function showEditForm(projectId) {
        var project = projectId ? NRM.bridge.getProject(projectId) : null;
        var container = document.getElementById('manage-all-detail');
        if (!container) return;

        var name = project ? project.name : '';
        var paths = project ? (project.paths || []) : [];
        var exportDir = project ? (project.exportDir || '') : '';
        var exportPrefix = project ? (project.exportPrefix || '') : '';
        var recyclePrefix = project ? (project.recyclePrefix || '') : '';

        container.innerHTML = [
            '<h3>' + (project ? '编辑项目' : '新建项目') + '</h3>',
            '<div class="form-group">',
            '  <label>项目名称</label>',
            '  <input type="text" id="f-name" value="' + escapeHtml(name) + '" placeholder="输入项目名称">',
            '</div>',
            '<div class="form-group">',
            '  <label>目录路径</label>',
            '  <div id="path-list" class="path-list">',
            buildPathRows(paths),
            '  </div>',
            '  <button class="btn btn-sm" onclick="NRM.pages.manage.addPathRow()" style="margin-top:6px;">+ 添加路径</button>',
            '</div>',
            '<div class="form-group">',
            '  <label>导出目标目录</label>',
            '  <div class="path-browse-row">',
            '    <input type="text" id="f-export-dir" value="' + escapeHtml(exportDir) + '" placeholder="导出目录路径">',
            '    <button class="btn btn-sm" onclick="NRM.pages.manage.browseExportDir()">浏览...</button>',
            '  </div>',
            '</div>',
            '<div class="form-group">',
            '  <label>导出前缀</label>',
            '  <input type="text" id="f-export-prefix" value="' + escapeHtml(exportPrefix) + '" placeholder="例如: EXP_">',
            '</div>',
            '<div class="form-group">',
            '  <label>回收前缀</label>',
            '  <input type="text" id="f-recycle-prefix" value="' + escapeHtml(recyclePrefix) + '" placeholder="例如: REC_">',
            '</div>',
            '<div class="form-actions">',
            '  <button class="btn btn-primary" onclick="NRM.pages.manage.save()">保存</button>',
            (project ? '<button class="btn btn-danger" onclick="NRM.pages.manage.deleteProject()">删除项目</button>' : ''),
            '  <button class="btn" onclick="NRM.pages.manage.cancel()">取消</button>',
            '</div>',
        ].join('');
    }

    function browseExportDir() {
        var dir = NRM.bridge.pickDirectory();
        if (dir) {
            var input = document.getElementById('f-export-dir');
            if (input) input.value = dir;
        }
    }

    function save() {
        var elName = document.getElementById('f-name');
        var elExportDir = document.getElementById('f-export-dir');
        var elExportPrefix = document.getElementById('f-export-prefix');
        var elRecyclePrefix = document.getElementById('f-recycle-prefix');

        var name = elName ? elName.value.trim() : '';
        var exportDir = elExportDir ? elExportDir.value.trim() : '';
        var exportPrefix = elExportPrefix ? elExportPrefix.value.trim() : '';
        var recyclePrefix = elRecyclePrefix ? elRecyclePrefix.value.trim() : '';

        if (!name) {
            NRM.ui.showError('请输入项目名称');
            return;
        }
        // Collect paths from dynamic list
        var paths = [];
        var inputs = document.querySelectorAll('#path-list .path-input');
        inputs.forEach(function(inp) {
            var val = inp.value.trim();
            if (val) paths.push(val);
        });

        if (editingProjectId) {
            var updated = NRM.bridge.updateProject(editingProjectId, name, paths, exportDir, exportPrefix, recyclePrefix);
            if (updated) {
                NRM.ui.showToast('项目已更新');
                editingProjectId = updated.id;
                renderProjectList();
                NRM.components.projectTabs.render(NRM.state.currentPage);
            }
        } else {
            var created = NRM.bridge.createProject(name, paths, exportDir, exportPrefix, recyclePrefix);
            if (created) {
                NRM.ui.showToast('项目已创建');
                editingProjectId = created.id;
                renderProjectList();
                showEditForm(created.id);
                NRM.components.projectTabs.render(NRM.state.currentPage);
            }
        }
    }

    function deleteProject() {
        if (!editingProjectId) return;
        NRM.components.modal.confirm('确认删除', '确定要删除此项目吗？所有相关记录将被永久删除。',
            function() {
                NRM.bridge.deleteProject(editingProjectId);
                NRM.ui.showToast('项目已删除');
                editingProjectId = null;
                var detail = document.getElementById('manage-all-detail');
                if (detail) detail.innerHTML =
                    '<p class="placeholder-text">选择一个项目编辑，或创建新项目</p>';
                renderProjectList();
                NRM.components.projectTabs.render(NRM.state.currentPage);
            }
        );
    }

    function cancel() {
        editingProjectId = null;
        var detail = document.getElementById('manage-all-detail');
        if (detail) detail.innerHTML =
            '<p class="placeholder-text">选择一个项目编辑，或创建新项目</p>';
        renderProjectList();
    }

    // ==================== Top Bar Batch Operations ====================

    function batchExport() {
        var selected = Array.from(NRM.state.selectedFiles);
        if (selected.length === 0) return;
        var projectId = NRM.state.currentProjectId;
        NRM.components.modal.confirm(
            '确认导出',
            '确定要导出选中的 ' + selected.length + ' 个文件吗？',
            function() { doExport(selected, projectId); }
        );
    }

    function batchRecycle() {
        var selected = Array.from(NRM.state.selectedFiles);
        if (selected.length === 0) return;
        var projectId = NRM.state.currentProjectId;
        NRM.components.modal.confirm(
            '确认回收',
            '确定要回收选中的 ' + selected.length + ' 个文件吗？',
            function() { doRecycle(selected, projectId); }
        );
    }

    function batchTag() {
        var selected = Array.from(NRM.state.selectedFiles);
        if (selected.length === 0) return;
        var projectId = NRM.state.currentProjectId;
        NRM.components.contextMenu.showTagDialog(selected, projectId);
    }

    // ==================== Path List Editor ====================

    function buildPathRows(paths) {
        if (!paths || paths.length === 0) {
            return '<div class="path-row"><input type="text" class="path-input" placeholder="C:\\Users\\Example\\Documents"><button class="btn btn-sm" onclick="NRM.pages.manage.browsePath(this)">浏览...</button><button class="btn btn-sm" onclick="NRM.pages.manage.removePathRow(this)">✕</button></div>';
        }
        var html = '';
        paths.forEach(function(p) {
            html += '<div class="path-row"><input type="text" class="path-input" value="'
                + escapeHtml(p) + '"><button class="btn btn-sm" onclick="NRM.pages.manage.browsePath(this)">浏览...</button><button class="btn btn-sm" onclick="NRM.pages.manage.removePathRow(this)">✕</button></div>';
        });
        return html;
    }

    function addPathRow() {
        var list = document.getElementById('path-list');
        if (!list) return;
        var row = document.createElement('div');
        row.className = 'path-row';
        row.innerHTML = '<input type="text" class="path-input" placeholder="C:\\Users\\Example\\Documents"><button class="btn btn-sm" onclick="NRM.pages.manage.browsePath(this)">浏览...</button><button class="btn btn-sm" onclick="NRM.pages.manage.removePathRow(this)">✕</button>';
        list.appendChild(row);
    }

    function removePathRow(btn) {
        var row = btn.parentElement;
        if (row) row.remove();
    }

    function browsePath(btn) {
        var dir = NRM.bridge.pickDirectory();
        if (dir) {
            var input = btn.parentElement.querySelector('.path-input');
            if (input) input.value = dir;
        }
    }

    // ==================== Navigation ====================

    function goHome() {
        NRM.state.currentDirectory = '';
        NRM.state.selectedFiles.clear();
        NRM.router.refresh();
    }

    // ==================== Helpers ====================

    function escapeHtml(str) {
        var div = document.createElement('div');
        div.textContent = str || '';
        return div.innerHTML;
    }

    return {
        init: init,
        refresh: refresh,
        doExport: doExport,
        doRecycle: doRecycle,
        goHome: goHome,
        batchExport: batchExport,
        batchRecycle: batchRecycle,
        batchTag: batchTag,
        showCreateForm: showCreateForm,
        browseExportDir: browseExportDir,
        addPathRow: addPathRow,
        removePathRow: removePathRow,
        browsePath: browsePath,
        save: save,
        deleteProject: deleteProject,
        cancel: cancel
    };
})();
