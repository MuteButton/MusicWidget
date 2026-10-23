// Summarize Android HPROF live instance counts after `am dumpheap -g`.
// This is a retention trend aid, not a full GC-root/dominator leak analyzer.
const fs=require('fs');
const b=fs.readFileSync(process.argv[2]);let p=b.indexOf(0)+1;
const idSize=b.readUInt32BE(p);p+=12;
const strings=new Map(),classes=new Map(),counts=new Map();
const id=()=>{const x=b.subarray(p,p+idSize).toString('hex');p+=idSize;return x;};
const u1=()=>b[p++],u2=()=>{const v=b.readUInt16BE(p);p+=2;return v;},u4=()=>{const v=b.readUInt32BE(p);p+=4;return v;};
const sizes={2:idSize,4:1,5:2,6:4,7:8,8:1,9:2,10:4,11:8};
function value(type){if(!sizes[type])throw Error('Unknown value type '+type);p+=sizes[type];}
function heap(end){while(p<end){const tag=u1();switch(tag){
case 0xff:case 5:case 7:case 0x89:case 0x8a:case 0x8b:case 0x8c:case 0x8d:case 0x90:p+=idSize;break;
case 1:p+=2*idSize;break;
case 2:case 3:case 8:case 0x8e:p+=idSize+8;break;
case 4:case 6:p+=idSize+4;break;
case 0xfe:p+=4+idSize;break;
case 0x20:{p+=idSize+4+6*idSize+4;let n=u2();for(let i=0;i<n;i++){p+=2;value(u1());}n=u2();for(let i=0;i<n;i++){p+=idSize;value(u1());}n=u2();p+=n*(idSize+1);break;}
case 0x21:{p+=idSize+4;const cls=id(),len=u4();counts.set(cls,(counts.get(cls)||0)+1);p+=len;break;}
case 0x22:{p+=idSize+4;const len=u4();p+=idSize+len*idSize;break;}
case 0x23:case 0xc3:{p+=idSize+4;const len=u4(),type=u1();if(tag===0x23)p+=len*sizes[type];break;}
default:throw Error('Unknown heap tag 0x'+tag.toString(16)+' at '+(p-1));
}}if(p!==end)throw Error('Heap record overrun');}
while(p<b.length){const tag=u1();p+=4;const len=u4(),end=p+len;
if(tag===1){const key=id();strings.set(key,b.toString('utf8',p,end));}
else if(tag===2){p+=4;const cls=id();p+=4;classes.set(cls,id());}
else if(tag===0xc||tag===0x1c)heap(end);
p=end;}
const all=[...counts].map(([key,count])=>({className:strings.get(classes.get(key))||key,count}));
const selected=all.filter(x=>x.className.includes('io.github.mutebutton.musicwidget')||x.className.includes('io/github/mutebutton/musicwidget')||/android[./](graphics[./]Bitmap$|media[./]session[./]MediaController$|app[./]PendingIntent$)/.test(x.className));
selected.sort((a,b)=>a.className.localeCompare(b.className));
console.log(JSON.stringify({file:process.argv[2],bytes:b.length,idSize,instanceCount:[...counts.values()].reduce((a,b)=>a+b,0),selected},null,2));
