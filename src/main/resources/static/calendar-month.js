(() => {
    const picker=document.querySelector('.mc-picker');
    if(picker) {
        document.addEventListener('keydown',event=>{
            if(event.key==='Escape'&&picker.open) { picker.open=false; picker.querySelector('summary').focus(); }
        });
        document.addEventListener('click',event=>{ if(!picker.contains(event.target))picker.open=false; });
    }
})();
