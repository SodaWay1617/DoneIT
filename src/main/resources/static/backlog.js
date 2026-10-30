(() => {
    const filter = document.getElementById('backlog-filter-dialog');
    const dialog = document.getElementById('backlog-move-dialog');
    const form = dialog.querySelector('form');
    const day = form.elements.plannedDate;
    const withTime = form.querySelector('[data-move-with-time]');
    const time = form.querySelector('[data-move-time]');
    const update = () => {
        form.querySelector('[data-move-time-label]').hidden = !withTime.checked;
        time.required = withTime.checked;
        form.elements.plannedForAt.disabled = !withTime.checked;
        form.elements.plannedForAt.value = withTime.checked && day.value && time.value ? day.value + 'T' + time.value : '';
    };
    document.querySelector('[data-backlog-filter]').addEventListener('click', () => filter.showModal());
    document.querySelectorAll('[data-backlog-schedule]').forEach(button => button.addEventListener('click', () => {
        form.reset(); form.action = button.dataset.action; form.dataset.owner = button.dataset.owner;
        form.querySelector('[data-move-task]').textContent = button.dataset.title;
        day.value = form.dataset.today; update(); dialog.showModal(); day.focus();
    }));
    [day, withTime, time].forEach(input => input.addEventListener('change', update));
    form.addEventListener('submit', update);
    [filter, dialog].forEach(modal => {
        modal.querySelectorAll('[data-close-dialog]').forEach(button => button.addEventListener('click', () => modal.close()));
        modal.addEventListener('click', event => {
            const rect = modal.getBoundingClientRect();
            if (event.target === modal && (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom)) modal.close();
        });
    });
})();
