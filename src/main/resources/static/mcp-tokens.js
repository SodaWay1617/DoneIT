(() => {
    const script = document.currentScript;
    const form = document.getElementById('mcp-token-issue');
    const secretBox = document.getElementById('mcp-token-secret');
    const secretInput = document.getElementById('mcp-secret-value');
    const copyStatus = document.getElementById('mcp-copy-status');
    const formatDates = () => document.querySelectorAll('.mcp-token-page time[datetime]').forEach(element => {
        const date = new Date(element.dateTime);
        if (!Number.isNaN(date.valueOf())) element.textContent = date.toLocaleString(document.documentElement.lang);
    });
    const refreshList = async () => {
        try {
            const response = await fetch(location.href, {credentials: 'same-origin', cache: 'no-store'});
            if (!response.ok || response.redirected) throw new Error();
            const documentCopy = new DOMParser().parseFromString(await response.text(), 'text/html');
            const updated = documentCopy.getElementById('mcp-token-list-panel');
            if (!updated) throw new Error();
            document.getElementById('mcp-token-list-panel').replaceWith(updated);
            formatDates();
        } catch {
            document.getElementById('mcp-token-list-stale').hidden = false;
        }
    };
    let generation = 0;
    const hide = () => {
        generation++;
        if (secretInput) secretInput.value = '';
        if (secretBox) secretBox.hidden = true;
        if (copyStatus) copyStatus.textContent = '';
    };
    window.addEventListener('pagehide', hide);
    window.addEventListener('pageshow', hide);
    document.addEventListener('visibilitychange', () => { if (document.hidden) hide(); });
    document.getElementById('mcp-token-hide')?.addEventListener('click', hide);
    formatDates();
    document.getElementById('mcp-token-copy')?.addEventListener('click', async () => {
        const current = generation;
        try {
            await navigator.clipboard.writeText(secretInput.value);
            if (current === generation) copyStatus.textContent = script.dataset.copied;
        } catch {
            if (current === generation) {
                secretInput.select();
                copyStatus.textContent = script.dataset.copyFailed;
            }
        }
    });
    form?.addEventListener('submit', async event => {
        event.preventDefault();
        if (form.dataset.pending) return;
        hide();
        const current = generation;
        const button = form.querySelector('button[type="submit"]');
        const error = document.getElementById('mcp-token-error');
        error.hidden = true;
        form.dataset.pending = 'true';
        button.disabled = true;
        try {
            const response = await fetch(form.action, {
                method: 'POST', credentials: 'same-origin', cache: 'no-store',
                headers: {Accept: 'application/json'}, body: new FormData(form)
            });
            if (!response.ok || response.redirected || !response.headers.get('content-type')?.includes('application/json')) throw new Error();
            const issued = await response.json();
            document.getElementById('mcp-token-list-stale').hidden = false;
            if (current === generation && !document.hidden) {
                secretInput.value = issued.secret;
                secretBox.hidden = false;
                secretInput.focus();
            }
            issued.secret = '';
            await refreshList();
        } catch {
            error.hidden = false;
            // A failed response can still mean the server committed the issuance.
            document.getElementById('mcp-token-list-stale').hidden = false;
        } finally {
            delete form.dataset.pending;
            button.disabled = false;
        }
    });
})();
