document.querySelectorAll('.track-time-button').forEach(button => button.addEventListener('click', () => {
    const form = button.closest('form');
    const value = window.prompt('Time spent, minutes');
    if (value === null) return;
    const minutes = Number(value);
    if (!Number.isInteger(minutes) || minutes <= 0) {
        window.alert('Enter a positive whole number of minutes.');
        return;
    }
    form.querySelector('[name=minutes]').value = minutes;
    form.submit();
}));

document.querySelectorAll('.done-with-time').forEach(form => form.addEventListener('submit', event => {
    if (form.dataset.confirmed) return;
    event.preventDefault();
    const value = window.prompt('Time spent, minutes (optional)');
    if (value !== null && value.trim() !== '') {
        const minutes = Number(value);
        if (!Number.isInteger(minutes) || minutes <= 0) {
            window.alert('Enter a positive whole number of minutes.');
            return;
        }
        form.querySelector('[name=spentMinutes]').value = minutes;
    }
    form.dataset.confirmed = 'true';
    form.requestSubmit();
}));
