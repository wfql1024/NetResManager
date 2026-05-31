/**
 * manage.js - Manage page: file browsing, export, recycle, tag operations.
 */
NRM.pages = NRM.pages || {};

NRM.pages.manage = (function() {
    'use strict';

    function init() {
        var projectId = NRM.state.currentProjectId;
        if (!projectId) {
            document.getElementById('file-table-body').innerHTML =
                '<tr class="empty-row"><td colspan="6">请选择或创建一个项目</td></tr>';
            document.getElementById('breadcrumb').innerHTML = '';
            return;
        }

        NRM.ui.showProgress('扫描文件中...');
        var groups = NRM.bridge.scanProject(projectId, NRM.state.currentDirectory);
        NRM.ui.hideProgress();

        if (groups) {
            NRM.state.files = groups;
            NRM.components.breadcrumb.render(NRM.state.currentDirectory);
            NRM.components.fileTable.render(groups);
        } else {
            document.getElementById('file-table-body').innerHTML =
                '<tr class="empty-row"><td colspan="6">扫描失败或目录不可访问</td></tr>';
        }
    }

    function refresh() {
        var projectId = NRM.state.currentProjectId;
        if (!projectId) return;

        NRM.ui.showProgress('刷新中...');
        var groups = NRM.bridge.refreshScan(projectId, NRM.state.currentDirectory);
        NRM.ui.hideProgress();

        if (groups) {
            NRM.state.files = groups;
            NRM.components.breadcrumb.render(NRM.state.currentDirectory);
            NRM.components.fileTable.render(groups);
        }
    }

    function doExport(filePaths, projectId) {
        NRM.ui.showProgress('导出中...');
        var result = NRM.bridge.exportFiles(filePaths, projectId);
        NRM.ui.hideProgress();

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
            refresh();
        }
    }

    function doRecycle(filePaths, projectId) {
        NRM.ui.showProgress('回收中...');
        var result = NRM.bridge.recycleFiles(filePaths, projectId);
        NRM.ui.hideProgress();

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
            refresh();
        }
    }

    return { init: init, refresh: refresh, doExport: doExport, doRecycle: doRecycle };
})();
