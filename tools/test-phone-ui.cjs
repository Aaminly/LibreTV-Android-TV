// Exercise the shipped script's synchronization without a browser or fake success responses.
const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const html=fs.readFileSync('app/src/main/assets/phone-input.html','utf8');
const code=html.match(/<script nonce="__TOKEN__">([\s\S]*?)<\/script>/)[1];
const elements=new Map(),listeners=new Map();let requests=0,pending;
function element(id){if(!elements.has(id))elements.set(id,{hidden:false,textContent:'',value:0,disabled:false,style:{},addEventListener(name,fn){listeners.set(id+':'+name,fn)},querySelectorAll(){return []}});return elements.get(id)}
const context=vm.createContext({document:{hidden:false,getElementById:element,addEventListener(name,fn){listeners.set('document:'+name,fn)},body:{style:{}}},AbortController,URLSearchParams,console,setTimeout:()=>1,clearTimeout:()=>{},fetch:()=>{requests++;return new Promise(resolve=>pending=resolve)}});
vm.runInContext(code,context);
const run=js=>vm.runInContext(js,context);
(async()=>{
 assert.equal(requests,1);run('poll();poll()');assert.equal(requests,1,'one in-flight poll only');
 pending({ok:true,json:async()=>({active:false})});await new Promise(setImmediate);
 run('document.hidden=true;poll()');assert.equal(requests,1,'hidden page must not poll');
 run(`document.hidden=false;connection(true);renderState({active:true,mediaId:'a',title:'影片',duration:90000,position:10000,paused:false,seekable:true});dragId='a';`);
 element('seek').value=30000;run(`renderState({...state,position:12000})`);assert.equal(element('seek').value,30000,'poll must not overwrite drag');
 run(`renderState({...state,mediaId:'b',position:0})`);assert.equal(run('dragId'),null);assert.equal(element('seek').value,0,'changed media cancels stale drag');
 run(`dragId='b';connection(false)`);assert.equal(run('dragId'),null);assert.equal(element('toggle').disabled,true);assert.equal(element('seek').disabled,true);
 run(`connection(true);commandPending=true;controls()`);assert.equal(element('toggle').disabled,true,'pending command cannot be duplicated');
 console.log('PASS 7 phone synchronization checks');
})().catch(e=>{console.error(e);process.exitCode=1});
