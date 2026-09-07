import fs from 'node:fs/promises';
import {FileBlob, SpreadsheetFile} from '@oai/artifact-tool';
const root='C:/Users/zdrav/Desktop/Raid Alliance Logistics Liason Equipment';
const out=root+'/outputs/01a07d96-settings-review';
const w=await SpreadsheetFile.importXlsx(await FileBlob.load('C:/Users/zdrav/Downloads/Untitled spreadsheet (1).xlsx'));
const s=w.worksheets.getItem('Settings');
const old=s.getRange('A5:L35').values;
const excluded=new Set(old.map(r=>r[3]));
const l=JSON.parse(await fs.readFile(root+'/src/main/resources/assets/ralle/lang/en_us.json','utf8'));
let java=await fs.readFile(root+'/src/main/java/org/kingdomfoxes/ralle/settings/RalleSettings.java','utf8');
const constants=[...java.matchAll(/public static final String (\w+) = "([^"]+)"/g)];
java=java.slice(java.indexOf('public static void register'));
for(const [,k,v] of constants) java=java.replaceAll(k,JSON.stringify(v));
const rows=[], covered=new Set(), meta=new Map();
let category='About', sub='General';
const title=id=>l['ralle.settings.option.'+id]||id;
for(const line of java.split('\n')){
 const cat=line.match(/^\s*"(about|chat|raid-lfg|war)",\s*$/); if(cat){category=cat[1]==='about'?'About':l['ralle.settings.category.'+cat[1]];sub='General';}
 const sc=line.match(/subcategory\("([^"]+)"/); if(sc)sub=l['ralle.settings.subcategory.'+sc[1]];
 for(const m of line.matchAll(/\b(toggle|choice|action|keybind|customPanel)\("([^"]+)"([^\n]*)/g)){
  const [,type,id,tail]=m; const vals=[...tail.matchAll(/"([^"]+)"/g)].map(x=>x[1]);
  meta.set(id,{category,sub,type,vals});
 }
}
meta.set('queue-self-color',{category:'War',sub:'Attack Timers',type:'color',vals:[]});
meta.set('hq-distance-keybind',{category:'War',sub:'Territory Map',type:'keybind',vals:[]});
const parents=new Map([...java.matchAll(/requireEnabled\("([^"]+)", "([^"]+)"\)/g)].map(m=>[m[1],title(m[2])]));
function add(cat,sub,type,id,text,desc='',def='',opts='',parent='') {rows.push([cat,sub,type,id,text,desc,def,opts,parent,'','','']);}
for(const [id,m] of meta){
 if(excluded.has(id))continue;
 const k='ralle.settings.option.'+id; if(!l[k])continue;
 let def='',opts='';
 if(m.type==='toggle'){def='Off';opts='On / Off';}
 if(m.type==='choice'){def=l['ralle.settings.value.'+m.vals[0]]||m.vals[0];opts=m.vals.slice(1).map(v=>l['ralle.settings.value.'+v]||v).join('; ');}
 if(m.type==='keybind'){def=id==='hq-distance-keybind'?'Ctrl (either Ctrl key)':'Unbound';opts='Any key; Unbound';}
 if(m.type==='color'){def='#5555FF';opts='Solid color; Rainbow';}
 add(m.category,m.sub,({toggle:'Boolean',choice:'Choice',action:'Action',keybind:'Keybind',customPanel:'Custom panel',color:'Color'})[m.type],id,l[k],l[k+'.description']||'',def,opts,parents.get(id)||'');covered.add(k);covered.add(k+'.description');
}
for(const id of excluded){covered.add('ralle.settings.option.'+id);covered.add('ralle.settings.option.'+id+'.description');}
for(const [k,v] of Object.entries(l)){
 if(covered.has(k)||k.endsWith('.description')||k.startsWith('ralle.settings.value.')||k==='ralle.settings.choice'||k==='ralle.settings.title')continue;
 if(!k.startsWith('ralle.settings.')&&!k.startsWith('ralle.consumables.')&&!k.startsWith('ralle.chat-layout.')&&k!=='ralle.war.queue.color.title')continue;
 let cat='Settings menu',sub='Shared controls',type='Label / message';
 if(k.startsWith('ralle.settings.option.'))throw Error('Unmapped option '+k);
 if(k.startsWith('ralle.settings.category.')){cat=v;sub='Navigation';type='Category';}
 if(k.startsWith('ralle.settings.subcategory.')){sub='Navigation';type='Subcategory';cat=['consumables','territory-map','attack-timers'].some(a=>k.endsWith('.'+a))?'War':['notifications','controls'].some(a=>k.endsWith('.'+a))?'Raid LFG':'Chat';}
 if(k.startsWith('ralle.settings.about')){cat='About';sub='About page';}
 if(k.includes('guild-ranks.')){cat='Chat';sub='Guild Ranks';type='Tooltip / status';}
 if(k.includes('hq-distance.')||k.includes('queue-attribution.')){cat='War';sub='Compatibility';type='Availability message';}
 if(k.startsWith('ralle.consumables.')){cat='War';sub='Consumables editor';type='Editor text';}
 if(k.startsWith('ralle.chat-layout.')){cat='HUD editor';sub='Opened from settings';type='Editor text';}
 if(k==='ralle.war.queue.color.title'){cat='War';sub='Queue color editor';type='Dialog title';}
 add(cat,sub,type,k,v,l[k+'.description']||'');
}
for(const [id,text] of [['settings.header','RALLE SETTINGS'],['about.identity','R.A.L.L.E.'],['about.version','Version {version}'],['gui.cancel','Cancel'],['color.validation','Color must use #RRGGBB'],['aliases.error','Could not add aliases'],['highlight.error','Could not save this highlight rule']])add('Settings menu','Other text','Label / message',id,text);
const n=rows.length+4;
s.getRange('A5:L35').clear({applyTo:'contents'});
for(let r=5;r<=n;r++)s.getRange(`A${r}:L${r}`).copyFrom(s.getRange('A5:L5'),'all');
s.getRange(`A5:L${n}`).values=rows;
s.getRange('A1').values=[['RALLE Settings — Missing Items Review']];
s.getRange('A2').values=[['Edit Proposed Description; use Notes for title/label changes or write "make description empty". Blank inputs mean no change. Includes settings, menu text, and editors opened from settings.']];
s.getRange('L5').formulas=[['=IF(AND(J5="",K5=""),"Unchanged",IF(J5<>"","Description draft","Needs clarification"))']];
s.getRange(`L5:L${n}`).fillDown();
s.getRange(`A5:L${n}`).format.wrapText=true;
s.getRange(`A5:L${n}`).format.verticalAlignment='top';
s.getRange('D:D').format.columnWidth=42;
s.getRange('A2:L2').format.rowHeight=32;
for(let i=0;i<rows.length;i++){
 const lengths=rows[i].slice(0,9).map((v,c)=>Math.ceil(String(v||'').length/[17,19,15,42,30,68,18,28,36][c]));
 s.getRange(`A${i+5}:L${i+5}`).format.rowHeight=Math.max(44,Math.max(...lengths)*15+12);
}
s.freezePanes.freezeRows(4);
w.recalculate();
console.log('Missing rows',rows.length,'new registered entries',rows.filter(r=>meta.has(r[3])).length);
console.log((await w.inspect({kind:'table',range:'Settings!D5:L8',include:'values,formulas',tableMaxRows:4,tableMaxCols:9,maxChars:2000})).ndjson);
console.log((await w.inspect({kind:'match',searchTerm:'#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A',options:{useRegex:true,maxResults:20},summary:'Errors'})).ndjson);
for(const [name,range,file] of [['Settings','A1:F12','settings-preview'],['Settings','I4:L12','inputs-preview'],['Notes','A1:D8','notes-preview']]){
 const b=await w.render({sheetName:name,range,scale:1,format:'png'});await fs.writeFile(out+'/'+file+'.png',new Uint8Array(await b.arrayBuffer()));
}
await (await SpreadsheetFile.exportXlsx(w)).save(out+'/ralle-missing-settings-review.xlsx');
await fs.writeFile(out+'/inventory.json',JSON.stringify(rows,null,2));
