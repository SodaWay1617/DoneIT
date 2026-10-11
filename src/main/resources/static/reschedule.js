document.querySelectorAll('.reschedule-form').forEach(form => {
    const mode = form.querySelector('[name=plannedWithTime]');
    const day = form.querySelector('[name=plannedDate]');
    const time = form.querySelector('[name=plannedForAt]');
    function updateMode() {
        day.hidden = mode.checked;
        day.disabled = mode.checked;
        day.required = !mode.checked;
        time.hidden = !mode.checked;
        time.disabled = !mode.checked;
        time.required = mode.checked;
    }
    mode.addEventListener('change', () => {
        if (mode.checked && day.value && time.value) {
            time.value = day.value + 'T' + time.value.slice(11);
        } else if (!mode.checked && time.value) {
            day.value = time.value.slice(0, 10);
        }
        updateMode();
    });
    updateMode();
});
