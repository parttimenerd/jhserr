# HotSpot Error Report (hs_err) Grammars

[![Build](https://github.com/parttimenerd/jhserr/actions/workflows/ci.yml/badge.svg)](https://github.com/parttimenerd/jhserr/actions/workflows/ci.yml)

Syntax highlighting for HotSpot crash report files (`hs_err_pid*.log`), covering
signals, threads, stack frames, registers (x86/ARM/PPC/s390x), heap, GC, NMT,
and all other sections found in real hs_err files.

| Format | File | Lines |
|--------|------|------:|
| TextMate / VS Code / shiki | [`textmate/hserr.tmLanguage.json`](textmate/hserr.tmLanguage.json) | 1127 |
| highlight.js | [`highlightjs/hserr.js`](highlightjs/hserr.js) | 964 |
| CodeMirror 6 | [`codemirror/hserr.ts`](codemirror/hserr.ts) | 515 |

**Web links (hosted artifacts):**

- Index page: [parttimenerd.github.io/jhserr/grammars/index.html](https://parttimenerd.github.io/jhserr/grammars/index.html)
- TextMate / VS Code: [hserr.tmLanguage.json](https://parttimenerd.github.io/jhserr/grammars/hserr.tmLanguage.json)
- highlight.js: [hserr.js](https://parttimenerd.github.io/jhserr/grammars/hserr.js)
- CodeMirror 6: [hserr-codemirror.ts](https://parttimenerd.github.io/jhserr/grammars/hserr-codemirror.ts)
- YAML source spec: [hserr.grammar.yaml](https://parttimenerd.github.io/jhserr/grammars/hserr.grammar.yaml)

## VS Code Extension

The VS Code extension packaging now lives in `../vscode-plugin`.
The extension copies `textmate/hserr.tmLanguage.json` from this folder at build time.

### Installation

Install from a VSIX (grab the latest from [CI artifacts](https://github.com/parttimenerd/jhserr/actions) or build locally):

```bash
code --install-extension hserr-syntax-*.vsix
```

### Building from Source

```bash
cd ../vscode-plugin
npm install
npm run compile
npx @vscode/vsce package
```

### Features

- Auto-detection of `hs_err_pid*.log`, `hs_err*.log`, `hserr*.log`, and `replay_pid*.log`
- Section folding (fold at `--- T H R E A D ---` banners)
- Word selection includes hex addresses (`0x00007f...`) and Java identifiers (`java.lang.Thread$State`)
- Works with Dark+, Light+, Monokai, Solarized, and all standard themes

### Releasing a New Version

1. Update the version in `package.json`
2. Update `CHANGELOG.md` with the new version and changes
3. Commit, push, and tag (e.g. `git tag v0.1.0`)
4. The CI will build and upload the VSIX artifact

## highlight.js

```js
// Node.js
import hljs from 'highlight.js/lib/core';
import hserr from './highlightjs/hserr.js';
hljs.registerLanguage('hserr', hserr);
```

```html
<!-- Browser: auto-registers if hljs is a global -->
<script src="highlight.min.js"></script>
<script src="hserr.js"></script>
```

## CodeMirror 6

```ts
import { StreamLanguage } from '@codemirror/language';
import { hserr } from './codemirror/hserr';
const lang = StreamLanguage.define(hserr);
```

## Coverage

45+ contexts covering every section of an hs_err file:

- **Header** — signal names, error types, library references, source locations, URLs
- **Summary** — command line, host, time
- **Thread** — current thread, 6 frame types (V/v/C/J/j/A), registers, siginfo, stack bounds, top of stack, instructions, register-to-memory mapping, lock stack, compile task
- **Process** — thread lists, SMR info, event logs, dynamic libraries, VM arguments, global flags, environment variables, signal handlers, heap, heap regions, card table, marking bits, metaspace, code cache, GC precious log, NMT, compiler memory, internal statistics, logging, release file, locale, VM mutex, JFR settings
- **System** — OS info, CPU info, memory info, vm_info
- **500+ keywords** — 38 signals, 18 thread types, 10 thread states, 280+ register names across 4 architectures

## Support, Feedback, Contributing

This project is open to feature requests, bug reports, and contributions
via [GitHub Issues](https://github.com/parttimenerd/jhserr/issues).

## License

MIT