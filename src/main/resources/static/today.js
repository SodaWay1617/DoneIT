(() => {
    const picker = document.querySelector('.today-date-picker');
    if (!picker) return;
    document.addEventListener('keydown', event => {
        if (event.key === 'Escape' && picker.open) {
            picker.open = false;
            picker.querySelector('summary').focus();
        }
    });
    document.addEventListener('click', event => {
        if (picker.open && !picker.contains(event.target)) picker.open = false;
    });
})();
