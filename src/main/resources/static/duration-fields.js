(() => {
    const estimate = document.querySelector('[data-estimate-value]');
    if (!estimate) return;
    const picker = document.querySelector('[data-estimate-picker]');
    const hours = picker.querySelector('[data-hours]');
    const minutes = picker.querySelector('[data-minutes]');
    const mode = document.querySelector('[data-end-mode]');
    const end = document.querySelector('[data-end-value]');
    const control = document.querySelector('[data-end-control]');
    const start = document.querySelector('[name=plannedForAt], [name=occurrenceTime]');
    const status = document.querySelector('[name=status]');
    function showEstimate() {
        const value = Number(estimate.value);
        const h = estimate.value ? Math.floor(value / 60) : '';
        if (h !== '' && ![...hours.options].some(o => o.value === String(h)))
            hours.add(new Option(String(h), String(h)));
        hours.value = String(h);
        minutes.value = estimate.value ? String(value % 60) : '';
    }
    function timeMinutes(value) {
        const part = value.includes('T') ? value.split('T')[1] : value;
        const pieces = part.split(':').map(Number);
        return pieces.length >= 2 ? pieces[0] * 60 + pieces[1] : NaN;
    }
    function sync() {
        const dayOnly = document.querySelector('[name=plannedWithTime]');
        const undated = document.querySelector('[name=withoutPlannedDate]');
        const available = !!start.value && !start.disabled && (!dayOnly || dayOnly.checked)
            && (!undated || !undated.checked) && (!dayOnly || !['NEW','BACKLOG','PAUSED'].includes(status.value));
        control.hidden = !available;
        mode.disabled = !available;
        const explicit = available && mode.checked;
        end.disabled = !explicit;
        end.required = explicit;
        document.querySelector('[data-end-field]').hidden = !explicit;
        picker.hidden = explicit;
        estimate.hidden = true;
        const output = document.querySelector('[data-duration-preview]');
        output.hidden = !explicit;
        if (explicit && end.value) {
            let duration = timeMinutes(end.value) - timeMinutes(start.value);
            if (duration < 0) duration += 1440;
            if (duration > 0) {
                estimate.value = String(duration);
                showEstimate();
                output.textContent = Math.floor(duration / 60) + ' ' + output.dataset.hoursUnit + ' ' + duration % 60 + ' ' + output.dataset.minutesUnit;
            } else output.textContent = '';
        } else output.textContent = '';
    }
    [hours, minutes].forEach(select => select.addEventListener('change', () => {
        const value = Number(hours.value || 0) * 60 + Number(minutes.value || 0);
        estimate.value = value > 0 ? String(value) : '';
    }));
    document.querySelector('form').addEventListener('change', sync);
    start.addEventListener('input', sync);
    end.addEventListener('input', sync);
    mode.addEventListener('change', sync);
    showEstimate();
    sync();
})();
