(() => {
    if (!document.querySelector('.app-navigation')) return;
    window.lucide?.createIcons();
    const menus = [...document.querySelectorAll('.app-menu')];
    const openers = new Map();
    const close = menu => { if (menu.open) menu.close(); };
    document.querySelectorAll('[data-open-menu]').forEach(button => {
        button.addEventListener('click', () => {
            const menu = document.getElementById(button.dataset.openMenu);
            if (menu.open) return;
            menus.forEach(close);
            openers.set(menu, button);
            menu.showModal();
            button.setAttribute('aria-expanded', 'true');
            document.body.classList.add('app-menu-open');
        });
    });
    menus.forEach(menu => {
        menu.querySelector('[data-close-menu]').addEventListener('click', () => close(menu));
        menu.addEventListener('keydown', event => {
            if (event.key !== 'Tab') return;
            const focusable = [...menu.querySelectorAll('a[href],button:not([disabled]),input:not([disabled]),select:not([disabled]),textarea:not([disabled]),[tabindex]')]
                .filter(element => element.tabIndex >= 0 && element.getClientRects().length);
            if (!focusable.length) return;
            event.preventDefault();
            const index = focusable.indexOf(document.activeElement);
            const next = index < 0 ? (event.shiftKey ? focusable.length - 1 : 0)
                : (index + (event.shiftKey ? -1 : 1) + focusable.length) % focusable.length;
            focusable[next].focus();
        });
        menu.addEventListener('click', event => {
            if (event.target === menu) {
                const box = menu.getBoundingClientRect();
                if (event.clientX < box.left || event.clientX > box.right || event.clientY < box.top || event.clientY > box.bottom) close(menu);
            }
            if (event.target.closest('a[href]')) close(menu);
        });
        menu.addEventListener('close', () => {
            const opener = openers.get(menu);
            opener?.setAttribute('aria-expanded', 'false');
            if (opener?.getClientRects().length) opener.focus({preventScroll: true});
            if (!menus.some(item => item.open)) document.body.classList.remove('app-menu-open');
        });
    });
    const desktop = matchMedia('(min-width:1024px)');
    const mobile = matchMedia('(max-width:767px)');
    const syncMenus = () => {
        if (desktop.matches) menus.forEach(close);
        else close(document.getElementById(mobile.matches ? 'app-drawer' : 'app-more'));
    };
    desktop.addEventListener('change', syncMenus);
    mobile.addEventListener('change', syncMenus);
    const viewport = window.visualViewport;
    let fullHeight = innerHeight;
    const keyboard = () => {
        const editing = document.activeElement?.matches('input:not([type=checkbox]):not([type=radio]):not([type=submit]),textarea,select,[contenteditable=true]');
        if (!editing) fullHeight = innerHeight;
        document.body.classList.toggle('app-keyboard-open', Boolean(editing && fullHeight - (viewport?.height || innerHeight) > 120));
    };
    viewport?.addEventListener('resize', keyboard);
    window.addEventListener('resize', keyboard);
    document.addEventListener('focusin', keyboard);
    document.addEventListener('focusout', () => setTimeout(keyboard, 100));
})();
