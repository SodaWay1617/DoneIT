(() => {
    const labels=document.currentScript.dataset;
    const busy=new WeakSet();
    const supported=/^(?:\/tasks(?:\/\d+(?:\/(?:reschedule|start-today))?|\/bulk-move-(?:to-tomorrow|overdue-to-today))?|\/planning\/tasks\/\d+\/schedule)$/;
    const duration=n=>{const h=Math.floor(n/60),m=n%60;return [h?`${h} ${labels.hours}`:'',m||!h?`${m} ${labels.minutes}`:''].filter(Boolean).join(' ');};
    const confirm=payload=>new Promise(resolve=>{
        const dialog=document.createElement('dialog');dialog.className='wip-dialog';
        const title=document.createElement('h2');title.id='wip-title';title.textContent=labels.title;
        dialog.setAttribute('aria-labelledby',title.id);dialog.append(title);
        payload.days.forEach(day=>{
            const section=document.createElement('section'),heading=document.createElement('h3');
            heading.textContent=new Intl.DateTimeFormat(document.documentElement.lang,{day:'numeric',month:'long',year:'numeric'}).format(new Date(day.date+'T12:00:00'));
            section.append(heading);
            [['before',day.before],['after',day.after],['limit',day.limit],['excess',day.excess],['added',day.added]].forEach(([key,value])=>{
                const line=document.createElement('p');line.textContent=labels[key]+': '+duration(value);section.append(line);
            });dialog.append(section);
        });
        const hint=document.createElement('p');hint.textContent=labels.hint;dialog.append(hint);
        const actions=document.createElement('div');actions.className='wip-actions';
        const edit=document.createElement('button');edit.type='button';edit.className='wip-edit';edit.textContent=labels.edit;
        const proceed=document.createElement('button');proceed.type='button';proceed.textContent=labels.confirm;
        let accepted=false;edit.addEventListener('click',()=>dialog.close());
        proceed.addEventListener('click',()=>{accepted=true;dialog.close();});
        actions.append(edit,proceed);dialog.append(actions);document.body.append(dialog);
        dialog.addEventListener('close',()=>{dialog.remove();resolve(accepted);},{once:true});
        dialog.showModal();edit.focus();
    });
    document.addEventListener('submit',async event=>{
        const form=event.target;
        if(event.defaultPrevented || !(form instanceof HTMLFormElement) || form.method!=='post' || form.hasAttribute('data-wip-replay'))return;
        const url=new URL(form.action,location.href);
        if(url.origin!==location.origin || !supported.test(url.pathname))return;
        event.preventDefault();event.stopImmediatePropagation();
        if(busy.has(form))return;
        busy.add(form);form.setAttribute('aria-busy','true');
        const body=new FormData(form);
        if(event.submitter?.name)body.append(event.submitter.name,event.submitter.value);
        body.delete('wipApproval');
        try {
            for(;;) {
                const response=await fetch(url,{method:'POST',body,credentials:'same-origin',headers:{'X-DoneIt-Wip':'true'}});
                if(response.status===409 && response.headers.get('content-type')?.includes('application/json')) {
                    const payload=await response.json();if(payload.kind!=='wip')throw new Error();
                    if(!await confirm(payload)) {
                        const field=[...form.querySelectorAll('[name=plannedDate],[name=plannedForAt]')].find(el=>el.offsetParent!==null);
                        field?.focus();break;
                    }
                    body.set('wipApproval',payload.approval);continue;
                }
                if(response.redirected) { location.assign(response.url);break; }
                if(!response.ok)throw new Error();
                // Existing server-side form validation supplies the populated form, including its errors.
                const html=await response.text();document.open();document.write(html);document.close();break;
            }
        } catch {
            window.alert(labels.failure);
        } finally {
            busy.delete(form);form.removeAttribute('aria-busy');
        }
    });
})();
