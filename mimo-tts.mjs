#!/usr/bin/env node
/**
 * mimo-tts — 小米 MiMo Token Plan 文字转语音命令行小工具
 *
 * 支持的模型：
 *   mimo-v2.5-tts             预置音色 + 风格标签 + 唱歌
 *   mimo-v2.5-tts-voicedesign 用文字描述设计音色
 *   mimo-v2.5-tts-voiceclone  用音频样本复刻音色
 *
 * 用法示例：
 *   node mimo-tts.mjs "你好，世界"
 *   node mimo-tts.mjs "你好" --voice 茉莉 --out hello.mp3 --format mp3
 *   node mimo-tts.mjs --list-voices
 *   node mimo-tts.mjs "文本" --style 东北话
 *   node mimo-tts.mjs --design "低沉磁性的中年男声" --text "晚上好"
 *   node mimo-tts.mjs "文本" --clone ./sample.wav
 *   echo "从管道读入的文本" | node mimo-tts.mjs --out out.wav
 */

import { readFileSync, writeFileSync } from 'node:fs';
import { basename, extname } from 'node:path';
import { homedir } from 'node:os';

// ─────────────────────────── 配置 ───────────────────────────

const DEFAULT_BASE = 'https://token-plan-cn.xiaomimimo.com/v1';
const CREDENTIALS = `${homedir()}/.dsh/.credentials.yaml`;

const MODELS = {
  tts: 'mimo-v2.5-tts',
  design: 'mimo-v2.5-tts-voicedesign',
  clone: 'mimo-v2.5-tts-voiceclone',
};

/** 官方预置音色（实测 8 个全部有效） */
const VOICES = [
  { id: 'mimo_default', label: 'MiMo-默认', lang: '中文', gender: '女', note: '中国集群等同于「冰糖」' },
  { id: '冰糖', label: '冰糖', lang: '中文', gender: '女' },
  { id: '茉莉', label: '茉莉', lang: '中文', gender: '女' },
  { id: '苏打', label: '苏打', lang: '中文', gender: '男' },
  { id: '白桦', label: '白桦', lang: '中文', gender: '男' },
  { id: 'Mia', label: 'Mia', lang: '英文', gender: '女' },
  { id: 'Chloe', label: 'Chloe', lang: '英文', gender: '女' },
  { id: 'Milo', label: 'Milo', lang: '英文', gender: '男' },
  { id: 'Dean', label: 'Dean', lang: '英文', gender: '男' },
];

/** 支持的整体风格标签，写在文本最前面，形如 (东北话)文本 */
const STYLE_TAGS = {
  基础情绪: ['开心', '悲伤', '愤怒', '恐惧', '惊讶', '兴奋', '委屈', '平静', '冷漠'],
  复合情绪: ['怅然', '欣慰', '无奈', '愧疚', '释然', '嫉妒', '厌倦', '忐忑', '动情'],
  整体语调: ['温柔', '高冷', '活泼', '严肃', '慵懒', '俏皮', '深沉', '干练', '凌厉'],
  音色定位: ['磁性', '醇厚', '清亮', '空灵', '稚嫩', '苍老', '甜美', '沙哑', '醇雅'],
  人设腔调: ['夹子音', '御姐音', '正太音', '大叔音', '台湾腔'],
  方言: ['东北话', '四川话', '河南话', '粤语'],
  角色扮演: ['孙悟空', '林黛玉'],
  唱歌: ['唱歌'],
};

/** 可插在文本任意位置的细粒度音频标签 */
const AUDIO_TAGS = {
  语速节奏: ['吸气', '深呼吸', '叹气', '长叹一口气', '喘息', '屏息'],
  情绪状态: ['紧张', '害怕', '激动', '疲惫', '委屈', '撒娇', '心虚', '震惊', '不耐烦'],
  语音特征: ['颤抖', '声音颤抖', '变调', '破音', '鼻音', '气声', '沙哑'],
  哭笑表达: ['笑', '轻笑', '大笑', '冷笑', '抽泣', '呜咽', '哽咽', '嚎啕大哭'],
};

/** wav 实测参数：24kHz / 16bit / 单声道 */
const SAMPLE_RATE = 24000;
const BITS = 16;
const CHANNELS = 1;

// ─────────────────────────── CLI 解析 ───────────────────────────

function parseArgs(argv) {
  const o = { positional: [], format: 'wav', voice: '冰糖', speed: 1, quiet: false };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    const next = () => argv[++i];
    switch (a) {
      case '-h': case '--help': o.help = true; break;
      case '-v': case '--version': o.version = true; break;
      case '--list-voices': o.listVoices = true; break;
      case '--list-styles': o.listStyles = true; break;
      case '-o': case '--out': o.out = next(); break;
      case '--voice': o.voice = next(); break;
      case '--format': case '-f': o.format = (next() || '').toLowerCase(); break;
      case '--style': o.style = next(); break;
      case '--instruct': o.instruct = next(); break;
      case '--model': o.model = next(); break;
      case '--design': o.design = next(); break;
      case '--text': o.text = next(); break;
      case '--clone': o.clone = next(); break;
      case '--speed': o.speed = Number(next()); break;
      case '--optimize': o.optimize = true; break;
      case '--stream': o.stream = true; break;
      case '--base-url': o.baseUrl = next(); break;
      case '--api-key': o.apiKey = next(); break;
      case '--timeout': o.timeout = Number(next()) * 1000; break;
      case '-q': case '--quiet': o.quiet = true; break;
      default:
        if (a.startsWith('--')) fail(`未知参数：${a}（用 --help 查看用法）`);
        o.positional.push(a);
    }
  }
  return o;
}

function fail(msg) {
  console.error(`✗ ${msg}`);
  process.exit(1);
}

const HELP = `
mimo-tts — 小米 MiMo 文字转语音小工具（Token Plan / 按量付费均可）

用法:
  node mimo-tts.mjs <文本> [选项]
  node mimo-tts.mjs --design "<音色描述>" --text "<要朗读的文本>" [选项]
  node mimo-tts.mjs "<文本>" --clone <样本音频> [选项]
  <其他命令> | node mimo-tts.mjs [选项]

三种模式:
  （默认）            预置音色朗读，支持风格标签与唱歌
  --design "<描述>"   用文字描述现场设计一个音色（无需样本）
  --clone <文件>      用 mp3/wav 样本复刻音色

常用选项:
  -o, --out <文件>      输出路径（默认 tts-<时间戳>.<format>）
  -f, --format <格式>   wav | mp3 | pcm16  （默认 wav）
      --voice <音色>    预置音色 ID（默认 冰糖），用 --list-voices 查看
      --style <风格>    整体风格标签，如 东北话 / 粤语 / 唱歌 / 慵懒 / 御姐音
      --instruct "<指令>" 自然语言风格指令，如 "语速稍快，声音明亮有活力"
      --speed <倍数>    变速 0.5–2.0（本地处理，不额外计费）
      --optimize        智能润色文本（仅 voicedesign 模型支持）
      --stream          流式请求（更快开始返回，输出仍拼接为完整文件）
      --base-url <URL>  接口地址（默认 Token Plan CN 端点）
      --api-key <KEY>   直接指定密钥（默认读 ~/.dsh/.credentials.yaml）
      --timeout <秒>    超时（默认 120）
  -q, --quiet           静默，只输出文件路径

查询:
      --list-voices     列出预置音色
      --list-styles     列出全部风格 / 音频标签
  -h, --help            显示本帮助
  -v, --version         显示版本

风格标签:
  写在文本最前面，如:  (东北话)哎呀妈呀，这天儿也忒冷了！
  可叠加:              (慵懒 磁性)夜已经深了……
  唱歌必须加标签:       (唱歌)原谅我这一生不羁放纵爱自由
  细粒度标签可插在任意位置:
                      （紧张，深呼吸）呼……冷静，冷静。（语速加快）我可以的。

示例:
  node mimo-tts.mjs "你好，世界"
  node mimo-tts.mjs "(东北话)哎呀妈呀，这天儿也忒冷了吧！" -o dongbei.wav
  node mimo-tts.mjs "(唱歌)原谅我这一生不羁放纵爱自由" --voice 茉莉 -o song.mp3 -f mp3
  node mimo-tts.mjs "报告已完成" --instruct "轻快上扬，语速稍快" --voice 苏打
  node mimo-tts.mjs --design "低沉磁性的中年男声，语速偏慢" --text "晚上好，欢迎收听"
  node mimo-tts.mjs "用这个声音说话" --clone ./my-voice.wav

计费: TTS 系列限时免费；ASR 约 ¥0.5/小时。
`;

// ─────────────────────────── 密钥解析 ───────────────────────────

function resolveKey(explicit) {
  if (explicit) return explicit;
  if (process.env.XIAOMI_TOKEN_PLAN_CN_API_KEY) return process.env.XIAOMI_TOKEN_PLAN_CN_API_KEY;
  if (process.env.MIMO_API_KEY) return process.env.MIMO_API_KEY;

  try {
    const text = readFileSync(CREDENTIALS, 'utf8');
    const m = text.match(/^\s*([A-Z0-9_]*MIMO[A-Z0-9_]*|[A-Z0-9_]*XIAOMI[A-Z0-9_]*)\s*:\s*(\S+)\s*$/m);
    if (m) return m[2].replace(/^["']|["']$/g, '');
  } catch { /* 文件不存在就继续 */ }

  fail(
    '找不到 API Key。三种给法：\n' +
    '  1) 写进 ~/.dsh/.credentials.yaml 的 refs: 段（推荐，本工具自动读取）\n' +
    '  2) 设置环境变量 XIAOMI_TOKEN_PLAN_CN_API_KEY\n' +
    '  3) 用 --api-key tp-xxxxx 显式传入',
  );
}

// ─────────────────────────── 音频处理 ───────────────────────────

/** 把 16bit 单声道 PCM 包成 wav */
function pcmToWav(pcm) {
  const byteRate = SAMPLE_RATE * CHANNELS * BITS / 8;
  const blockAlign = CHANNELS * BITS / 8;
  const h = Buffer.alloc(44);
  h.write('RIFF', 0, 'latin1');
  h.writeUInt32LE(36 + pcm.length, 4);
  h.write('WAVE', 8, 'latin1');
  h.write('fmt ', 12, 'latin1');
  h.writeUInt32LE(16, 16);          // fmt chunk 大小
  h.writeUInt16LE(1, 20);           // PCM
  h.writeUInt16LE(CHANNELS, 22);
  h.writeUInt32LE(SAMPLE_RATE, 24);
  h.writeUInt32LE(byteRate, 28);
  h.writeUInt16LE(blockAlign, 32);
  h.writeUInt16LE(BITS, 34);
  h.write('data', 36, 'latin1');
  h.writeUInt32LE(pcm.length, 40);
  return Buffer.concat([h, pcm]);
}

/** 从 wav 里抽出裸 PCM（跳过头部），顺便读出格式做校验 */
function wavToPcm(wav) {
  if (wav.toString('latin1', 0, 4) !== 'RIFF') return { pcm: wav, fmt: null };
  const fmt = {
    channels: wav.readUInt16LE(22),
    sampleRate: wav.readUInt32LE(24),
    bits: wav.readUInt16LE(34),
  };
  let off = 12;
  while (off < wav.length - 8) {
    const id = wav.toString('latin1', off, off + 4);
    const size = wav.readUInt32LE(off + 4);
    if (id === 'data') return { pcm: wav.subarray(off + 8, off + 8 + size), fmt };
    off += 8 + size + (size % 2);
  }
  return { pcm: Buffer.alloc(0), fmt };
}

/** 线性重采样（够用即可，避免引入依赖） */
function resample(pcm, from, to) {
  if (from === to) return pcm;
  const inN = Math.floor(pcm.length / 2);
  const outN = Math.floor(inN * to / from);
  const out = Buffer.alloc(outN * 2);
  for (let i = 0; i < outN; i++) {
    const src = i * from / to;
    const i0 = Math.floor(src);
    const i1 = Math.min(i0 + 1, inN - 1);
    const frac = src - i0;
    const s0 = pcm.readInt16LE(i0 * 2);
    const s1 = pcm.readInt16LE(i1 * 2);
    let v = Math.round(s0 + (s1 - s0) * frac);
    if (v > 32767) v = 32767; else if (v < -32768) v = -32768;
    out.writeInt16LE(v, i * 2);
  }
  return out;
}

/** 按倍数变速：先改采样率再重采样回标准率，音高会跟着变（符合直觉） */
function changeSpeed(pcm, speed) {
  if (speed === 1) return pcm;
  return resample(pcm, Math.round(SAMPLE_RATE * speed), SAMPLE_RATE);
}

// ─────────────────────────── 请求 ───────────────────────────

function buildRequest(opts, text, key) {
  const audio = { format: opts.format === 'mp3' ? 'mp3' : 'pcm16' };
  const messages = [];

  if (opts.mode === 'design') {
    // voicedesign：user 放音色描述（必填），assistant 放待朗读文本
    messages.push({ role: 'user', content: opts.design });
    if (text) messages.push({ role: 'assistant', content: text });
    if (opts.optimize) audio.optimize_text_preview = true;
    if (!text && !opts.optimize) fail('voicedesign 模式要么给 --text，要么加 --optimize 让模型自己生成文本');
  } else if (opts.mode === 'clone') {
    audio.voice = opts.cloneB64;   // 样本 base64
    messages.push({ role: 'assistant', content: text });
  } else {
    audio.voice = opts.voice;
    if (opts.instruct) messages.push({ role: 'user', content: opts.instruct });
    messages.push({ role: 'assistant', content: text });
  }

  const body = { model: opts.model, messages, audio };
  if (opts.stream) body.stream = true;
  return body;
}

async function request(body, opts, key) {
  const url = `${opts.baseUrl}/chat/completions`;
  const ctl = new AbortController();
  const timer = setTimeout(() => ctl.abort(), opts.timeout ?? 120000);
  try {
    const r = await fetch(url, {
      method: 'POST',
      headers: { 'content-type': 'application/json', authorization: `Bearer ${key}` },
      body: JSON.stringify(body),
      signal: ctl.signal,
    });
    if (!r.ok) {
      const t = await r.text();
      let hint = '';
      if (r.status === 401) hint = '\n  → API Key 无效或已过期';
      else if (/assistant role/i.test(t)) hint = '\n  → 待朗读文本必须放在 assistant 角色（本工具已处理，若仍报错请反馈）';
      else if (/system role/i.test(t)) hint = '\n  → TTS 不接受 system 消息';
      else if (/voice/i.test(t)) hint = '\n  → 音色 ID 无效，用 --list-voices 查看可用值';
      fail(`接口返回 HTTP ${r.status}：${t.slice(0, 400)}${hint}`);
    }
    return r;
  } catch (e) {
    if (e.name === 'AbortError') fail(`请求超时（${(opts.timeout ?? 120000) / 1000}s）`);
    fail(`请求失败：${e.message}${e.cause ? ` (${e.cause.message})` : ''}`);
  } finally {
    clearTimeout(timer);
  }
}

/** 非流式：一次性拿完整 base64 */
async function fetchFull(body, opts, key) {
  const r = await request(body, opts, key);
  const j = await r.json();
  const msg = j.choices?.[0]?.message;
  const b64 = msg?.audio?.data;
  if (!b64) fail(`响应里没有音频数据。原始响应：${JSON.stringify(j).slice(0, 400)}`);
  return {
    buf: Buffer.from(b64, 'base64'),
    usage: j.usage,
    preview: msg?.final_text_preview,
  };
}

/** 流式：SSE 增量拿 base64 分片再拼接 */
async function fetchStream(body, opts, key) {
  const r = await request(body, opts, key);
  const reader = r.body.getReader();
  const dec = new TextDecoder();
  let rest = '', b64 = '', preview;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    rest += dec.decode(value, { stream: true });
    const lines = rest.split('\n');
    rest = lines.pop() ?? '';
    for (const raw of lines) {
      const t = raw.trim();
      if (!t.startsWith('data:')) continue;
      const p = t.slice(5).trim();
      if (!p || p === '[DONE]') continue;
      try {
        const ev = JSON.parse(p);
        const d = ev.choices?.[0]?.delta;
        if (d?.audio?.data) b64 += d.audio.data;
        if (d?.final_text_preview) preview = d.final_text_preview;
      } catch { /* 忽略不完整行 */ }
    }
  }
  if (!b64) fail('流式响应里没有拿到音频数据');
  return { buf: Buffer.from(b64, 'base64'), usage: undefined, preview };
}

// ─────────────────────────── 输出 ───────────────────────────

async function readStdin() {
  if (process.stdin.isTTY) return '';
  const chunks = [];
  for await (const c of process.stdin) chunks.push(c);
  return Buffer.concat(chunks).toString('utf8').trim();
}

function timestamp() {
  const d = new Date();
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}-${p(d.getHours())}${p(d.getMinutes())}${p(d.getSeconds())}`;
}

function listVoices() {
  console.log('预置音色（mimo-v2.5-tts）：\n');
  console.log('  ' + 'Voice ID'.padEnd(16) + '名称'.padEnd(12) + '语言'.padEnd(8) + '性别');
  console.log('  ' + '─'.repeat(46));
  for (const v of VOICES) {
    console.log('  ' + v.id.padEnd(16) + v.label.padEnd(12) + v.lang.padEnd(8) + v.gender + (v.note ? `   (${v.note})` : ''));
  }
  console.log('\n用法：--voice 茉莉   或   --voice Mia');
  console.log('注：voiceclone 用 --clone 传样本；voicedesign 用 --design 传描述，不用 --voice。');
}

function listStyles() {
  console.log('整体风格标签 —— 写在文本最前面，形如 (标签)文本\n');
  for (const [group, tags] of Object.entries(STYLE_TAGS)) {
    console.log(`  ${group}`);
    console.log(`    ${tags.join('、')}`);
  }
  console.log('\n细粒度音频标签 —— 插在文本任意位置，形如 （标签）或 [标签]\n');
  for (const [group, tags] of Object.entries(AUDIO_TAGS)) {
    console.log(`  ${group}`);
    console.log(`    ${tags.join('、')}`);
  }
  console.log('\n括号可用半角 () / 全角（）/ 方括号 []，效果相同。');
  console.log('多个风格可写在同一对括号里，如 (慵懒 磁性)。');
  console.log('唱歌必须在最前面加 (唱歌)，歌词建议用中文。');
  console.log('未列出的自定义风格同样支持。');
}

// ─────────────────────────── 主流程 ───────────────────────────

async function main() {
  const opts = parseArgs(process.argv.slice(2));

  if (opts.help) return void console.log(HELP.trim());
  if (opts.version) return void console.log('mimo-tts 1.0.0');
  if (opts.listVoices) return listVoices();
  if (opts.listStyles) return listStyles();

  opts.baseUrl = (opts.baseUrl ?? process.env.MIMO_BASE_URL ?? DEFAULT_BASE).replace(/\/+$/, '');
  opts.timeout ??= 120000;

  // 校验格式
  const fmt = opts.format === 'pcm' ? 'pcm16' : opts.format;
  if (!['wav', 'mp3', 'pcm16'].includes(fmt)) fail(`不支持的格式 "${opts.format}"，可选 wav / mp3 / pcm16`);
  opts.format = fmt;

  if (!(opts.speed > 0)) fail('--speed 必须是正数');
  if (opts.speed < 0.5 || opts.speed > 2) fail('--speed 建议在 0.5–2.0 之间');

  // 决定模式
  if (opts.model) {
    const known = Object.values(MODELS).includes(opts.model);
    if (!known && !opts.design && !opts.clone) fail(`未知的 --model "${opts.model}"`);
  }
  if (opts.design) { opts.mode = 'design'; opts.model ??= MODELS.design; }
  else if (opts.clone) { opts.mode = 'clone'; opts.model ??= MODELS.clone; }
  else { opts.mode = 'tts'; opts.model ??= MODELS.tts; }

  // 风格标签：--style 拼到文本前面
  let text = opts.positional.join(' ').trim();
  if (!text) text = await readStdin();
  if (opts.text) text = opts.text;

  if (opts.mode === 'design') {
    if (!opts.design.trim()) fail('--design 需要一段音色描述');
  } else if (opts.mode === 'clone') {
    if (!opts.clone) fail('--clone 需要样本音频路径');
  } else if (!text) {
    fail('没有文本。用法：node mimo-tts.mjs "要朗读的文字"（或用管道传入，或 --help 看帮助）');
  }

  if (opts.style && opts.mode !== 'design') {
    const tag = opts.style.trim().replace(/^[(（[【]\s*|\s*[)）\]】]$/g, '');
    text = `(${tag})${text}`;
  }

  // clone：读样本并转 base64
  if (opts.mode === 'clone') {
    let sample;
    try { sample = readFileSync(opts.clone); }
    catch (e) { fail(`读不到样本文件 ${opts.clone}：${e.message}`); }
    const ext = extname(opts.clone).toLowerCase();
    if (!['.mp3', '.wav'].includes(ext)) fail('样本只支持 .mp3 或 .wav');
    if (sample.length > 8 * 1024 * 1024) fail(`样本过大（${(sample.length / 1048576).toFixed(1)}MB），建议 10 秒以内的短样本`);
    // 服务端要求 DataURL（"audio.voice must be a DataURL for voice clone model"），不是裸 base64
    const mime = ext === '.mp3' ? 'audio/mpeg' : 'audio/wav';
    opts.cloneB64 = `data:${mime};base64,${sample.toString('base64')}`;
  }

  const key = resolveKey(opts.apiKey);
  if (!/^tp-|^sk-/.test(key)) {
    if (!opts.quiet) console.error('⚠ Key 格式不是常见的 tp-/sk- 开头，仍将尝试。');
  }

  const body = buildRequest(opts, text, key);

  if (!opts.quiet) {
    const label = { tts: '预置音色', design: '音色设计', clone: '音色复刻' }[opts.mode];
    const who = opts.mode === 'clone' ? basename(opts.clone)
      : opts.mode === 'design' ? '（描述生成）'
      : opts.voice;
    console.error(`→ ${label} | 模型 ${body.model} | 音色 ${who} | 格式 ${opts.format}${opts.stream ? ' | 流式' : ''}`);
    console.error(`  文本 ${text.length} 字：${text.slice(0, 60)}${text.length > 60 ? '…' : ''}`);
  }

  const t0 = Date.now();
  const got = opts.stream ? await fetchStream(body, opts, key) : await fetchFull(body, opts, key);
  const ms = Date.now() - t0;

  if (got.preview && !opts.quiet) console.error(`  润色后文本：${got.preview.slice(0, 80)}${got.preview.length > 80 ? '…' : ''}`);

  // 组装输出
  let out;
  if (opts.format === 'mp3') {
    out = got.buf;                                   // mp3 原样
    if (out.toString('latin1', 0, 3) !== 'ID3' && (out[0] !== 0xff || (out[1] & 0xe0) !== 0xe0)) {
      fail('返回的数据不像 MP3，可能该端点不支持 mp3，请改用 wav');
    }
  } else {
    // wav / pcm16：服务端给的是裸 PCM，本地补头
    let { pcm, fmt: wfmt } = wavToPcm(got.buf);
    if (pcm.length === 0) pcm = got.buf;
    const sr = wfmt?.sampleRate ?? SAMPLE_RATE;
    if (opts.speed !== 1) {
      if (!opts.quiet) console.error(`  变速 ${opts.speed}× …`);
      pcm = changeSpeed(pcm, opts.speed);
    }
    out = opts.format === 'pcm16' ? pcm : pcmToWav(resample(pcm, SAMPLE_RATE, sr));
  }

  const outPath = opts.out ?? `tts-${timestamp()}.${opts.format === 'pcm16' ? 'pcm' : opts.format}`;
  try { writeFileSync(outPath, out); }
  catch (e) { fail(`写文件失败 ${outPath}：${e.message}`); }

  const secs = opts.format === 'mp3'
    ? '—'
    : ((out.length - 44) / (SAMPLE_RATE * CHANNELS * BITS / 8)).toFixed(2);

  if (opts.quiet) {
    console.log(outPath);
  } else {
    console.error(`✓ 完成：${outPath}`);
    console.error(`  ${(out.length / 1024).toFixed(1)} KB${secs !== '—' ? ` | 约 ${secs} 秒` : ''} | 耗时 ${(ms / 1000).toFixed(1)}s | ${SAMPLE_RATE}Hz/16bit/单声道`);
    if (got.usage) console.error(`  tokens: 输入 ${got.usage.prompt_tokens} / 输出 ${got.usage.completion_tokens}`);
    console.error(`  （TTS 系列限时免费，本次不额外计费）`);
    console.log(outPath);
  }
}

main().catch((e) => fail(e?.stack ?? String(e)));
