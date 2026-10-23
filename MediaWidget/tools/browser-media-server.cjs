const http = require('http');
const fs = require('fs');
const eventPath=process.argv[2];
if(!eventPath)throw new Error('Pass event log path');
const rate=8000, seconds=600, wav=Buffer.alloc(44+rate*seconds*2);
wav.write('RIFF');wav.writeUInt32LE(wav.length-8,4);wav.write('WAVEfmt ',8);wav.writeUInt32LE(16,16);wav.writeUInt16LE(1,20);wav.writeUInt16LE(1,22);wav.writeUInt32LE(rate,24);wav.writeUInt32LE(rate*2,28);wav.writeUInt16LE(2,32);wav.writeUInt16LE(16,34);wav.write('data',36);wav.writeUInt32LE(wav.length-44,40);
for(let i=0;i<rate*seconds;i++)wav.writeInt16LE(Math.round(300*Math.sin(i*2*Math.PI*220/rate)),44+i*2);
const page=`<!doctype html><meta name="viewport" content="width=device-width"><title>Widget Browser Acceptance</title><h1>Widget Browser Acceptance</h1><button id="play" style="font-size:32px;padding:25px">Play test audio</button><audio id="audio" controls src="/tone.wav"></audio><p id="status">Ready</p><script>
const a=document.getElementById('audio');
navigator.mediaSession.metadata=new MediaMetadata({title:'Browser Acceptance Track',artist:'Local Chrome regression',album:'Widget test'});
const record=(event)=>{document.getElementById('status').textContent=event;fetch('/event?name='+event+'&position='+a.currentTime)};
navigator.mediaSession.setActionHandler('play',()=>{a.play();record('session-play')});
navigator.mediaSession.setActionHandler('pause',()=>{a.pause();record('session-pause')});
a.onplay=()=>{navigator.mediaSession.playbackState='playing';record('playing')};a.onpause=()=>{navigator.mediaSession.playbackState='paused';record('paused')};
a.ontimeupdate=()=>{if(isFinite(a.duration))navigator.mediaSession.setPositionState({duration:a.duration,playbackRate:1,position:a.currentTime})};
document.getElementById('play').onclick=()=>a.play();
</script>`;
http.createServer((req,res)=>{if(req.url.startsWith('/event')){fs.appendFileSync(eventPath,JSON.stringify({time:new Date().toISOString(),url:req.url})+'\n');res.end('ok');return;}if(req.url==='/tone.wav'){res.writeHead(200,{'Content-Type':'audio/wav','Content-Length':wav.length});res.end(wav);return;}res.writeHead(200,{'Content-Type':'text/html'});res.end(req.url.startsWith('/other')?'<title>Other tab</title><h1>Unrelated browser tab</h1>':page);}).listen(8765,'127.0.0.1',()=>console.log('Local browser acceptance server ready'));
