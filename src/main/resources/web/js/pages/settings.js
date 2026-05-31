/**
 * settings.js - Settings page: project CRUD.
 */
NRM.pages = NRM.pages || {};

NRM.pages.settings = (function() {
    'use strict';

    var editingProjectId = null;

    function init() {
        renderProjectList();
        if (!editingProjectId) {
            document.getElementById('settings-form-container').innerHTML =
                '<p class="placeholder-text">选择一个项目编辑，或创建新项目</p>';
        }
    }

    function renderProjectList() {
        var projects = NRM.bridge.getAllProjects();
        NRM.state.projects = projects || [];

        var list = document.getElementById('project-list-settings');
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
        var container = document.getElementById('settings-form-container');
        if (!container) return;

        var name = project ? project.name : '';
        var paths = project ? (project.paths || []).join('\n') : '';
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
            '  <label>目录路径（每行一个）</label>',
            '  <textarea id="f-paths" placeholder="C:\\Users\\Example\\Documents\\nD:\\Downloads">' + escapeHtml(paths) + '</textarea>',
            '</div>',
            '<div class="form-group">',
            '  <label>导出目标目录</label>',
            '  <div class="path-browse-row">',
            '    <input type="text" id="f-export-dir" value="' + escapeHtml(exportDir) + '" placeholder="导出目录路径">',
            '    <button class="btn btn-sm" onclick="NRM.pages.settings.browseExportDir()">浏览...</button>',
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
            '  <button class="btn btn-primary" onclick="NRM.pages.settings.save()">保存</button>',
            (project ? '<button class="btn btn-danger" onclick="NRM.pages.settings.deleteProject()">删除项目</button>' : ''),
            '  <button class="btn" onclick="NRM.pages.settings.cancel()">取消</button>',
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
        var elPaths = document.getElementById('f-paths');
        var elExportDir = document.getElementById('f-export-dir');
        var elExportPrefix = document.getElementById('f-export-prefix');
        var elRecyclePrefix = document.getElementById('f-recycle-prefix');

        var name = elName ? elName.value.trim() : '';
        var pathsText = elPaths ? elPaths.value.trim() : '';
        var exportDir = elExportDir ? elExportDir.value.trim() : '';
        var exportPrefix = elExportPrefix ? elExportPrefix.value.trim() : '';
        var recyclePrefix = elRecyclePrefix ? elRecyclePrefix.value.trim() : '';

        if (!name) {
            NRM.ui.showError('请输入项目名称');
            return;
        }
        var paths = pathsText ? pathsText.split('\n').map(function(p) { return p.trim(); }).filter(function(p) { return p.length > 0; }) : [];

        if (editingProjectId) {
            var updated = NRM.bridge.updateProject(editingProjectId, name, paths, exportDir, exportPrefix, recyclePrefix);
            if (updated) {
                NRM.ui.showToast('项目已更新');
                editingProjectId = updated.id;
                renderProjectList();
                NRM.components.projectTabs.render(NRM.state.currentPage === 'statistics');
            }
        } else {
            var created = NRM.bridge.createProject(name, paths, exportDir, exportPrefix, recyclePrefix);
            if (created) {
                NRM.ui.showToast('项目已创建');
                editingProjectId = created.id;
                renderProjectList();
                showEditForm(created.id);
                NRM.components.projectTabs.render(NRM.state.currentPage === 'statistics');
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
                document.getElementById('settings-form-container').innerHTML =
                    '<p class="placeholder-text">选择一个项目编辑，或创建新项目</p>';
                renderProjectList();
                NRM.components.projectTabs.render(NRM.state.currentPage === 'statistics');
            }
        );
    }

    function cancel() {
        editingProjectId = null;
        document.getElementById('settings-form-container').innerHTML =
            '<p class="placeholder-text">选择一个项目编辑，或创建新项目</p>';
        renderProjectList();
    }

    function escapeHtml(str) {
        var div = document.createElement('div');
        div.textContent = str || '';
        return div.innerHTML;
    }

    return {
        init: init,
        showCreateForm: showCreateForm,
        browseExportDir: browseExportDir,
        save: save,
        deleteProject: deleteProject,
        cancel: cancel
    };
})();
