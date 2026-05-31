/**
 * nav-sidebar.js - Left navigation sidebar component.
 */
NRM.components = NRM.components || {};

NRM.components.navSidebar = (function() {
    'use strict';

    function init() {
        var items = document.querySelectorAll('#nav-sidebar .nav-item');
        items.forEach(function(item) {
            item.addEventListener('click', function() {
                var page = this.getAttribute('data-page');
                NRM.router.navigate(page);
            });
        });
    }

    function setActive(page) {
        var items = document.querySelectorAll('#nav-sidebar .nav-item');
        items.forEach(function(item) {
            item.classList.toggle('active', item.getAttribute('data-page') === page);
        });
    }

    return { init: init, setActive: setActive };
})();
