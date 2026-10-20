(() => {
    const script = document.currentScript;
    const pending = new WeakSet();
    const ready = new WeakSet();
    let refresh;
    const token = () => {
        if (!refresh) {
            // A safe GET can restore remembered authentication before a state-changing POST.
            refresh = fetch('/api/csrf', {credentials: 'same-origin', cache: 'no-store', headers: {Accept: 'application/json'}})
                .then(async response => {
                    if (response.redirected || response.status === 401) throw new Error('login');
                    if (!response.ok || !response.headers.get('content-type')?.includes('application/json')) throw new Error('refresh');
                    const value = await response.json();
                    if (!script.dataset.user || value.username !== script.dataset.user) throw new Error('login');
                    if (value.parameterName !== '_csrf' || typeof value.token !== 'string' || !value.token) throw new Error('refresh');
                    return value;
                })
                .finally(() => { refresh = null; });
        }
        return refresh;
    };
    document.addEventListener('submit', async event => {
        const form = event.target;
        if (event.defaultPrevented || !(form instanceof HTMLFormElement) || form.method !== 'post'
            || !form.matches('.done-with-time, .track-time, [data-refresh-csrf]')
            || new URL(form.action).origin !== location.origin || ready.has(form)) return;
        event.preventDefault();
        if (pending.has(form)) return;
        pending.add(form);
        form.setAttribute('aria-busy', 'true');
        try {
            const value = await token();
            const fields = [...form.querySelectorAll('input[name="_csrf"]')];
            if (!fields.length) {
                const input = document.createElement('input');
                input.type = 'hidden';
                input.name = value.parameterName;
                form.append(input);
                fields.push(input);
            }
            fields.forEach(input => { input.value = value.token; });
            ready.add(form);
            form.requestSubmit(event.submitter || undefined);
        } catch (error) {
            window.alert(error.message === 'login' ? script.dataset.loginRequired : script.dataset.refreshFailed);
        } finally {
            ready.delete(form);
            pending.delete(form);
            form.removeAttribute('aria-busy');
            delete form.dataset.confirmed;
        }
    });
})();
