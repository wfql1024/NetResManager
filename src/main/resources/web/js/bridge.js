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
                console.error('Bridge error [' + methodName + ']:', err.code, err.message);
                NRM.ui.showError(err.message || err.code || 'Unknown error');
                return null;
            }
            return result.data;
        } catch (e) {
            console.error('Bridge call failed [' + methodName + ']:', e.message);
            NRM.ui.showError(e.message);
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
        getExportStatsByType: function(projectId) { return call('jsGetExportStatsByType', projectId); },
        getExportStatsByTag: function(projectId) { return call('jsGetExportStatsByTag', projectId); },
        getRecycleStatsByType: function(projectId) { return call('jsGetRecycleStatsByType', projectId); },
        getRecycleStatsByTag: function(projectId) { return call('jsGetRecycleStatsByTag', projectId); },
        getStatsSummary: function(projectId) { return call('jsGetStatsSummary', projectId); },

        // History
        getHistory: function(projectId) { return call('jsGetHistory', projectId); },
        setRecordHidden: function(recordId, hidden) { return call('jsSetRecordHidden', recordId, hidden); },
        setRecordExcludeFromStats: function(recordId, exclude) { return call('jsSetRecordExcludeFromStats', recordId, exclude); },
        setRecordDeleted: function(recordId, deleted) { return call('jsSetRecordDeleted', recordId, deleted); },
        rollbackRecord: function(recordId) { return call('jsRollbackRecord', recordId); },

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
