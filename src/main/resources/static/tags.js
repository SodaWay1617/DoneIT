(() => {
    document.querySelectorAll('[data-tag-editor]').forEach(editor => {
        const input = editor.querySelector('[data-tag-input]');
        const selected = editor.querySelector('.tag-selected');
        const error = editor.querySelector('[data-tag-error]');
        const normalize = text => text.normalize('NFKC').trim().replace(/^#/, '').trim().replace(/\s+/gu, ' ');
        const add = () => {
            const name = normalize(input.value);
            if (!name) { input.value = ''; return true; }
            const boxes = [...selected.querySelectorAll('input[name=tagNames]')];
            const existing = boxes.find(box => normalize(box.value).toLowerCase() === name.toLowerCase());
            const count = boxes.filter(box => box.checked).length;
            if (!/^[\p{L}\p{N}_-][\p{L}\p{N}\p{M} _-]{0,39}$/u.test(name) || (count >= 20 && !existing?.checked)) {
                error.hidden = false;
                input.setAttribute('aria-invalid', 'true');
                return false;
            }
            if (existing) existing.checked = true;
            else {
                const label = document.createElement('label');
                label.className = 'tag-option';
                const checkbox = document.createElement('input');
                checkbox.type = 'checkbox'; checkbox.name = 'tagNames'; checkbox.value = name; checkbox.checked = true;
                const text = document.createElement('span'); text.textContent = '#' + name;
                label.append(checkbox, text); selected.append(label);
            }
            input.value = ''; input.removeAttribute('aria-invalid'); error.hidden = true;
            return true;
        };
        editor.querySelector('[data-add-tag]').addEventListener('click', () => { add(); input.focus(); });
        input.addEventListener('keydown', event => {
            if (event.key === 'Enter') { event.preventDefault(); add(); }
        });
        editor.closest('form').addEventListener('submit', event => {
            if (!add()) { event.preventDefault(); input.focus(); }
        });
    });
    document.querySelectorAll('[data-delete-tag]').forEach(form => {
        form.addEventListener('submit', event => {
            if (form.dataset.confirmed) return;
            if (!window.confirm(form.dataset.confirm)) { event.preventDefault(); return; }
            form.dataset.confirmed = 'true';
        });
    });
})();
