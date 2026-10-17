(() => {
    if (document.getElementById('doneit-reminders')) return;
    fetch('/api/reminders', {credentials: 'same-origin', cache: 'no-store'})
        .then(response => response.ok && response.headers.get('content-type')?.includes('application/json') ? response.json() : null)
        .then(data => {
            if (!data?.items?.length) return;
            const key = `doneit-reminders:${data.userId}:${data.day}`;
            try { if (sessionStorage.getItem(key)) return; } catch (_) { /* Storage may be unavailable. */ }
            const style = document.createElement('link');
            style.rel = 'stylesheet'; style.href = '/reminders.css'; document.head.append(style);
            const panel = document.createElement('aside');
            panel.id = 'doneit-reminders'; panel.setAttribute('aria-live', 'polite');
            const header = document.createElement('header');
            const heading = document.createElement('h2'); heading.textContent = data.heading;
            const close = document.createElement('button');
            close.type = 'button'; close.textContent = data.dismissLabel;
            close.addEventListener('click', () => panel.remove());
            header.append(heading, close); panel.append(header);
            const list = document.createElement('ul');
            data.items.forEach(item => {
                const row = document.createElement('li');
                const link = document.createElement('a'); link.textContent = item.title; link.href = item.href;
                const detail = document.createElement('span'); detail.textContent = item.message;
                row.append(link, detail); list.append(row);
            });
            panel.append(list);
            if (data.more) { const more = document.createElement('p'); more.textContent = data.more; panel.append(more); }
            style.addEventListener('load', () => {
                document.body.append(panel);
                try { sessionStorage.setItem(key, 'shown'); } catch (_) { /* Keep reminders usable without storage. */ }
            });
        }).catch(() => { /* A reminder failure must not interrupt task work. */ });
})();
