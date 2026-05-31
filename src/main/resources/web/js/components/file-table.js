/**
 * file-table.js - File listing table with multi-select, sorting, and grouping.
 */
NRM.components = NRM.components || {};

NRM.components.fileTable = (function() {
    'use strict';

    var tableBody = null;
    var currentSort = { field: 'name', asc: true };
    var allFiles = []; // flattened list of {entry, sourcePath}

    function init() {
        tableBody = document.getElementById('file-table-body');
        if (!tableBody) return;

        // Select all checkbox
        var selectAll = document.getElementById('select-all');
        if (selectAll) {
            selectAll.addEventListener('change', function() {
                var rows = tableBody.querySelectorAll('tr.file-row');
                rows.forEach(function(row) {
                    var cb = row.querySelector('.row-checkbox');
                    var path = row.getAttribute('data-path');
                    if (cb) {
                        cb.checked = selectAll.checked;
                        if (selectAll.checked) {
                            NRM.state.selectedFiles.add(path);
                            row.classList.add('selected');
                        } else {
                            NRM.state.selectedFiles.delete(path);
                            row.classList.remove('selected');
                        }
                    }
                });
            });
        }

        // Sortable headers
        var headers = document.querySelectorAll('.sortable');
        headers.forEach(function(th) {
            th.addEventListener('click', function() {
                var field = this.getAttribute('data-sort');
                sortBy(field);
            });
        });
    }

    function render(fileGroups) {
        if (!tableBody) init();
        if (!tableBody) return;

        allFiles = [];
        tableBody.innerHTML = '';

        if (!fileGroups || fileGroups.length === 0) {
            tableBody.innerHTML = '<tr class="empty-row"><td colspan="6">没有文件</td></tr>';
            return;
        }

        // Fetch all tags for current project (for tag column)
        var projectId = NRM.state.currentProjectId;
        var allTags = projectId ? (NRM.bridge.getAllTags(projectId) || []) : [];
        // Build lookup: path -> [tagNames]
        var tagMap = {};
        allTags.forEach(function(t) {
            if (!tagMap[t.filePath]) tagMap[t.filePath] = [];
            tagMap[t.filePath].push(t.tagName);
        });

        var hasAnyFiles = false;
        fileGroups.forEach(function(group) {
            if (group.files && group.files.length > 0) {
                hasAnyFiles = true;
                // Group header
                var headerRow = document.createElement('tr');
                headerRow.className = 'group-header';
                headerRow.innerHTML = '<td colspan="6">📁 ' + escapeHtml(group.sourcePath) + '</td>';
                tableBody.appendChild(headerRow);

                group.files.forEach(function(file) {
                    allFiles.push({ entry: file, sourcePath: group.sourcePath });
                });
            }
        });

        if (!hasAnyFiles) {
            tableBody.innerHTML = '<tr class="empty-row"><td colspan="6">目录为空</td></tr>';
            return;
        }

        // Sort
        applySort();

        // Render rows
        allFiles.forEach(function(item, idx) {
            var file = item.entry;
            var row = document.createElement('tr');
            row.className = 'file-row';
            row.setAttribute('data-path', file.path);
            row.setAttribute('data-index', idx);
            row.setAttribute('data-source', item.sourcePath);

            if (NRM.state.selectedFiles.has(file.path)) {
                row.classList.add('selected');
            }

            // Checkbox
            var tdCheck = document.createElement('td');
            tdCheck.className = 'col-check';
            var cb = document.createElement('input');
            cb.type = 'checkbox';
            cb.className = 'row-checkbox';
            cb.checked = NRM.state.selectedFiles.has(file.path);
            cb.addEventListener('click', function(e) {
                e.stopPropagation();
                toggleSelection(file.path, row, cb.checked);
            });
            tdCheck.appendChild(cb);
            row.appendChild(tdCheck);

            // Name
            var tdName = document.createElement('td');
            tdName.className = 'col-name';
            var nameSpan = document.createElement('span');
            nameSpan.className = 'file-name';
            var icon = file.isDirectory ? '📁' : '📄';
            nameSpan.innerHTML = icon + ' ' + escapeHtml(file.name);
            tdName.appendChild(nameSpan);
            row.appendChild(tdName);

            // Type
            var tdType = document.createElement('td');
            tdType.className = 'col-type';
            tdType.textContent = file.type || '';
            row.appendChild(tdType);

            // Size
            var tdSize = document.createElement('td');
            tdSize.className = 'col-size';
            tdSize.textContent = file.isDirectory ? '' : (file.sizeFormatted || formatSize(file.size));
            row.appendChild(tdSize);

            // Modified
            var tdDate = document.createElement('td');
            tdDate.className = 'col-date';
            tdDate.textContent = file.modified || '';
            row.appendChild(tdDate);

            // Tags
            var tdTags = document.createElement('td');
            tdTags.className = 'col-tags';
            var fileTags = tagMap[file.path] || [];
            if (fileTags.length > 0) {
                tdTags.innerHTML = fileTags.map(function(t) {
                    return '<span class="tag-badge">' + escapeHtml(t) + '</span>';
                }).join('');
            }
            row.appendChild(tdTags);

            // Click handler
            row.addEventListener('click', function(e) {
                if (e.ctrlKey || e.metaKey) {
                    toggleSelection(file.path, row, !NRM.state.selectedFiles.has(file.path));
                } else if (e.shiftKey) {
                    // Shift-select range
                    var lastIdx = parseInt(NRM.state.lastClickedIndex || 0);
                    var start = Math.min(lastIdx, idx);
                    var end = Math.max(lastIdx, idx);
                    for (var i = start; i <= end; i++) {
                        var f = allFiles[i];
                        if (f) {
                            NRM.state.selectedFiles.add(f.entry.path);
                        }
                    }
                    NRM.state.lastClickedIndex = idx;
                    rerenderSelection();
                } else {
                    NRM.state.selectedFiles.clear();
                    toggleSelection(file.path, row, true);
                }
                NRM.state.lastClickedIndex = idx;
                updateSelectAllCheckbox();
            });

            row.addEventListener('dblclick', function() {
                if (file.isDirectory) {
                    NRM.state.currentDirectory = file.path;
                    NRM.state.selectedFiles.clear();
                    NRM.router.refresh();
                }
            });

            row.addEventListener('contextmenu', function(e) {
                e.preventDefault();
                if (!NRM.state.selectedFiles.has(file.path)) {
                    NRM.state.selectedFiles.clear();
                    NRM.state.selectedFiles.add(file.path);
                    rerenderSelection();
                }
                NRM.components.contextMenu.show(e.clientX, e.clientY);
            });

            tableBody.appendChild(row);
        });

        updateSelectAllCheckbox();
    }

    function toggleSelection(path, row, selected) {
        if (selected) {
            NRM.state.selectedFiles.add(path);
            row.classList.add('selected');
            row.querySelector('.row-checkbox').checked = true;
        } else {
            NRM.state.selectedFiles.delete(path);
            row.classList.remove('selected');
            row.querySelector('.row-checkbox').checked = false;
        }
    }

    function rerenderSelection() {
        var rows = tableBody.querySelectorAll('tr.file-row');
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

    function updateSelectAllCheckbox() {
        var selectAll = document.getElementById('select-all');
        if (!selectAll) return;
        var rows = tableBody.querySelectorAll('tr.file-row');
        if (rows.length === 0) {
            selectAll.checked = false;
            selectAll.indeterminate = false;
        } else if (NRM.state.selectedFiles.size === 0) {
            selectAll.checked = false;
            selectAll.indeterminate = false;
        } else if (NRM.state.selectedFiles.size < rows.length) {
            selectAll.checked = false;
            selectAll.indeterminate = true;
        } else {
            selectAll.checked = true;
            selectAll.indeterminate = false;
        }
    }

    function sortBy(field) {
        currentSort.asc = (currentSort.field === field) ? !currentSort.asc : true;
        currentSort.field = field;
        applySort();
        rerenderTable();
        updateSortArrows();
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
                    va = a.entry.size;
                    vb = b.entry.size;
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

    function rerenderTable() {
        if (!tableBody) return;
        var rows = tableBody.querySelectorAll('tr.file-row');
        var rowArr = Array.from(rows);
        rowArr.sort(function(ra, rb) {
            var ia = parseInt(ra.getAttribute('data-index'));
            var ib = parseInt(rb.getAttribute('data-index'));
            // Recalculate index in sorted allFiles
            var pa = ra.getAttribute('data-path');
            var pb = rb.getAttribute('data-path');
            var iaNew = allFiles.findIndex(function(f) { return f.entry.path === pa; });
            var ibNew = allFiles.findIndex(function(f) { return f.entry.path === pb; });
            return iaNew - ibNew;
        });
        rowArr.forEach(function(row) {
            tableBody.appendChild(row);
        });
        // Update data-index attributes
        allFiles.forEach(function(f, i) {
            var row = tableBody.querySelector('tr.file-row[data-path="' + f.entry.path.replace(/\\/g, '\\\\') + '"]');
            if (row) row.setAttribute('data-index', i);
        });
    }

    function updateSortArrows() {
        var headers = document.querySelectorAll('.sortable');
        headers.forEach(function(th) {
            var field = th.getAttribute('data-sort');
            var arrow = th.querySelector('.sort-arrow');
            if (!arrow) {
                arrow = document.createElement('span');
                arrow.className = 'sort-arrow';
                th.appendChild(arrow);
            }
            if (field === currentSort.field) {
                arrow.textContent = currentSort.asc ? '▲' : '▼';
            } else {
                arrow.textContent = '';
            }
        });
    }

    function formatSize(bytes) {
        if (!bytes || bytes === 0) return '0 B';
        if (bytes < 1024) return bytes + ' B';
        var exp = Math.floor(Math.log(bytes) / Math.log(1024));
        var prefix = 'KMGTPE'[exp - 1];
        return (bytes / Math.pow(1024, exp)).toFixed(1) + ' ' + prefix + 'B';
    }

    function escapeHtml(str) {
        var div = document.createElement('div');
        div.textContent = str;
        return div.innerHTML;
    }

    return { init: init, render: render, sortBy: sortBy };
})();
