(() => {
    const filters = new URLSearchParams(location.search);
    const keys = ['projectId', 'planningType'];
    const views = new Set(['/', '/tasks', '/kanban', '/calendar', '/backlog', '/inbox', '/finished']);
    function preserve(target) {
        const url = new URL(target, location.origin);
        keys.forEach(key => {
            if (filters.get(key)) url.searchParams.set(key, filters.get(key));
        });
        return url.pathname + url.search + url.hash;
    }
    document.querySelectorAll('a[href]').forEach(link => {
        const url = new URL(link.href);
        if (url.origin === location.origin && views.has(url.pathname)) {
            link.setAttribute('href', preserve(link.getAttribute('href')));
        }
    });
    document.querySelectorAll('input[name=redirectTo]').forEach(input => {
        input.value = preserve(input.value);
    });
    document.querySelectorAll('form[method=get]:not(.view-filters)').forEach(form => {
        keys.forEach(key => {
            if (!filters.get(key) || form.querySelector('[name=' + key + ']')) return;
            const input = document.createElement('input');
            input.type = 'hidden';
            input.name = key;
            input.value = filters.get(key);
            form.appendChild(input);
        });
    });
})();
