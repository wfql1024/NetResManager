/**
 * modal-dialog.js - Generic modal dialog component.
 */
NRM.components = NRM.components || {};

NRM.components.modal = (function() {
    'use strict';

    var overlay = null;
    var currentCallback = null;

    function init() {
        overlay = document.getElementById('modal-overlay');
    }

    /**
     * Shows a modal dialog.
     * @param {string} title - Dialog title
     * @param {string|HTMLElement} body - HTML content or element
     * @param {Array} buttons - Array of {text, cls, callback} or null for default OK
     */
    function show(title, body, buttons) {
        if (!overlay) init();
        var titleEl = overlay.querySelector('.modal-title');
        var bodyEl = overlay.querySelector('.modal-body');
        var footerEl = overlay.querySelector('.modal-footer');

        titleEl.textContent = title;
        if (typeof body === 'string') {
            bodyEl.innerHTML = body;
        } else if (body instanceof HTMLElement) {
            bodyEl.innerHTML = '';
            bodyEl.appendChild(body);
        }

        footerEl.innerHTML = '';
        if (!buttons || buttons.length === 0) {
            buttons = [{ text: '确定', cls: 'btn-primary', value: true }];
        }
        buttons.forEach(function(btn) {
            var btnEl = document.createElement('button');
            btnEl.className = 'btn ' + (btn.cls || '');
            btnEl.textContent = btn.text;
            btnEl.onclick = function() {
                close();
                if (btn.callback) btn.callback(btn.value);
            };
            footerEl.appendChild(btnEl);
        });

        overlay.style.display = 'flex';
    }

    function close() {
        if (overlay) overlay.style.display = 'none';
    }

    /**
     * Show a simple confirmation dialog.
     */
    function confirm(title, message, onOk, onCancel) {
        show(title, '<p>' + message + '</p>', [
            { text: '取消', cls: '', callback: function() { if (onCancel) onCancel(); } },
            { text: '确定', cls: 'btn-primary', callback: function() { if (onOk) onOk(); } }
        ]);
    }

    /**
     * Show a simple alert dialog.
     */
    function alert(title, message) {
        show(title, '<p>' + message + '</p>', [
            { text: '确定', cls: 'btn-primary' }
        ]);
    }

    // Close on overlay click
    document.addEventListener('click', function(e) {
        if (overlay && e.target === overlay) {
            close();
        }
    });

    return {
        show: show,
        close: close,
        confirm: confirm,
        alert: alert
    };
})();
