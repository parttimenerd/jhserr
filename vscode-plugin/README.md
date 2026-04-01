# HotSpot Error Report (hs_err) for VS Code

Syntax highlighting for HotSpot crash report files such as `hs_err_pid*.log` to let you read and navigate them more easily. The grammar is tuned for real hs_err files, covering signals, threads, stack frames, registers (x86/ARM/PPC/s390x), heap, GC, NMT, and all other sections found in real hs_err files.

This extension is part of the main `jhserr` project:
https://github.com/parttimenerd/jhserr

## Install

Install from VS Code Marketplace:
https://marketplace.visualstudio.com/items?itemName=bechberger.hserr-syntax

## What You Get

**Syntax highlighting** tuned for real `hs_err` files — everything is colour-coded so
you can scan a crash report at a glance:

| Element | Examples |
|---|---|
| Section banners | `---  T H R E A D  ---`, `---  P R O C E S S  ---` |
| Subsection headings | `Registers:`, `Heap:`, `Native frames:`, `Events (20 events):` |
| Crash signals & error types | `SIGSEGV`, `Internal Error`, `Out of Memory Error` |
| Stack frames | `V` / `C` / `J` / `j` / `v` frame types, compile tier (`c1` / `c2`) |
| Java class & method names | `java.lang.Thread.run`, `LambdaForm$MH+0x…` |
| C++ symbols | `ShenandoahHeap::allocate_memory`, `os::Linux::ucontext_get_pc` |
| Shared libraries | `libjvm.so`, `libpthread.so.0` |
| Addresses & hex values | `0x00007fff…`, register values, hex dumps |
| Registers | `RAX`, `RBX`, `x0`–`x28`, `r0`–`r31`, etc. |
| Thread names & states | `"main"`, `_thread_in_vm`, `_thread_blocked` |
| VM flags & options | `-XX:+UseG1GC`, `-Xmx384M`, `-Djava.io.tmpdir=…` |
| GC, NMT, event-log entries | `nmethod`, `Loading`, `Unloading`, `DEOPT`, GC phase timings |
| Assertions & fatal errors | `assert(…) failed:`, `guarantee(…) failed:` |
| Paths, URLs, versions | file paths, `https://…`, JRE/JVM version strings |
| Numeric literals | integers, floats, sizes (`2048K`), timestamps |

**Navigation** — the Outline / Breadcrumb bar shows every section and
subsection so you can jump straight to Registers, Heap, Event logs, etc.

**Folding** — collapse sections, subsections, the `#` header block, and
code-blob disassembly blocks.

**Auto-detection** — files with non-standard names (not `hs_err_pid*.log`)
are automatically recognised as hs_err if they contain the characteristic
header and section banners.

**File icons** for common hs_err filenames.

## Screenshot

TODO: add screenshot of the extension highlighting a real `hs_err_pid*.log` file.

## Build and Package (local)

Marketplace page:
https://marketplace.visualstudio.com/items?itemName=bechberger.hserr-syntax

```bash
cd vscode-plugin
npm install
npm run compile
npm run package
```

Install the generated VSIX locally:

```bash
code --install-extension hserr-syntax-*.vsix
```

## Related: Online hs_err Redaction Tool

Use the browser-based parser/redaction tool here:
https://parttimenerd.github.io/jhserr/

## Support, Feedback, Contributing

This project is open to feature requests/suggestions, bug reports etc. via <a href="https://github.com/parttimenerd/jhserr/issues">GitHub issues</a>. Contribution and feedback are encouraged and always welcome.

## License

MIT, Copyright 2026 SAP SE or an SAP affiliate company, Johannes Bechberger and contributors