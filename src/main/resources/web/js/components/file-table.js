/**
 * file-table.js - File listing with grouped-by-path design (matching history page).
 *
 * Layout per group:
 *   <div class="file-group">
 *     <div class="file-group-header">
 *       <span class="file-group-source">path</span>
 *       <span class="batch-actions">export/recycle group</span>
 *       <span class="file-group-count">N 项</span>
 *     </div>
 *     <table class="file-table">
 *       <thead>sortable columns</thead>
 *       <tbody>file rows with hover action buttons</tbody>
 *     </table>
 *   </div>
 *
 * Top bar: manage-top-actions shown when files are selected.
 */
NRM.components = NRM.components || {};

NRM.components.fileTable = (function() {
    'use strict';

    var container = null;
    var currentSort = { field: 'name', asc: true };
    var allFiles = []; // flattened list of {entry, sourcePath, sourcePathEscaped}
    var folderSizeCache = {}; // path -> {size, formatted} — survives re-renders

    function init() {
        container = document.getElementById('manage-files-container');
    }

    function render(fileGroups, skipFolderSizes) {
        if (!container) init();
        if (!container) return;

        // Cancel pending folder size calculations (only on full render)
        if (!skipFolderSizes) {
            NRM.cancelFolderSizes();
            // Clear cache in place (keep reference for onFolderSize callback)
            for (var k in folderSizeCache) {
                if (folderSizeCache.hasOwnProperty(k)) delete folderSizeCache[k];
            }
        }
        allFiles = [];
        container.innerHTML = '';

        if (!fileGroups || fileGroups.length === 0) {
            container.innerHTML = '<p class="placeholder-text">没有文件</p>';
            updateTopButtons();
            return;
        }

        // Fetch all tags for current project (for tag column)
        var projectId = NRM.state.currentProjectId;
        var allTags = projectId ? (NRM.bridge.getAllTags(projectId) || []) : [];
        var tagMap = {};
        allTags.forEach(function(t) {
            if (!tagMap[t.filePath]) tagMap[t.filePath] = [];
            tagMap[t.filePath].push(t.tagName);
        });

        // Flatten and check for files
        var hasAnyFiles = false;
        fileGroups.forEach(function(group) {
            if (group.files && group.files.length > 0) {
                hasAnyFiles = true;
                group.files.forEach(function(file) {
                    allFiles.push({ entry: file, sourcePath: group.sourcePath });
                });
            }
        });

        if (!hasAnyFiles) {
            container.innerHTML = '<p class="placeholder-text">目录为空</p>';
            updateTopButtons();
            return;
        }

        // Apply current sort to all files
        applySort();

        // Rebuild per-group lists in sorted order, preserving groups
        var sortedGroups = [];
        var groupMap = {};
        allFiles.forEach(function(item) {
            var sp = item.sourcePath;
            if (!groupMap[sp]) {
                groupMap[sp] = { sourcePath: sp, files: [] };
                sortedGroups.push(groupMap[sp]);
            }
            groupMap[sp].files.push(item);
        });

        // Render groups
        sortedGroups.forEach(function(group) {
            renderGroup(group, tagMap, projectId);
        });

        // Attach event listeners
        attachListeners();
        updateTopButtons();
        updateAllSortArrows();

        // Trigger async folder size calculation (skip on sort-only renders)
        if (!skipFolderSizes && NRM.requestFolderSizes) {
            NRM._folderSizeTimer = setTimeout(function() {
                NRM.requestFolderSizes();
            }, 100);
        }
    }

    function renderGroup(group, tagMap, projectId) {
        var html = '';
        var sourcePath = group.sourcePath;
        var files = group.files;

        // Group header — show breadcrumb when in subdirectory
        html += '<div class="file-group">';
        html += '<div class="file-group-header">';
        html += buildGroupHeader(sourcePath);
        // Batch actions (hidden, shown on header hover)
        html += '<span class="batch-actions">';
        html += '<button class="btn btn-sm batch-export-group" data-project="' + projectId + '">导出此组</button>';
        html += '<button class="btn btn-sm batch-recycle-group" data-project="' + projectId + '">回收此组</button>';
        html += '</span>';
        html += '<span class="file-group-count">' + files.length + ' 项</span>';
        html += '</div>';

        // Table with sortable column headers
        html += '<table class="file-table"><thead><tr>';
        html += '<th class="col-check"><input type="checkbox" class="group-checkbox"></th>';
        html += '<th class="col-name sortable" data-sort="name">文件名 <span class="sort-arrow"></span></th>';
        html += '<th class="col-type sortable" data-sort="type">类型 <span class="sort-arrow"></span></th>';
        html += '<th class="col-size sortable" data-sort="size">大小 <span class="sort-arrow"></span></th>';
        html += '<th class="col-date sortable" data-sort="modified">修改日期 <span class="sort-arrow"></span></th>';
        html += '</tr></thead><tbody>';

        files.forEach(function(item) {
            var file = item.entry;
            var rowClass = 'file-row';
            if (NRM.state.selectedFiles.has(file.path)) {
                rowClass += ' selected';
            }

            // Tags inline after filename
            var tagsHtml = '';
            var fileTags = tagMap[file.path] || [];
            if (fileTags.length > 0) {
                tagsHtml = '<span class="file-inline-tags">' +
                    fileTags.map(function(t) {
                        return '<span class="tag-badge">' + escapeHtml(t) + '</span>';
                    }).join('') + '</span>';
            }

            var effectiveSize = (file.isDirectory && folderSizeCache[file.path])
                ? folderSizeCache[file.path].size : file.size;
            html += '<tr class="' + rowClass + '" data-path="'
                + escapeAttr(file.path) + '" data-size="' + effectiveSize + '"'
                + (file.isDirectory ? ' data-isdir="true"' : '') + '>';

            // Checkbox
            html += '<td class="col-check">';
            html += '<input type="checkbox" class="row-checkbox" '
                + (NRM.state.selectedFiles.has(file.path) ? 'checked' : '') + '>';
            html += '</td>';

            // Filename + tags + hover actions
            html += '<td class="col-name">';
            html += '<div class="file-name-cell">';
            var icon = file.isDirectory ? '📁' : '📄';
            html += '<span class="file-name-text">' + icon + ' ' + escapeHtml(file.name) + '</span>';
            html += tagsHtml;
            // Row hover actions
            html += '<span class="file-row-actions">';
            html += '<button class="btn btn-sm file-btn-export" title="导出">导出</button>';
            html += '<button class="btn btn-sm file-btn-recycle" title="回收">回收</button>';
            html += '<button class="btn btn-sm file-btn-tag" title="标签">标签</button>';
            html += '<button class="btn btn-sm file-btn-open" title="打开位置">打开</button>';
            html += '<button class="btn btn-sm file-btn-copy" title="复制路径">复制</button>';
            html += '</span>';
            html += '</div>';
            html += '</td>';

            // Type
            html += '<td class="col-type">' + escapeHtml(file.type || '') + '</td>';

            // Size — use cached value for folders if available
            var sizeDisplay;
            if (file.isDirectory) {
                var cached = folderSizeCache[file.path];
                sizeDisplay = cached ? cached.formatted : '计算中...';
            } else {
                sizeDisplay = file.sizeFormatted || formatSize(file.size);
            }
            html += '<td class="col-size">' + sizeDisplay + '</td>';

            // Modified
            html += '<td class="col-date">' + escapeHtml(file.modified || '') + '</td>';

            html += '</tr>';
        });

        html += '</tbody></table></div>';
        container.insertAdjacentHTML('beforeend', html);
    }

    // ==================== Event Listeners ====================

    function attachListeners() {
        // Sortable headers
        var headers = container.querySelectorAll('.sortable');
        headers.forEach(function(th) {
            th.addEventListener('click', function() {
                var field = this.getAttribute('data-sort');
                sortBy(field);
            });
        });

        // Breadcrumb segment clicks
        var crumbs = container.querySelectorAll('.br-segment');
        crumbs.forEach(function(seg) {
            seg.addEventListener('click', function(e) {
                e.stopPropagation();
                var navPath = this.getAttribute('data-nav');
                // navPath may be "" (empty) to go back to root
                NRM.state.currentDirectory = navPath || '';
                NRM.state.selectedFiles.clear();
                NRM.router.refresh();
            });
        });

        // Row click: toggle selection
        var rows = container.querySelectorAll('tr.file-row');
        rows.forEach(function(row) {
            var path = row.getAttribute('data-path');

            row.addEventListener('click', function(e) {
                var tag = e.target.tagName.toLowerCase();
                // Don't toggle when clicking buttons or checkboxes
                if (tag === 'button' || tag === 'input') return;
                // Check if click is inside action buttons
                var el = e.target;
                while (el && el !== row) {
                    if (el.classList.contains('file-row-actions') ||
                        el.classList.contains('batch-actions')) return;
                    el = el.parentElement;
                }
                toggleSelection(path);
            });

            row.addEventListener('contextmenu', function(e) {
                e.preventDefault();
                if (!NRM.state.selectedFiles.has(path)) {
                    NRM.state.selectedFiles.clear();
                    addToSelection(path);
                    rerenderSelections();
                }
                NRM.components.contextMenu.show(e.clientX, e.clientY, 'manage');
            });

            // Double-click: enter directory
            row.addEventListener('dblclick', function(e) {
                var isDir = row.getAttribute('data-isdir');
                if (isDir === 'true') {
                    NRM.state.currentDirectory = path;
                    NRM.state.selectedFiles.clear();
                    NRM.router.refresh();
                }
            });
        });

        // Row checkboxes
        var rowCbs = container.querySelectorAll('.row-checkbox');
        rowCbs.forEach(function(cb) {
            cb.addEventListener('click', function(e) {
                e.stopPropagation();
                var row = this.closest('tr.file-row');
                var path = row ? row.getAttribute('data-path') : null;
                if (path) toggleSelection(path);
            });
        });

        // Group checkboxes
        var groupCbs = container.querySelectorAll('.group-checkbox');
        groupCbs.forEach(function(gcb) {
            gcb.addEventListener('click', function(e) {
                e.stopPropagation();
                var table = this.closest('table');
                if (!table) return;
                var checked = this.checked;
                var rows = table.querySelectorAll('tr.file-row');
                rows.forEach(function(row) {
                    var path = row.getAttribute('data-path');
                    if (path) {
                        if (checked) {
                            NRM.state.selectedFiles.add(path);
                        } else {
                            NRM.state.selectedFiles.delete(path);
                        }
                    }
                });
                rerenderSelections();
                updateGroupCheckboxes();
                updateTopButtons();
            });
        });

        // Row hover action buttons
        attachRowActionListeners();

        // Group batch buttons (via event delegation)
        attachGroupBatchListeners();
    }

    function attachGroupBatchListeners() {
        // Export group
        container.querySelectorAll('.batch-export-group').forEach(function(btn) {
            btn.addEventListener('click', function(e) {
                e.stopPropagation();
                var projectId = parseInt(this.getAttribute('data-project'));
                var groupDiv = this.closest('.file-group');
                var paths = collectGroupPaths(groupDiv);
                if (paths.length > 0) {
                    NRM.components.modal.confirm('确认导出',
                        '确定要导出此组的 ' + paths.length + ' 个文件吗？',
                        function() { NRM.pages.manage.doExport(paths, projectId); });
                }
            });
        });

        // Recycle group
        container.querySelectorAll('.batch-recycle-group').forEach(function(btn) {
            btn.addEventListener('click', function(e) {
                e.stopPropagation();
                var projectId = parseInt(this.getAttribute('data-project'));
                var groupDiv = this.closest('.file-group');
                var paths = collectGroupPaths(groupDiv);
                if (paths.length > 0) {
                    NRM.components.modal.confirm('确认回收',
                        '确定要回收此组的 ' + paths.length + ' 个文件吗？',
                        function() { NRM.pages.manage.doRecycle(paths, projectId); });
                }
            });
        });
    }

    function collectGroupPaths(groupDiv) {
        var paths = [];
        if (groupDiv) {
            var rows = groupDiv.querySelectorAll('tr.file-row');
            rows.forEach(function(row) {
                var p = row.getAttribute('data-path');
                if (p) paths.push(p);
            });
        }
        return paths;
    }

    function attachRowActionListeners() {
        // Export button
        container.querySelectorAll('.file-btn-export').forEach(function(btn) {
            btn.addEventListener('click', function(e) {
                e.stopPropagation();
                var row = this.closest('tr.file-row');
                var path = row ? row.getAttribute('data-path') : null;
                if (path) {
                    var projectId = NRM.state.currentProjectId;
                    if (!projectId) return;
                    NRM.components.modal.confirm('确认导出',
                        '确定要导出此文件吗？',
                        function() {
                            NRM.pages.manage.doExport([path], projectId);
                        });
                }
            });
        });

        // Recycle button
        container.querySelectorAll('.file-btn-recycle').forEach(function(btn) {
            btn.addEventListener('click', function(e) {
                e.stopPropagation();
                var row = this.closest('tr.file-row');
                var path = row ? row.getAttribute('data-path') : null;
                if (path) {
                    var projectId = NRM.state.currentProjectId;
                    if (!projectId) return;
                    NRM.components.modal.confirm('确认回收',
                        '确定要回收此文件吗？',
                        function() {
                            NRM.pages.manage.doRecycle([path], projectId);
                        });
                }
            });
        });

        // Tag button
        container.querySelectorAll('.file-btn-tag').forEach(function(btn) {
            btn.addEventListener('click', function(e) {
                e.stopPropagation();
                var row = this.closest('tr.file-row');
                var path = row ? row.getAttribute('data-path') : null;
                if (path) {
                    NRM.components.contextMenu.showTagDialog([path], NRM.state.currentProjectId);
                }
            });
        });

        // Open location button
        container.querySelectorAll('.file-btn-open').forEach(function(btn) {
            btn.addEventListener('click', function(e) {
                e.stopPropagation();
                var row = this.closest('tr.file-row');
                var path = row ? row.getAttribute('data-path') : null;
                if (path) NRM.bridge.openFileExplorer(path);
            });
        });

        // Copy path button
        container.querySelectorAll('.file-btn-copy').forEach(function(btn) {
            btn.addEventListener('click', function(e) {
                e.stopPropagation();
                var row = this.closest('tr.file-row');
                var path = row ? row.getAttribute('data-path') : null;
                if (path) {
                    navigator.clipboard.writeText(path).then(function() {
                        NRM.ui.showToast('路径已复制');
                    });
                }
            });
        });
    }

    // ==================== Selection ====================

    function toggleSelection(path) {
        if (NRM.state.selectedFiles.has(path)) {
            NRM.state.selectedFiles.delete(path);
        } else {
            NRM.state.selectedFiles.add(path);
        }
        rerenderSelections();
        updateGroupCheckboxes();
        updateTopButtons();
    }

    function addToSelection(path) {
        NRM.state.selectedFiles.add(path);
    }

    function rerenderSelections() {
        var rows = container.querySelectorAll('tr.file-row');
        rows.forEach(function(row) {
            var path = row.getAttribute('data-path');
            var cb = row.querySelector('.row-checkbox');
            if (NRM.state.selectedFiles.has(path)) {
                row.classList.add('selected');
                if (cb) cb.checked = true;
            } else {
                row.classList.remove('selected');
                if (cb) cb.checked = false;
            }
        });
    }

    function updateGroupCheckboxes() {
        var groupCbs = container.querySelectorAll('.group-checkbox');
        groupCbs.forEach(function(gcb) {
            var table = gcb.closest('table');
            if (!table) return;
            var rows = table.querySelectorAll('tr.file-row');
            var allChecked = true;
            var noneChecked = true;
            rows.forEach(function(row) {
                var path = row.getAttribute('data-path');
                if (NRM.state.selectedFiles.has(path)) {
                    noneChecked = false;
                } else {
                    allChecked = false;
                }
            });
            gcb.checked = allChecked;
            gcb.indeterminate = !allChecked && !noneChecked;
        });
    }

    // ==================== Top Bar ====================

    function updateTopButtons() {
        var ids = getSelectedPaths();
        var count = ids.length;
        var label = document.getElementById('manage-top-label');
        var actions = document.getElementById('manage-top-actions');
        var btnExport = document.getElementById('btn-manage-export');
        var btnRecycle = document.getElementById('btn-manage-recycle');
        var btnTag = document.getElementById('btn-manage-tag');

        if (count === 0) {
            if (label) label.textContent = '';
            if (actions) actions.style.display = 'none';
            return;
        }

        if (actions) actions.style.display = '';
        if (label) label.textContent = '已选 ' + count + ' 项';
        if (btnExport) btnExport.style.display = '';
        if (btnRecycle) btnRecycle.style.display = '';
        if (btnTag) btnTag.style.display = '';
    }

    function getSelectedPaths() {
        var paths = [];
        NRM.state.selectedFiles.forEach(function(p) { paths.push(p); });
        return paths;
    }

    // ==================== Sorting ====================

    function sortBy(field) {
        currentSort.asc = (currentSort.field === field) ? !currentSort.asc : true;
        currentSort.field = field;
        // Re-render to apply new sort (skip folder size recalculation)
        var fileGroups = NRM.state.files;
        if (fileGroups) {
            render(fileGroups, true);
        }
    }

    /** Returns effective size: cached value for folders, entry.size for files. */
    function getEffectiveSize(item) {
        if (item.entry.isDirectory) {
            var cached = folderSizeCache[item.entry.path];
            if (cached && cached.size > 0) return cached.size;
        }
        return item.entry.size;
    }

    function applySort() {
        var field = currentSort.field;
        var asc = currentSort.asc;
        allFiles.sort(function(a, b) {
            var va, vb;
            switch (field) {
                case 'name':
                    va = a.entry.name.toLowerCase();
                    vb = b.entry.name.toLowerCase();
                    // Directories first
                    if (a.entry.isDirectory && !b.entry.isDirectory) return -1;
                    if (!a.entry.isDirectory && b.entry.isDirectory) return 1;
                    break;
                case 'type':
                    va = (a.entry.type || '').toLowerCase();
                    vb = (b.entry.type || '').toLowerCase();
                    break;
                case 'size':
                    va = getEffectiveSize(a);
                    vb = getEffectiveSize(b);
                    break;
                case 'modified':
                    va = a.entry.modified || '';
                    vb = b.entry.modified || '';
                    break;
                default:
                    return 0;
            }
            if (va < vb) return asc ? -1 : 1;
            if (va > vb) return asc ? 1 : -1;
            return 0;
        });
    }

    function updateAllSortArrows() {
        var headers = container.querySelectorAll('.sortable');
        headers.forEach(function(th) {
            var field = th.getAttribute('data-sort');
            var arrow = th.querySelector('.sort-arrow');
            if (arrow) {
                if (field === currentSort.field) {
                    arrow.textContent = currentSort.asc ? '▲' : '▼';
                } else {
                    arrow.textContent = '';
                }
            }
        });
    }

    // ==================== Group Header Breadcrumb ====================

    /**
     * Builds the group header content.
     * When browsing a subdirectory, renders a clickable breadcrumb.
     * Otherwise shows the source path as a plain label.
     */
    function buildGroupHeader(sourcePath) {
        var currentDir = NRM.state.currentDirectory || '';

        // Normalize paths for comparison (ensure trailing separator consistency)
        var sp = sourcePath.replace(/\\/g, '/').replace(/\/+$/, '');
        var cd = currentDir.replace(/\\/g, '/').replace(/\/+$/, '');

        if (cd.length > sp.length && cd.indexOf(sp) === 0 && cd.charAt(sp.length) === '/') {
            // currentDir is deeper than sourcePath → render breadcrumb
            var relative = cd.substring(sp.length + 1); // e.g., "A/B"
            var segments = relative.split('/');

            var html = '<span class="file-group-breadcrumb">';
            // Base path (clickable — navigates back to multi-path root)
            html += '<span class="br-segment" data-nav="" title="回到首页">📁 '
                + escapeHtml(sourcePath) + '</span>';

            // Build clickable segments
            var partialPath = sp; // normalized base path for URL construction
            for (var i = 0; i < segments.length; i++) {
                html += '<span class="br-sep">&gt;</span>';
                partialPath += '/' + segments[i];
                // Convert back to native separators for the click action
                var nativePath = partialPath.replace(/\//g, '\\');
                if (i === segments.length - 1) {
                    // Last segment = current, not clickable
                    html += '<span class="br-current">' + escapeHtml(segments[i]) + '</span>';
                } else {
                    html += '<span class="br-segment" data-nav="'
                        + escapeAttr(nativePath) + '">' + escapeHtml(segments[i]) + '</span>';
                }
            }

            html += '</span>';
            return html;
        } else {
            // Root level — plain path label
            return '<span class="file-group-source" title="' + escapeAttr(sourcePath) + '">📁 '
                + escapeHtml(sourcePath) + '</span>';
        }
    }

    // ==================== Helpers ====================

    function formatSize(bytes) {
        if (!bytes || bytes === 0) return '0 B';
        if (bytes < 1024) return bytes + ' B';
        var exp = Math.floor(Math.log(bytes) / Math.log(1024));
        var prefix = 'KMGTPE'[exp - 1];
        return (bytes / Math.pow(1024, exp)).toFixed(1) + ' ' + prefix + 'B';
    }

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
        render: render,
        sortBy: sortBy,
        updateTopButtons: updateTopButtons,
        _folderSizeCache: folderSizeCache
    };
})();
