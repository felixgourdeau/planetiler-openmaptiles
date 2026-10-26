import {readFile,writeFile} from 'node:fs/promises';
const root='/private/tmp/outmap-sea-to-sky-20261025';
type Tile={key:string;pilotStored:number;pilotRaw:number;pilotGzip:number;productionRaw?:number;productionGzip?:number};
type Comparison={counts:Record<string,number>;identities:Record<string,unknown>;examples:Record<string,unknown>;tiles:Tile[]};
const data:Comparison=JSON.parse(await readFile(`${root}/comparison.json`,'utf8'));
const interior=(t:Tile)=>{const[z,x,y]=t.key.split('/').map(Number);const n=2**z;const lon=(x:number)=>x/n*360-180;const lat=(y:number)=>Math.atan(Math.sinh(Math.PI*(1-2*y/n)))*180/Math.PI;return lon(x)>=-123.75&&lon(x+1)<=-122.25&&lat(y)<=50.65&&lat(y+1)>=49.25;};
const stats=(values:number[])=>{values.sort((a,b)=>a-b);const percentile=(p:number)=>values[Math.max(0,Math.ceil(values.length*p)-1)]??0;return{sum:values.reduce((a,b)=>a+b,0),median:percentile(.5),p95:percentile(.95),p99:percentile(.99),max:percentile(1)};};
const groups:Record<string,unknown>={};
for(let z=0;z<=14;z++){for(const mode of ['all','interior']){const rows=data.tiles.filter(t=>Number(t.key.split('/')[0])===z&&t.productionRaw!==undefined&&(mode==='all'||interior(t)));groups[`${mode}/${z}`]={tiles:rows.length,pilot:stats(rows.map(t=>t.pilotGzip)),production:stats(rows.map(t=>t.productionGzip??0))};}}
const ranges:Record<string,{min:number;max:number;features:number}>={};
for(const[k,v]of Object.entries(data.counts)){const[side,zoom,...rest]=k.split('/');if(rest.some(x=>x.startsWith('@')||x.startsWith('#')))continue;const category=side+'/'+rest.join('/');const z=Number(zoom);const previous=ranges[category];ranges[category]={min:Math.min(previous?.min??z,z),max:Math.max(previous?.max??z,z),features:(previous?.features??0)+v};}
const top=data.tiles.filter(t=>t.productionGzip!==undefined).sort((a,b)=>(b.pilotGzip-(b.productionGzip??0))-(a.pilotGzip-(a.productionGzip??0))).slice(0,15);
const result={groups,ranges,top,countByZoomLayerCategoryField:data.counts,identities:data.identities,examples:data.examples};
await writeFile(`${root}/benchmark-summary.json`,JSON.stringify(result,null,2));
console.log(JSON.stringify({groups,top:top.slice(0,5)},null,2));
