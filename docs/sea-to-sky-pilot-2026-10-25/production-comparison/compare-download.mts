import {readFile,writeFile,mkdir,access} from 'node:fs/promises';
import {gzipSync} from 'node:zlib';
const root='/private/tmp/outmap-sea-to-sky-20261025';
const sizes:Record<string,number>=JSON.parse(await readFile(`${root}/sea-to-sky-20261025-r2-tile-sizes.json`,'utf8'));
const keys=Object.keys(sizes);let cursor=0,done=0;const rows:Record<string,unknown>={};
await mkdir(`${root}/production`,{recursive:true});
const worker=async()=>{while(cursor<keys.length){const key=keys[cursor++];const path=`${root}/production/${key.replaceAll('/','_')}.mvt`;let success=false;
for(let attempt=0;attempt<3&&!success;attempt++){try{const started=performance.now();const response=await fetch(`https://planet.tiles.outmap.pro/planet-250825/${key}.mvt`,{signal:AbortSignal.timeout(30000)});const bytes=Buffer.from(await response.arrayBuffer());if(response.status!==200&&response.status!==204&&response.status!==404)throw new Error(`HTTP ${response.status}`);if(response.status===200)await writeFile(path,bytes);rows[key]={status:response.status,decodedBytes:bytes.length,recompressedBytes:response.status===200?gzipSync(bytes).length:0,contentLength:response.headers.get('content-length'),contentEncoding:response.headers.get('content-encoding'),elapsedMs:Math.round(performance.now()-started)};success=true;}catch(error){if(attempt===2)rows[key]={error:String(error)};}}
if(++done%500===0)console.log(`${done}/${keys.length}`);}}
await Promise.all(Array.from({length:8},worker));await writeFile(`${root}/production-download.json`,JSON.stringify(rows));console.log('complete',keys.length);
