document.querySelectorAll('[data-page-size]').forEach(select => {
    select.addEventListener('change', event => location.assign(event.target.value));
});
