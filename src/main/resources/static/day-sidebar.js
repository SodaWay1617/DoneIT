(() => {
    const choice=document.querySelector('.day-create-choice');
    if(choice) {
        document.addEventListener('click',event=>{if(choice.open&&!choice.contains(event.target))choice.open=false;});
        document.addEventListener('keydown',event=>{if(event.key==='Escape'&&choice.open){choice.open=false;choice.querySelector('summary').focus();}});
    }
    document.querySelectorAll('[data-bulk-confirm]').forEach(form=>{
        const button=form.querySelector('button[type=submit]');
        button.disabled=false;
        button.addEventListener('click',event=>{
            if(form.dataset.previewPending||form.getAttribute('aria-busy')==='true') event.preventDefault();
        });
        form.addEventListener('submit',async event=>{
            if(form.dataset.confirmed) return;
            event.preventDefault();
            if(form.dataset.previewPending) return;
            form.dataset.previewPending='true';
            const text=(template,args)=>template.replace(/\{(\d+)\}/g,(_,index)=>String(args[Number(index)]??''));
            const format=value=>{
                const [y,m,d]=value.split('-').map(Number);
                return new Intl.DateTimeFormat(document.documentElement.lang||'en').format(new Date(y,m-1,d,12));
            };
            try {
                const response=await fetch('/api/tasks/bulk-preview?date='+encodeURIComponent(form.dataset.date),{
                    credentials:'same-origin',cache:'no-store',headers:{Accept:'application/json'}
                });
                if(!response.ok||response.redirected||!response.headers.get('content-type')?.includes('application/json'))throw new Error();
                const preview=await response.json();
                if(!form.dataset.user||preview.username!==form.dataset.user||preview.selectedDate!==form.dataset.date)throw new Error();
                const shift=form.dataset.bulkConfirm==='shift';
                const count=shift?preview.shiftCount:preview.overdueCount;
                if(!Number.isSafeInteger(count)||count<0)throw new Error();
                form.querySelector('.day-action-count').textContent=count;
                if(!count){window.alert(form.dataset.empty);return;}
                if(!window.confirm(text(form.dataset.confirm,[count,format(shift?preview.selectedDate:preview.today)])))return;
                form.dataset.confirmed='true';
                form.requestSubmit(event.submitter||undefined);
            } catch(error) { window.alert(form.dataset.failure); }
            finally { delete form.dataset.previewPending; }
        });
    });
    window.addEventListener('pageshow',()=>{
        document.querySelectorAll('[data-bulk-confirm]').forEach(form=>{
            delete form.dataset.confirmed;delete form.dataset.previewPending;form.removeAttribute('aria-busy');
        });
    });
})();
