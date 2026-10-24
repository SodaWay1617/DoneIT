(() => {
    const list=document.querySelector('[data-compact-list]');
    if(!list) return;
    list.classList.add('compact-enhanced');
    const filter=list.querySelector('#compact-filters'), toggle=list.querySelector('[data-filter-toggle]');
    toggle.addEventListener('click',()=>{
        const open=filter.classList.toggle('is-open');toggle.setAttribute('aria-expanded',String(open));
    });
    filter.querySelectorAll('.compact-categories input').forEach(input=>input.addEventListener('change',()=>{
        if(matchMedia('(min-width:768px)').matches) filter.requestSubmit();
    }));
    let active, suppressed, timer, origin;
    const menus=new Map();
    const items=menu=>[...menu.querySelectorAll('button:not(:disabled),a[href]')];
    const close=(focus=false)=>{
        if(!active) return;
        const {row,menu,button}=active;
        menu.hidden=true;row.append(menu);button.setAttribute('aria-expanded','false');
        active=null;if(focus) button.focus();
    };
    const open=row=>{
        close();
        const menu=menus.get(row),button=row.querySelector('[data-row-menu]');
        document.body.append(menu);menu.hidden=false;
        active={row,menu,button};button.setAttribute('aria-expanded','true');
        const rect=row.getBoundingClientRect(), box=menu.getBoundingClientRect();
        const viewport=window.visualViewport;
        const top=viewport?.offsetTop||0, height=viewport?.height||innerHeight;
        const bottomNav=document.querySelector('.app-bottom-nav');
        const nav=bottomNav?.getBoundingClientRect();
        const bottom=nav&&nav.height ? Math.min(top+height,nav.top) : top+height;
        const desired=rect.top-box.height-6;
        const y=desired>=top+8?desired:rect.bottom+6;
        menu.style.left=Math.max(10,Math.min(rect.right-box.width,innerWidth-box.width-10))+'px';
        menu.style.top=Math.max(top+8,Math.min(y,bottom-box.height-8))+'px';
        items(menu)[0]?.focus({preventScroll:true});
    };
    list.querySelectorAll('.compact-row').forEach((row,index)=>{
        const menu=row.querySelector('.compact-menu'),button=row.querySelector('[data-row-menu]');
        menus.set(row,menu);menu.id='compact-menu-'+index;menu.setAttribute('role','menu');
        button.setAttribute('aria-controls',menu.id);
        items(menu).forEach(item=>{
            item.setAttribute('role','menuitem');
            item.title=item.textContent.trim();
            item.setAttribute('aria-label',item.title);
        });
        button.addEventListener('click',()=>active?.row===row?close(true):open(row));
        row.addEventListener('contextmenu',event=>{
            if(event.target.closest('button,input,form')) return;
            event.preventDefault();open(row);
        });
        const clear=()=>{clearTimeout(timer);timer=null;};
        row.addEventListener('pointerdown',event=>{
            if(event.pointerType==='mouse'||event.target.closest('button,input,form')) return;
            clear();origin={x:event.clientX,y:event.clientY};
            timer=setTimeout(()=>{suppressed=row;open(row);timer=null;},550);
        });
        row.addEventListener('pointermove',event=>{
            if(origin&&Math.hypot(event.clientX-origin.x,event.clientY-origin.y)>10) clear();
        });
        ['pointerup','pointercancel','pointerleave'].forEach(name=>row.addEventListener(name,clear));
        row.addEventListener('click',event=>{
            if(suppressed===row){event.preventDefault();event.stopPropagation();suppressed=null;}
        },true);
        const complete=row.querySelector('.compact-complete');
        complete.querySelector('button').addEventListener('click',event=>{
            if(complete.getAttribute('aria-busy')==='true'||row.dataset.pending==='true') event.preventDefault();
        });
        menu.querySelector('[data-complete-row]').addEventListener('click',()=>{
            if(complete.getAttribute('aria-busy')!=='true'&&row.dataset.pending!=='true') complete.requestSubmit();
        });
        menu.addEventListener('click',event=>{
            if(event.target.closest('button,a')&&!event.target.closest('button:disabled')) close();
        });
        menu.addEventListener('keydown',event=>{
            const entries=items(menu),index=entries.indexOf(document.activeElement);
            if(['ArrowRight','ArrowDown','ArrowLeft','ArrowUp','Home','End'].includes(event.key)){
                event.preventDefault();
                const next=event.key==='Home'?0:event.key==='End'?entries.length-1:(index+(['ArrowLeft','ArrowUp'].includes(event.key)?-1:1)+entries.length)%entries.length;
                entries[next]?.focus();
            }
        });
    });
    document.addEventListener('pointerdown',event=>{
        suppressed=null;
        if(active&&!active.menu.contains(event.target)&&!event.target.closest('[data-row-menu]')) close();
    });
    document.addEventListener('keydown',event=>{
        if(event.key==='Escape'){close(true);filter.classList.remove('is-open');toggle.setAttribute('aria-expanded','false');}
        if(event.key==='Tab'&&active) close(true);
    });
    window.addEventListener('scroll',event=>{
        if(timer){clearTimeout(timer);timer=null;}
        if(active&&!active.menu.contains(event.target)) close();
    },true);
    window.addEventListener('resize',()=>close());
    const dialog=document.getElementById('compact-move-dialog'), form=dialog.querySelector('form');
    const day=form.elements.plannedDate, withTime=form.querySelector('[data-move-with-time]'), time=form.querySelector('[data-move-time]');
    const update=()=>{form.querySelector('[data-move-time-label]').hidden=!withTime.checked;time.required=withTime.checked;};
    list.querySelectorAll('[data-reschedule]').forEach(button=>button.addEventListener('click',()=>{
        form.action=button.dataset.action;
        day.value=button.dataset.day;time.value=button.dataset.start;
        withTime.checked=Boolean(button.dataset.start);
        form.querySelector('[data-move-task]').textContent=button.dataset.title;
        form.querySelector('[data-current-date]').textContent=button.dataset.day;
        update();dialog.showModal();day.focus();
    }));
    withTime.addEventListener('change',update);
    form.querySelectorAll('[data-close-dialog]').forEach(button=>button.addEventListener('click',()=>dialog.close()));
    dialog.addEventListener('click',event=>{if(event.target===dialog){const r=dialog.getBoundingClientRect();if(event.clientX<r.left||event.clientX>r.right||event.clientY<r.top||event.clientY>r.bottom)dialog.close();}});
    form.addEventListener('submit',event=>{
        form.elements.plannedForAt.disabled=!withTime.checked;
        // The explicit mode flag tells the existing endpoint which date representation to use.
        form.elements.plannedForAt.value=withTime.checked?day.value+'T'+time.value:'';
        if(withTime.checked&&!time.value){event.preventDefault();time.reportValidity();}
    });
    window.addEventListener('pageshow',()=>{
        list.querySelectorAll('[data-pending]').forEach(row=>delete row.dataset.pending);
        list.querySelectorAll('[aria-checked]').forEach(button=>button.setAttribute('aria-checked','false'));
    });
    document.addEventListener('DOMContentLoaded',()=>{
        const query=new URLSearchParams(location.search), selected=query.getAll('tagId');
        document.querySelectorAll('a[href]').forEach(link=>{
            const url=new URL(link.href,location.origin);
            if(link.hasAttribute('data-reset-list')){url.searchParams.delete('planningType');url.searchParams.delete('tagId');link.href=url.pathname+url.search;return;}
            if(url.origin!==location.origin||!['/','/tasks'].includes(url.pathname))return;
            if(link.closest('.app-project-section')&&location.pathname==='/tasks'){
                url.pathname='/tasks';url.searchParams.set('date',query.get('date'));
            }
            if(selected.length&&!link.closest('.compact-filters')){
                url.searchParams.delete('tagId');
                selected.forEach(id=>url.searchParams.append('tagId',id));link.href=url.pathname+url.search;
            } else if(link.closest('.app-project-section')) link.href=url.pathname+url.search;
        });
        document.querySelectorAll('.today-day-navigation form,.today-date-picker form').forEach(target=>{
            selected.forEach(id=>{const field=document.createElement('input');field.type='hidden';field.name='tagId';field.value=id;target.append(field);});
        });
        const feedback=document.createElement('p');feedback.className='compact-feedback';feedback.hidden=true;feedback.setAttribute('role','alert');list.prepend(feedback);
        // Runs after the existing CSRF refresh listener; only its replay reaches this handler.
        document.addEventListener('submit',async event=>{
            const target=event.target;
            if(event.defaultPrevented||!target.matches('.compact-row form,.compact-menu form,.compact-move-form'))return;
            event.preventDefault();
            if(target.dataset.pending==='true')return;
            target.dataset.pending='true';
            const row=[...menus].find(([row,menu])=>row.contains(target)||menu.contains(target))?.[0];
            if(row)row.dataset.pending='true';
            try{
                const response=await fetch(target.action,{method:'POST',body:new FormData(target),credentials:'same-origin'});
                if(!response.ok||!response.redirected||new URL(response.url).pathname==='/login')throw new Error();
                location.assign(response.url);
            }catch(error){
                feedback.textContent=list.dataset.failure;feedback.hidden=false;
                if(dialog.open)window.alert(list.dataset.failure);
                delete target.dataset.confirmed;
            }finally{
                delete target.dataset.pending;if(row)delete row.dataset.pending;
            }
        });
    });
})();
