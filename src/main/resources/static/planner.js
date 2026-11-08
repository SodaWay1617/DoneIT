(() => {
    const root=document.querySelector('[data-planner]');if(!root)return;
    const wide=matchMedia('(min-width:900px), (min-width:800px) and (max-height:500px)');
    const sources=root.querySelector('[data-planner-sources]'),dialog=document.getElementById('planner-dialog');
    if(!sources)return;
    const form=dialog.querySelector('form'),day=form.elements.plannedDate,withTime=form.elements.plannedWithTime,time=form.elements.time;
    const rows=[...root.querySelectorAll('.pp-task[data-mutable=true]')];
    const pointer=matchMedia('(pointer:fine)');
    root.querySelector('.pp-filter select').addEventListener('change',event=>{
        if(document.documentElement.classList.contains('app-standalone') && matchMedia('(max-width:1023px)').matches) event.target.form.requestSubmit();
    });
    const syncDrag=()=>rows.forEach(row=>{row.draggable=root.dataset.mode==='week' && wide.matches && pointer.matches;});
    wide.addEventListener('change',syncDrag);pointer.addEventListener('change',syncDrag);
    const prefix='doneit-planner:'+root.dataset.user+':';
    const params=new URLSearchParams(location.search);
    const key=prefix+root.dataset.mode+':'+root.dataset.start+':'+(params.get('projectId')||'')+':'+(params.get('backlogPage')||1)+':'+(params.get('inboxPage')||1);
    const read=k=>{try{return JSON.parse(sessionStorage.getItem(k));}catch{return null;}};
    const write=(k,v)=>{try{sessionStorage.setItem(k,JSON.stringify(v));}catch{/* State is optional. */}};
    const scroller=()=>{const main=document.getElementById('app-main');return ['auto','scroll'].includes(getComputedStyle(main).overflowY)?main:document.scrollingElement;};
    const save=()=>write(key,{page:scroller().scrollTop,queue:sources.scrollTop,groups:[...sources.querySelectorAll('[data-group]')].map(g=>[g.dataset.group,g.open])});
    const restore=read(key);
    if(restore) {
        sources.scrollTop=restore.queue||0;
        restore.groups?.forEach(([name,open])=>{const group=sources.querySelector('[data-group="'+name+'"]');if(group)group.open=open;});
        if(read(prefix+'restore')===key || ['reload','back_forward'].includes(performance.getEntriesByType('navigation')[0]?.type))
            requestAnimationFrame(()=>scroller().scrollTop=restore.page||0);
    }
    write(prefix+'restore',null);
    const preserve=()=>{save();write(prefix+'restore',key);};
    window.addEventListener('pagehide',save);
    const edit=(row,estimate=false)=>{
        preserve();
        if(root.dataset.mode==='prepare' && wide.matches) {
            const url=new URL(root.dataset.return,location.origin);url.searchParams.set('taskId',row.dataset.id);
            if(estimate)url.hash='task-estimate';location.assign(url);
        } else location.assign(row.dataset.edit+(estimate?'#task-estimate':''));
    };
    const updateTime=()=>{
        form.querySelector('[data-planner-time-field]').hidden=!withTime.checked;
        time.disabled=!withTime.checked;time.required=withTime.checked;
    };
    withTime.addEventListener('change',updateTime);
    const open=(row,date,backlog=false)=>{
        form.action='/planning/tasks/'+row.dataset.id+(backlog?'/backlog':'/schedule');
        document.getElementById('planner-dialog-title').textContent=backlog?dialog.dataset.backlogTitle:dialog.dataset.planTitle;
        form.querySelector('button[type=submit]').textContent=backlog?dialog.dataset.backlogTitle:
            row.dataset.source==='day'?dialog.dataset.moveLabel:dialog.dataset.planLabel;
        form.querySelector('[data-planner-task]').textContent=row.dataset.title;
        form.querySelector('[data-planner-date-fields]').hidden=backlog;
        form.querySelector('[data-backlog-confirm]').hidden=!backlog;
        day.disabled=backlog;withTime.disabled=backlog;day.value=date||row.dataset.date||root.dataset.date;
        withTime.checked=!backlog && Boolean(row.dataset.time);
        time.value=row.dataset.time||'';updateTime();
        preserve();dialog.showModal();
        form.querySelector('[data-close-planner]').focus({preventScroll:true});
    };
    root.querySelectorAll('[data-close-planner]').forEach(button=>button.addEventListener('click',()=>dialog.close()));
    const menus=window.DoneItTaskMenu(root,'.pp-task[data-mutable=true]');
    rows.forEach(row=>{
        const card=row.querySelector('.pp-card');
        card.addEventListener('click',event=>{
            if(event.ctrlKey||event.metaKey||event.shiftKey||event.altKey)return;
            event.preventDefault();
            if(root.dataset.mode==='week')open(row,row.dataset.source==='backlog'?root.dataset.date:row.dataset.date);
            else edit(row);
        });
        row.querySelector('[data-plan-action]')?.addEventListener('click',()=>open(row,row.dataset.source==='backlog'?root.dataset.date:row.dataset.date));
        row.querySelector('[data-backlog-action]')?.addEventListener('click',()=>open(row,null,true));
        row.querySelectorAll('[data-edit-action],[data-estimate-action]').forEach(link=>link.addEventListener('click',event=>{
            if(event.ctrlKey||event.metaKey||event.shiftKey||event.altKey)return;
            event.preventDefault();edit(row,link.hasAttribute('data-estimate-action'));
        }));
        row.addEventListener('dragstart',event=>{
            if(!row.draggable){event.preventDefault();return;}
            event.dataTransfer.setData('application/x-doneit-planning',row.dataset.id);event.dataTransfer.effectAllowed='move';
            row.classList.add('is-dragging');menus.close();
        });
        row.addEventListener('dragend',()=>{row.classList.remove('is-dragging');root.querySelectorAll('.pp-drop-target').forEach(el=>el.classList.remove('pp-drop-target'));});
    });
    syncDrag();
    root.querySelectorAll('[data-drop-date],[data-backlog-drop=true]').forEach(target=>{
        target.addEventListener('dragover',event=>{
            if(!event.dataTransfer.types.includes('application/x-doneit-planning'))return;
            event.preventDefault();event.dataTransfer.dropEffect='move';target.classList.add('pp-drop-target');
        });
        target.addEventListener('dragleave',event=>{if(!target.contains(event.relatedTarget))target.classList.remove('pp-drop-target');});
        target.addEventListener('drop',event=>{
            const id=event.dataTransfer.getData('application/x-doneit-planning'),row=rows.find(r=>r.dataset.id===id);
            if(!row)return;
            event.preventDefault();target.classList.remove('pp-drop-target');
            const backlog=target.dataset.backlogDrop==='true';
            if(backlog && row.dataset.source==='backlog')return;
            open(row,target.dataset.dropDate,backlog);
        });
    });
    // The shared WIP handler owns scheduling POSTs. Backlog returns use the same confirmation form without WIP.
    document.addEventListener('submit',async event=>{
        if(event.defaultPrevented || event.target!==form || !form.action.endsWith('/backlog'))return;
        event.preventDefault();
        if(form.dataset.pending)return;form.dataset.pending='true';
        try {
            const response=await fetch(form.action,{method:'POST',body:new FormData(form),credentials:'same-origin'});
            if(!response.ok || !response.redirected)throw new Error();
            location.assign(response.url);
        } catch {window.alert(root.dataset.failure);}
        finally {delete form.dataset.pending;}
    });
    root.querySelectorAll('form').forEach(f=>f.addEventListener('submit',preserve));
    root.querySelectorAll('.pp-paging a,.pp-editor-back').forEach(link=>link.addEventListener('click',preserve));
})();
