/**
 * bridge.js - JavaScript wrapper for Java-JS bridge (window.javaObject).
 */
window.NRM = window.NRM || {};

NRM.bridge = (function() {
    'use strict';

    function call(methodName) {
        var args = Array.prototype.slice.call(arguments, 1);
        try {
            if (typeof window.javaObject === 'undefined' || window.javaObject === null) {
                console.error('Java bridge not available');
                return null;
            }
            var resultJson = window.javaObject[methodName].apply(window.javaObject, args);
            if (resultJson === null || resultJson === undefined) return null;
            var result = JSON.parse(resultJson);
            if (!result.success) {
                var err = result.error || {};
                var errMsg = err.message || err.code || '(no message)';
                var fullMsg = '[' + methodName + '] ' + errMsg;
                console.error('Bridge error: ' + fullMsg, resultJson);
                NRM.ui.showError(fullMsg);
                return null;
            }
            return result.data;
        } catch (e) {
            var catchMsg = '[' + methodName + '] JS-Error: ' + (e.message || e);
            console.error(catchMsg);
            NRM.ui.showError(catchMsg);
            return null;
        }
    }

    return {
        // Projects
        getAllProjects: function() { return call('jsGetAllProjects'); },
        getProject: function(id) { return call('jsGetProject', id); },
        createProject: function(name, paths, exportDir, exportPrefix, recyclePrefix) {
            return call('jsCreateProject', name, JSON.stringify(paths), exportDir, exportPrefix, recyclePrefix);
        },
        updateProject: function(id, name, paths, exportDir, exportPrefix, recyclePrefix) {
            return call('jsUpdateProject', id, name, JSON.stringify(paths), exportDir, exportPrefix, recyclePrefix);
        },
        deleteProject: function(id) { return call('jsDeleteProject', id); },

        // File scanning
        scanProject: function(projectId, dir) { return call('jsScanProject', projectId, dir || ''); },
        refreshScan: function(projectId, dir) { return call('jsRefreshScan', projectId, dir || ''); },

        // File operations
        exportFiles: function(paths, projectId) {
            return call('jsExportFiles', JSON.stringify(paths), projectId);
        },
        recycleFiles: function(paths, projectId) {
            return call('jsRecycleFiles', JSON.stringify(paths), projectId);
        },

        // Tags
        addTag: function(filePath, tagName, projectId) {
            return call('jsAddTag', filePath, tagName, projectId);
        },
        removeTag: function(filePath, tagName, projectId) {
            return call('jsRemoveTag', filePath, tagName, projectId);
        },
        getTagsForFile: function(filePath, projectId) {
            return call('jsGetTagsForFile', filePath, projectId);
        },
        getAllTags: function(projectId) { return call('jsGetAllTags', projectId); },

        // Statistics
        getExportStatsByType: function(projectId, includeRollback) { return call('jsGetExportStatsByType', projectId, includeRollback); },
        getExportStatsByTag: function(projectId, includeRollback) { return call('jsGetExportStatsByTag', projectId, includeRollback); },
        getRecycleStatsByType: function(projectId, includeRollback) { return call('jsGetRecycleStatsByType', projectId, includeRollback); },
        getRecycleStatsByTag: function(projectId, includeRollback) { return call('jsGetRecycleStatsByTag', projectId, includeRollback); },
        getStatsSummary: function(projectId, includeRollback) { return call('jsGetStatsSummary', projectId, includeRollback); },

        // History
        getHistory: function(projectId) { return call('jsGetHistory', projectId); },
        getHiddenRecords: function(projectId) { return call('jsGetHiddenRecords', projectId); },
        getDeletedRecords: function(projectId) { return call('jsGetDeletedRecords', projectId); },
        setRecordHidden: function(recordId, hidden) { return call('jsSetRecordHidden', recordId, hidden); },
        setRecordExcludeFromStats: function(recordId, exclude) { return call('jsSetRecordExcludeFromStats', recordId, exclude); },
        setRecordDeleted: function(recordId, deleted) { return call('jsSetRecordDeleted', recordId, deleted); },
        rollbackRecord: function(recordId) { return call('jsRollbackRecord', recordId); },

        // Export / Import
        exportAllRecords: function() { return call('jsExportAllRecords'); },
        importRecords: function(json) { return call('jsImportRecords', json); },

        // File dialogs for export/import
        saveExportFile: function(defaultName, content) { return call('jsSaveExportFile', defaultName, content); },
        openImportFile: function() { return call('jsOpenImportFile'); },

        // Utility
        pickDirectory: function() { return call('jsPickDirectory'); },
        openFileExplorer: function(path) {
            try { window.javaObject.jsOpenFileExplorer(path); } catch(e) {}
        },
        showMessage: function(title, msg, type) {
            try { window.javaObject.jsShowMessage(title, msg, type); } catch(e) {}
        }
    };
})();
