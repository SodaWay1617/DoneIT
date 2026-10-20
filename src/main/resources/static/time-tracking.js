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
    form.requestSubmit();
}));

document.querySelectorAll('.done-with-time').forEach(form => form.addEventListener('submit', event => {
    if (form.dataset.confirmed) return;
    const value = window.prompt('Time spent, minutes (optional)');
    if (value === null) {
        event.preventDefault();
        return;
    }
    const minutes = value.trim() === '' ? '' : Number(value);
    if (minutes !== '' && (!Number.isInteger(minutes) || minutes <= 0)) {
        event.preventDefault();
        window.alert('Enter a positive whole number of minutes.');
        return;
    }
    form.querySelector('[name=spentMinutes]').value = minutes;
    form.dataset.confirmed = 'true';
}));

window.addEventListener('pageshow', () => {
    document.querySelectorAll('.done-with-time').forEach(form => { delete form.dataset.confirmed; });
});
