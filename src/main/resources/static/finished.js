(() => {
    const dialog = document.getElementById('finished-filter-dialog');
    document.querySelector('[data-finished-filter]').addEventListener('click', () => dialog.showModal());
    dialog.querySelector('[data-close-dialog]').addEventListener('click', () => dialog.close());
    dialog.addEventListener('click', event => {
        const rect = dialog.getBoundingClientRect();
        if (event.target === dialog && (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom)) dialog.close();
    });
    document.querySelector('[data-finished-size]').addEventListener('change', event => location.assign(event.target.value));
})();
