import { writeFileSync, readFileSync } from 'node:fs';

if (process.argv[2] === 'save') {
  const r = await fetch('https://api.github.com/repos/redhat-et/ripwire/readme', {
    headers: { accept: 'application/vnd.github.raw', 'user-agent': 'dsh-probe' },
  });
  const t = await r.text();
  writeFileSync('D:/Pumpkin_build/__ripwire.md', t);
  console.log('saved', t.length, 'chars');

  // 同时存 INSTALL.md
  for (const f of ['INSTALL.md', 'CHANGELOG.md']) {
    const rr = await fetch(`https://raw.githubusercontent.com/redhat-et/ripwire/main/${f}`);
    if (rr.ok) { const tt = await rr.text(); writeFileSync(`D:/Pumpkin_build/__ripwire_${f}`, tt); console.log('saved', f, tt.length); }
    else console.log(f, 'HTTP', rr.status);
  }
} else {
  const t = readFileSync('D:/Pumpkin_build/__ripwire.md', 'utf8');
  const lines = t.split('\n');
  const show = (startRe, count) => {
    const i = lines.findIndex((l) => startRe.test(l));
    if (i < 0) { console.log('NOT FOUND', startRe); return; }
    console.log(`\n${'='.repeat(70)}\n[line ${i + 1}] ${lines[i]}\n${'='.repeat(70)}`);
    console.log(lines.slice(i, i + count).join('\n'));
  };
  show(/^##\s+Set it up in your coding agent/i, 90);
  show(/^##\s+Install/i, 60);
}
