(() => {
    const picker=document.querySelector('.mc-picker');
    if(picker) {
        document.addEventListener('keydown',event=>{
            if(event.key==='Escape'&&picker.open) { picker.open=false; picker.querySelector('summary').focus(); }
        });
        document.addEventListener('click',event=>{ if(!picker.contains(event.target))picker.open=false; });
    }
    document.querySelectorAll('.calendar-legacy .day .items').forEach(container=>{
        const items=[...container.querySelectorAll(':scope > .item')];
        items.sort((left,right)=>{
            const exactLeft=left.dataset.exactTime==='true',exactRight=right.dataset.exactTime==='true';
            if(exactLeft!==exactRight)return exactLeft?-1:1;
            return exactLeft?left.dataset.time.localeCompare(right.dataset.time):0;
        });
        items.forEach(item=>container.appendChild(item));
    });
})();
