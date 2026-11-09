(() => {
    const query=new URLSearchParams(window.location.search);
    const replay=query.get('mode')==='replay' || query.has('runId');
    const select=document.getElementById('monitor-mode');select.value=replay?'replay':'live';
    select.onchange=()=>{window.location.href=select.value==='replay'?'cycle.html?mode=replay':'cycle.html';};
    function load(source) {
        return new Promise((resolve,reject)=>{const script=document.createElement('script');script.src=source;script.onload=resolve;script.onerror=()=>reject(new Error('页面脚本加载失败，请刷新重试'));document.body.append(script);});
    }
    (async()=>{
        if(replay) {
            document.body.classList.add('replay-mode');
            await load('../js/services/sandbox-replay.js?v=20261109-1');
            await load('../js/sandbox-replay.js?v=20261109-1');
        } else {
            await load('../js/dashboard.js');await load('../js/cycle.js?v=20261109-1');
            if(document.readyState==='complete')window.onload?.();
        }
    })().catch(error=>{document.getElementById('data-health').textContent=error.message;});
})();
