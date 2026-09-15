const r = await fetch('https://api.github.com/repos/redhat-et/ripwire/releases/latest', {
  headers: { 'user-agent': 'dsh-probe', accept: 'application/vnd.github+json' },
});
console.log('HTTP', r.status);
const j = await r.json();
console.log('tag:', j.tag_name, '| published:', j.published_at);
console.log('assets:');
for (const a of j.assets ?? []) {
  console.log(`  ${a.name.padEnd(46)} ${(a.size / 1048576).toFixed(1)} MB  dl=${a.download_count}`);
}
console.log('\nbody (first 1200 chars):');
console.log(String(j.body ?? '').slice(0, 1200));
