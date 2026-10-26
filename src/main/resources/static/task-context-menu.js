// Shared row-menu behavior: presentation only; actions remain ordinary links and forms.
window.DoneItTaskMenu = (root, rowSelector) => {
    let active, timer, origin, suppressed;
    const menus = new Map();
    const entries = menu => [...menu.querySelectorAll('button:not(:disabled),a[href]')];
    const cancelPress = () => { clearTimeout(timer); timer = null; origin = null; };
    const close = (restoreFocus = false) => {
        if (!active) return;
        const {row, menu, button} = active;
        menu.hidden = true;
        row.append(menu);
        button.setAttribute('aria-expanded', 'false');
        active = null;
        if (restoreFocus) button.focus({preventScroll:true});
    };
    const open = row => {
        if (row.dataset.pending === 'true') return;
        close();
        const menu = menus.get(row), button = row.querySelector('[data-row-menu]');
        document.body.append(menu);
        menu.hidden = false;
        button.setAttribute('aria-expanded', 'true');
        active = {row, menu, button};
        const viewport = window.visualViewport;
        const left = viewport?.offsetLeft || 0, top = viewport?.offsetTop || 0;
        const width = viewport?.width || innerWidth;
        let bottom = top + (viewport?.height || innerHeight);
        const nav = document.querySelector('.app-bottom-nav')?.getBoundingClientRect();
        if (nav?.height) bottom = Math.min(bottom, nav.top);
        menu.style.maxHeight = Math.max(44, bottom - top - 16) + 'px';
        const rect = row.getBoundingClientRect(), box = menu.getBoundingClientRect();
        const below = rect.bottom + 6;
        const y = below + box.height <= bottom - 8 ? below : rect.top - box.height - 6;
        menu.style.left = Math.max(left + 10, Math.min(rect.right - box.width, left + width - box.width - 10)) + 'px';
        menu.style.top = Math.max(top + 8, Math.min(y, bottom - box.height - 8)) + 'px';
        entries(menu)[0]?.focus({preventScroll:true});
    };
    root.querySelectorAll(rowSelector).forEach((row, index) => {
        const menu = row.querySelector('.compact-menu'), button = row.querySelector('[data-row-menu]');
        menus.set(row, menu);
        menu.id = 'task-row-menu-' + index;
        menu.setAttribute('role', 'menu');
        button.setAttribute('aria-controls', menu.id);
        entries(menu).forEach(item => item.setAttribute('role', 'menuitem'));
        button.addEventListener('click', () => active?.row === row ? close(true) : open(row));
        row.addEventListener('contextmenu', event => { event.preventDefault(); cancelPress(); open(row); });
        row.addEventListener('pointerdown', event => {
            if (event.pointerType === 'mouse' || event.target.closest('button,input,form')) return;
            cancelPress();
            origin = {x:event.clientX, y:event.clientY};
            timer = setTimeout(() => { suppressed = row; open(row); timer = null; }, 550);
        });
        row.addEventListener('pointermove', event => {
            if (origin && Math.hypot(event.clientX - origin.x, event.clientY - origin.y) > 10) cancelPress();
        });
        ['pointerup','pointercancel','pointerleave'].forEach(name => row.addEventListener(name, cancelPress));
        row.addEventListener('click', event => {
            if (suppressed === row && !event.target.closest('button,form')) {
                event.preventDefault(); event.stopPropagation(); suppressed = null;
            }
        }, true);
        row.addEventListener('keydown', event => {
            if (event.key === 'ContextMenu' || (event.shiftKey && event.key === 'F10')) {
                event.preventDefault(); open(row);
            }
        });
        menu.addEventListener('click', event => { if (event.target.closest('button:not(:disabled),a')) close(); });
        menu.addEventListener('keydown', event => {
            if (!['ArrowDown','ArrowUp','ArrowLeft','ArrowRight','Home','End'].includes(event.key)) return;
            event.preventDefault();
            const items = entries(menu), current = items.indexOf(document.activeElement);
            const next = event.key === 'Home' ? 0 : event.key === 'End' ? items.length - 1
                : (current + (['ArrowUp','ArrowLeft'].includes(event.key) ? -1 : 1) + items.length) % items.length;
            items[next]?.focus({preventScroll:true});
        });
    });
    document.addEventListener('pointerdown', event => {
        suppressed = null;
        if (active && !active.menu.contains(event.target) && !event.target.closest('[data-row-menu]')) close();
    });
    document.addEventListener('keydown', event => {
        if (event.key === 'Escape') close(true);
        if (event.key === 'Tab' && active) close(true);
    });
    window.addEventListener('scroll', event => {
        cancelPress();
        if (active && !active.menu.contains(event.target)) close();
    }, true);
    window.addEventListener('resize', () => { cancelPress(); close(); });
    return {close};
};
