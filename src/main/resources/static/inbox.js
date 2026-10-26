(() => {
    const root = document.querySelector('[data-inbox-list]');
    if (!root) return;
    const feedback = document.querySelector('[data-inbox-feedback]');
    const retry = document.querySelector('[data-inbox-retry]');
    let failed;
    const owners = new Map();
    root.querySelectorAll('.inbox-row').forEach(row => row.querySelectorAll('form').forEach(form => owners.set(form, row)));
    window.DoneItTaskMenu(root, '.inbox-row');
    retry.addEventListener('click', () => failed?.requestSubmit());
    // The earlier form-security listener refreshes CSRF and validates remembered identity first.
    document.addEventListener('submit', async event => {
        const form = event.target;
        if (event.defaultPrevented || !owners.has(form)) return;
        event.preventDefault();
        const row = owners.get(form);
        if (row.dataset.pending === 'true') return;
        row.dataset.pending = 'true';
        row.setAttribute('aria-busy', 'true');
        feedback.hidden = true;
        try {
            const response = await fetch(form.action, {method:'POST', body:new FormData(form), credentials:'same-origin'});
            const url = new URL(response.url);
            if (!response.ok || !response.redirected || url.origin !== location.origin || url.pathname !== '/inbox') throw new Error();
            location.assign(url.href);
        } catch (error) {
            failed = form;
            feedback.hidden = false;
        } finally {
            delete row.dataset.pending;
            row.removeAttribute('aria-busy');
        }
    });
})();
