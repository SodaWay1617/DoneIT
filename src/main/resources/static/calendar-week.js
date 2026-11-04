(() => {
    const root=document.querySelector('[data-calendar-week]');
    if(!root)return;
    const vertical=matchMedia('(max-width:799px), (max-width:899px) and (min-height:501px)');
    const params=new URLSearchParams(location.search);
    const prefix='doneit-week:'+encodeURIComponent(root.dataset.user || '')+':';
    const key=prefix+root.dataset.start+':'+(params.get('projectId') || '')+':'+(params.get('planningType') || '');
    const pendingKey=prefix+'return',forceKey=prefix+'today';
    const read=name=>{try{return JSON.parse(sessionStorage.getItem(name));}catch{return null;}};
    const write=(name,value)=>{try{sessionStorage.setItem(name,JSON.stringify(value));}catch{/* Browsing remains usable without storage. */}};
    const remove=name=>{try{sessionStorage.removeItem(name);}catch{/* Storage may be unavailable. */}};
    const scroller=()=>{
        const main=document.getElementById('app-main');
        return main && ['auto','scroll'].includes(getComputedStyle(main).overflowY) ? main : document.scrollingElement;
    };
    const top=()=>scroller()?.scrollTop || 0;
    let skipSave=false,interacted=false,initialized=false,saveTimer;
    const ensureTop=value=>{
        if(!vertical.matches)return;
        const host=scroller();if(!host)return;
        const maximum=host.scrollHeight-host.clientHeight;
        if(value>maximum) {
            const tail=parseFloat(root.style.getPropertyValue('--cw-scroll-tail')) || 0;
            root.style.setProperty('--cw-scroll-tail',(tail+Math.ceil(value-maximum))+'px');
        }
    };
    const save=day=>{
        if(skipSave)return;
        const current=day || [...root.querySelectorAll('.cw-day')].find(d=>d.getBoundingClientRect().bottom>90)?.dataset.date;
        write(key,{top:top(),day:current,vertical:vertical.matches,time:Date.now()});
    };
    const moveTo=date=>{
        const day=document.getElementById('week-day-'+date);
        if(day) {
            const host=scroller();
            const offset=host===document.scrollingElement?0:host.getBoundingClientRect().top;
            ensureTop(top()+day.getBoundingClientRect().top-offset-(parseFloat(getComputedStyle(day).scrollMarginTop) || 0));
            day.scrollIntoView({block:'start',behavior:'instant'});
        }
    };
    const fresh=record=>record && Number.isFinite(record.time) && Date.now()-record.time<24*60*60*1000;
    const init=()=>{
        if(initialized)return;
        initialized=true;
        const force=read(forceKey);
        const forced=force?.week===root.dataset.start && Date.now()-force.time<60000;
        remove(forceKey);
        const pending=read(pendingKey);
        const navigation=performance.getEntriesByType('navigation')[0]?.type;
        const restore=!forced && (pending?.key===key && fresh(pending) || ['reload','back_forward'].includes(navigation));
        if(pending?.key===key)remove(pendingKey);
        const stored=read(key);
        if(restore && fresh(stored)) {
            if(stored.vertical===vertical.matches) {
                ensureTop(Math.max(0,stored.top));
                scroller()?.scrollTo({top:Math.max(0,stored.top),behavior:'instant'});
            }
            else if(pending?.day || stored.day)moveTo(pending?.day || stored.day);
            return;
        }
        if(!vertical.matches || interacted)return;
        if(root.dataset.focus && document.getElementById('week-day-'+root.dataset.focus))moveTo(root.dataset.focus);
        else if(document.getElementById('week-day-'+root.dataset.today))moveTo(root.dataset.today);
        else scroller()?.scrollTo({top:0,behavior:'instant'});
    };
    const schedule=()=>requestAnimationFrame(()=>requestAnimationFrame(init));
    window.addEventListener('pageshow',event=>{
        if(event.persisted) {
            if(read(pendingKey)?.key===key)remove(pendingKey);
            skipSave=false;initialized=true;interacted=true;
        }
        else schedule();
    });
    if(document.readyState==='complete')schedule();
    root.addEventListener('click',event=>{
        const link=event.target.closest('[data-week-task]');
        if(!link || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey)return;
        save(link.closest('.cw-day')?.dataset.date);
        write(pendingKey,{key,day:link.closest('.cw-day')?.dataset.date,time:Date.now()});
    });
    document.querySelector('[data-calendar-today]')?.addEventListener('click',event=>{
        if(event.ctrlKey || event.metaKey || event.shiftKey || event.altKey)return;
        const target=new URL(event.currentTarget.href);
        write(forceKey,{week:target.searchParams.get('week'),time:Date.now()});
        remove(pendingKey);skipSave=true;
    });
    ['pointerdown','touchstart','wheel','keydown'].forEach(name=>window.addEventListener(name,()=>{interacted=true;},{passive:true}));
    window.addEventListener('scroll',()=>{
        if(!initialized || skipSave)return;
        clearTimeout(saveTimer);saveTimer=setTimeout(()=>save(),150);
    },true);
    window.addEventListener('pagehide',()=>save());
})();
