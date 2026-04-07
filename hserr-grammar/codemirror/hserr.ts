/*
 * CodeMirror StreamParser grammar for HotSpot Error Report (hs_err) files.
 * Generated from hserr.grammar.yaml — the universal grammar spec.
 *
 * Uses CodeMirror 6 StreamLanguage with highlight tags mapping:
 *   constant.numeric.*     → tags.number
 *   constant.language.*    → tags.literal
 *   string.*               → tags.string
 *   comment.*              → tags.comment
 *   keyword.*              → tags.keyword
 *   entity.name.function.* → tags.function(tags.name)
 *   entity.name.type.*     → tags.typeName
 *   entity.name.tag.*      → tags.tagName
 *   entity.name.library.*  → tags.className
 *   variable.*             → tags.variableName
 *   markup.heading.1       → tags.heading1
 *   markup.heading.2       → tags.heading2
 *   markup.underline.link  → tags.link
 *   storage.type.*         → tags.typeName
 *   invalid.*              → tags.invalid
 *
 * Usage:
 *   import { StreamLanguage } from '@codemirror/language';
 *   import { hserr } from './hserr';
 *   const lang = StreamLanguage.define(hserr);
 */

import { tags as t } from '@lezer/highlight';

// ---------------------------------------------------------------------------
// Keyword lists (from YAML keywords section)
// ---------------------------------------------------------------------------

const SIGNALS_RE = /\b(?:SIGSEGV|SIGBUS|SIGFPE|SIGILL|SIGTRAP|SIGABRT|SIGTERM|SIGINT|SIGHUP|SIGQUIT|SIGPOLL|SIGKILL|SIGUSR1|SIGUSR2|SIGALRM|SIGCHLD|SIGCONT|SIGSTOP|SIGTSTP|SIGTTIN|SIGTTOU|SIGXCPU|SIGXFSZ|SIGPROF|EXCEPTION_ACCESS_VIOLATION|EXCEPTION_STACK_OVERFLOW|EXCEPTION_IN_PAGE_ERROR|EXCEPTION_INVALID_HANDLE|EXCEPTION_ARRAY_BOUNDS_EXCEEDED|EXCEPTION_FLT_DENORMAL_OPERAND|EXCEPTION_FLT_DIVIDE_BY_ZERO|EXCEPTION_FLT_INEXACT_RESULT|EXCEPTION_FLT_INVALID_OPERATION|EXCEPTION_FLT_OVERFLOW|EXCEPTION_FLT_UNDERFLOW|EXCEPTION_INT_DIVIDE_BY_ZERO|EXCEPTION_INT_OVERFLOW|EXCEPTION_DATATYPE_MISALIGNMENT)\b/;

const ERROR_TYPES_RE = /(?:Internal Error|Out of Memory|OutOfMemory|StackOverflow)/;
const ERROR_GENERIC_RE = /\b(?:\w+(?:Error|Exception|Failure|Violation|Overflow|Abort|Fault|Panic|Crash|Halt))\b/;

const THREAD_TYPES_RE = /\b(?:JavaThread|VMThread|CompilerThread|GCTaskThread|WatcherThread|ConcurrentGCThread|WorkerThread|CodeCacheSweeperThread|ServiceThread|NotificationThread|StringDedupThread|ParallelCompileThread|PeriodicalTaskThread|JfrRecorderThread|JfrThreadSampler|AttachListenerThread|TrimThread|ZMarkStackExpandThread)\b/;

const THREAD_STATES_RE = /\b(?:_thread_in_vm|_thread_in_native|_thread_in_Java|_thread_blocked|_thread_new|_thread_uninitialized|_thread_suspended|_thread_in_conc_gc|_thread_in_native_trans|_thread_in_vm_trans)\b/;

const FLAG_TYPES_RE = /\b(?:bool|int|uint|intx|uintx|uint64_t|size_t|double|ccstr|ccstrlist)\b/;

const GC_TYPES_RE = /\b(?:garbage-first heap|Shenandoah Heap|ZHeap|PSYoungGen|PSOldGen|ParOldGen|def new generation|tenured generation)\b/;
const CPU_ARCH_RE = /\b(?:AArch64|arm64|x86_64|amd64|x64|x86|ppc64le|ppc64|s390x|riscv64)\b/;

// Register names covering x86, ARM64, PPC, s390x
const REGISTER_RE = /(?:(?:R|E)?(?:AX|BX|CX|DX|SI|DI|BP|SP|IP)|R(?:8|9|10|11|12|13|14|15)[DWB]?|(?:XMM|YMM|ZMM)\d+|ST\d|EFLAGS|RFLAGS|CSGSFS|ERR|TRAPNO|FSBASE|GSBASE|CS|DS|SS|FS|GS|ES|x\d{1,2}|[xf]p|sp|pc|lr|msr|pstate|cpsr|[vf]\d{1,2}|r\d{1,2}|cr\d|ctr|xer|fpscr|psw|(?:fr|f)\d{1,2})/i;

const HEX_RE = /0x[0-9a-fA-F]{2,}/;
const MEMSIZE_RE = /\d+[KMG]B?/;
const FLOAT_RE = /\d+\.\d+/;

// ---------------------------------------------------------------------------
// Section detection helpers
// ---------------------------------------------------------------------------

type Section =
  | 'header' | 'summary' | 'thread' | 'process' | 'system'
  | 'frames' | 'registers' | 'reg_mapping' | 'top_of_stack' | 'instructions'
  | 'stack_slot' | 'lock_stack' | 'compile_task' | 'code_blob'
  | 'thread_list' | 'threads_smr' | 'threads_compile' | 'event_log'
  | 'dyn_libs' | 'vm_args' | 'global_flags' | 'env_vars' | 'signal_handlers'
  | 'heap' | 'heap_regions' | 'marking_bits' | 'metaspace' | 'code_cache'
  | 'gc_precious' | 'nmt' | 'compiler_mem' | 'internal_stats'
  | 'logging' | 'release_file' | 'active_locale' | 'vm_mutex' | 'jfr_settings'
  | 'os_info' | 'cpu_info'
  | null;

interface State {
  /** Current major section (header / summary / thread / process / system) */
  major: 'header' | 'summary' | 'thread' | 'process' | 'system' | null;
  /** Current sub-section within major */
  sub: Section;
  /** Whether we're inside a code_blob (between ---- lines) */
  inCodeBlob: boolean;
  /** Whether current line is a machine dump line: 0x...: ... */
  inHexDumpLine: boolean;
}

// ---------------------------------------------------------------------------
// Section banner detection
// ---------------------------------------------------------------------------

const BANNER_RE = /^-{10,}\s+[A-Z](?:\s+[A-Z])+\s+-{10,}$/;

function detectMajor(line: string): State['major'] | null {
  if (/S U M M A R Y/.test(line)) return 'summary';
  if (/T H R E A D/.test(line)) return 'thread';
  if (/P R O C E S S/.test(line)) return 'process';
  if (/S Y S T E M/.test(line)) return 'system';
  return null;
}

// Sub-section header patterns (process & thread & system sections)
const SUB_HEADERS: [RegExp, Section][] = [
  // Thread sub-sections
  [/^(?:Native|Java) frames:/, 'frames'],
  [/^Registers:\s*$/, 'registers'],
  [/^Register to memory mapping:\s*$/, 'reg_mapping'],
  [/^Top of Stack:/, 'top_of_stack'],
  [/^Instructions:/, 'instructions'],
  [/^Stack slot to memory mapping:\s*$/, 'stack_slot'],
  [/^Lock stack of current Java thread/, 'lock_stack'],
  [/^Current CompileTask:\s*$/, 'compile_task'],
  // Process sub-sections
  [/^(?:Java|Other) Threads:/, 'thread_list'],
  [/^Threads class SMR info:\s*$/, 'threads_smr'],
  [/^Threads with active compile tasks:\s*$/, 'threads_compile'],
  [/^\S.*\(\d+ events?\):\s*$/, 'event_log'],
  [/^Dynamic libraries:\s*$/, 'dyn_libs'],
  [/^VM Arguments:\s*$/, 'vm_args'],
  [/^\[Global flags\]\s*$/, 'global_flags'],
  [/^Environment Variables:\s*$/, 'env_vars'],
  [/^Signal Handlers:\s*$/, 'signal_handlers'],
  [/^Heap:\s*$/, 'heap'],
  [/^Heap Regions:\s*$/, 'heap_regions'],
  [/^Marking Bits:/, 'marking_bits'],
  [/^Metaspace\b/, 'metaspace'],
  [/^Code(?:Heap|Cache)\b/, 'code_cache'],
  [/^GC Precious Log:\s*$/, 'gc_precious'],
  [/^Native Memory Tracking:\s*$/, 'nmt'],
  [/^Compiler Memory Statistic/, 'compiler_mem'],
  [/^Internal statistics:\s*$/, 'internal_stats'],
  [/^Logging:\s*$/, 'logging'],
  [/^Release file:\s*$/, 'release_file'],
  [/^Active Locale:\s*$/, 'active_locale'],
  [/^VM Mutex\/Monitor/, 'vm_mutex'],
  [/^JFR settings:\s*$/, 'jfr_settings'],
  // System sub-sections
  [/^OS:\s*$/, 'os_info'],
  [/^CPU:/, 'cpu_info'],
];

// Sub-sections that terminate on blank lines
const BLANK_TERMINATES = new Set<Section>([
  'registers', 'reg_mapping', 'top_of_stack', 'instructions',
  'stack_slot', 'lock_stack', 'compile_task',
  'thread_list', 'threads_smr', 'threads_compile',
  'dyn_libs', 'vm_args', 'global_flags', 'env_vars', 'signal_handlers',
  'heap', 'heap_regions', 'marking_bits',
  'gc_precious', 'compiler_mem', 'internal_stats',
  'logging', 'release_file', 'active_locale', 'vm_mutex', 'jfr_settings',
  'cpu_info',
]);

// ---------------------------------------------------------------------------
// Token helpers
// ---------------------------------------------------------------------------

/** Try to match a regex at the current stream position. Returns match or null. */
function tryMatch(stream: any, re: RegExp): RegExpMatchArray | null {
  const match = stream.match(re, false);
  if (match) {
    stream.match(re);
  }
  return match;
}

/** Consume the rest of the line, returning a tag. */
function eatLine(stream: any, tag: any): any {
  stream.skipToEnd();
  return tag;
}

// ---------------------------------------------------------------------------
// Main token function
// ---------------------------------------------------------------------------

function tokenize(stream: any, state: State): any {
  const line = stream.string;
  const sol = stream.sol();

  // ---- Start of line detection ----
  if (sol) {
    state.inHexDumpLine = false;

    // END marker
    if (stream.match(/^END\.\s*$/)) {
      return t.keyword;
    }

    // Section banners
    if (BANNER_RE.test(line)) {
      const major = detectMajor(line);
      if (major) {
        state.major = major;
        state.sub = null;
      }
      return eatLine(stream, t.heading1);
    }

    // Code blob separators (80 dashes)
    if (/^-{80}$/.test(line)) {
      state.inCodeBlob = !state.inCodeBlob;
      return eatLine(stream, t.comment);
    }

    // Sub-section header detection
    if (!state.inCodeBlob) {
      for (const [re, sub] of SUB_HEADERS) {
        if (re.test(line)) {
          state.sub = sub;
          return eatLine(stream, t.heading2);
        }
      }
    }

    // Blank line → may terminate sub-section
    if (line.trim() === '') {
      if (state.sub && BLANK_TERMINATES.has(state.sub)) {
        state.sub = null;
      }
      stream.skipToEnd();
      return null;
    }

    // Header lines (# prefix)
    if (state.major === 'header' || (!state.major && line.startsWith('#'))) {
      if (!state.major) state.major = 'header';
      if (stream.match(/^#/)) {
        return t.punctuation;
      }
    }

    // Machine dump line start: optional indentation / arrow, then address and colon
    if (stream.match(/^\s*(?:=>\s*)?0x[0-9a-fA-F]+:/)) {
      state.inHexDumpLine = true;
      return t.number;
    }

    // Current thread line
    if (stream.match(/^Current thread/)) {
      return t.tagName;
    }

    // Stack bounds
    if (stream.match(/^Stack:/)) {
      return t.tagName;
    }

    // siginfo
    if (stream.match(/^siginfo:/)) {
      return t.tagName;
    }

    // Error during error
    if (/^\[error occurred during error reporting/.test(line)) {
      return eatLine(stream, t.invalid);
    }

    // Frame type prefix (V  , C  , v  , J , j , A )
    if (state.sub === 'frames' || state.major === 'thread') {
      if (stream.match(/^[VvCJjA]\s{1,2}/)) {
        return t.keyword;
      }
    }

    // => current thread marker in thread lists
    if (stream.match(/^=>/)) {
      return t.keyword;
    }

    // Event: timestamp in event logs
    if (state.sub === 'event_log' && stream.match(/^Event:/)) {
      return t.tagName;
    }

    // Event log continuation line for thrown paths/details
    if (state.sub === 'event_log' && stream.match(/^thrown\s+\[/)) {
      return t.tagName;
    }

    // No events
    if (state.sub === 'event_log' && stream.match(/^No events$/)) {
      return t.comment;
    }

    // Environment variable: NAME=value
    if (state.sub === 'env_vars' && stream.match(/^[A-Z_][A-Z0-9_]*(?==)/)) {
      return t.variableName;
    }

    // Active Locale: LC_*=value
    if (state.sub === 'active_locale' && stream.match(/^LC_\w+(?==)/)) {
      return t.variableName;
    }

    // Release file: KEY=
    if (state.sub === 'release_file' && stream.match(/^\w+(?==)/)) {
      return t.tagName;
    }

    // Linux /proc/self/maps permissions
    if (state.sub === 'dyn_libs' && stream.match(/^[0-9a-fA-F]+-[0-9a-fA-F]+/)) {
      return t.number;
    }

    // Process single-line items (key labels)
    if (state.major === 'process') {
      if (stream.match(/^(?:uid|euid|gid|egid|umask|VM state|Heap address|Compilation|CDS archive|Polling page|JVMTI agents|Preinit state|Periodic native trim|Encoding Range|Klass Range|Klass ID Range|Protection zone|MallocLimit|Decoder state|VM Error Count|Current thread is native thread|Sigstack overflow|Card table byte_map)(?::|\s)/)) {
        return t.tagName;
      }
    }

    // Summary key labels
    if (state.major === 'summary') {
      if (stream.match(/^(?:Command Line|Host|Time):/)) {
        return t.tagName;
      }
    }

    // OS info sub-labels
    if (state.sub === 'os_info') {
      if (stream.match(/^(?:uname|load average):/)) {
        return t.tagName;
      }
    }

    // Global flags: flag type at start
    if (state.sub === 'global_flags') {
      if (stream.match(FLAG_TYPES_RE)) {
        return t.typeName;
      }
    }

    // Signal handler line: SIGNAME:
    if (state.sub === 'signal_handlers') {
      const m = line.match(/^\s+((?:SIG|EXCEPTION_)[A-Z_]+):/);
      if (m) {
        stream.match(/^\s+/);
        if (stream.match(/(?:SIG|EXCEPTION_)[A-Z_]+/)) {
          return t.function(t.name);
        }
      }
    }

    // vm_info / Memory single-line
    if (stream.match(/^(?:vm_info|Memory):/)) {
      return t.heading2;
    }

    // Generic subsection header (Foo:  at start of line, alone)
    if (/^\S[^:]*:\s*$/.test(line)) {
      return eatLine(stream, t.heading2);
    }
  }

  // ---- Inline token matching (not at SOL or after SOL prefix consumed) ----

  // Skip whitespace
  if (stream.eatSpace()) {
    return null;
  }

  // Hex dump line payload: keep words consistently tokenized as hex chunks
  if (state.inHexDumpLine) {
    // Separator used in ARM dumps: " | "
    if (stream.match('|')) {
      return t.comment;
    }

    // Annotation payload after address: ; {metadata...}
    if (stream.match(/;.*/)) {
      return t.comment;
    }

    // Bare hex words (2..16 nybbles) in dump payload
    if (stream.match(/[0-9a-fA-F]{2,16}(?![0-9a-fA-F])/)) {
      return t.number;
    }
  }

  // Header comment body — special inner patterns
  if (state.major === 'header') {
    // Fatal error banner message
    if (stream.match(/A fatal error has been detected by the Java Runtime Environment/)) return t.keyword;
    // Core dump messages
    if (stream.match(/No core dump will be written/)) return t.keyword;
    if (stream.match(/Core dumps have been disabled/)) return t.keyword;
    // assert/guarantee internal error patterns
    if (stream.match(/(?:assert|guarantee)(?=\()/)) return t.keyword;
    if (stream.match(/failed:/)) return t.invalid;
    // Signal names
    if (stream.match(SIGNALS_RE))      return t.literal;
    // Error types
    if (stream.match(ERROR_TYPES_RE))  return t.literal;
    if (stream.match(ERROR_GENERIC_RE))return t.literal;
    // Header label tags
    if (stream.match(/JRE version:|Java VM:|Problematic frame:/)) return t.tagName;
    // OpenJDK product names
    if (stream.match(/OpenJDK (?:Runtime Environment|64-Bit (?:Server|Client) VM)/)) return t.typeName;
    // JVM vendor/distribution names
    if (stream.match(/\b(?:SapMachine|GraalVM|Temurin|Corretto|Zulu|Semeru|Dragonwell|Liberica|Mandrel|HotSpot)\b/)) return t.typeName;
    // Build type keywords
    if (stream.match(/\b(?:slowdebug|fastdebug|release|product|debug|optimized)\b/)) return t.keyword;
    // VM feature multi-word keywords
    if (stream.match(/(?:mixed mode|compressed oops|compressed class ptrs|compact obj headers|emulated-client)/)) return t.keyword;
    // VM feature single-word keywords
    if (stream.match(/\b(?:sharing|tiered|interpreted)\b/)) return t.keyword;
    // GC type references in VM info
    if (stream.match(/\b(?:g1|shenandoah|parallel|serial|epsilon|z) gc\b/i)) return t.typeName;
    // Platform (os-arch)
    if (stream.match(/\b(?:linux|bsd|windows|macosx|aix|solaris)-(?:aarch64|arm64|x86_64|amd64|x86|ppc64le|ppc64|s390x|riscv64)\b/)) return t.typeName;
    // Frame type code in problematic frame line (V, C, j, J, v, A followed by [lib...])
    if (stream.match(/[VvCJjA](?=\s{1,3}\[)/)) return t.keyword;
    // pc=, pid=, tid= labels
    if (stream.match(/\b(?:pc|pid|tid)=/)) return t.tagName;
    // 'at' keyword (before pc=)
    if (stream.match(/\bat\b/)) return t.keyword;
    // 'build' keyword (in JRE version line)
    if (stream.match(/\bbuild\b/)) return t.keyword;
    // LTS version tag
    if (stream.match(/\bLTS\b/)) return t.keyword;
    // Crash context messages
    if (stream.match(/The crash happened outside the Java Virtual Machine in native code/)) return t.keyword;
    if (stream.match(/See problematic frame for where to report the bug/)) return t.keyword;
    // URLs
    if (stream.match(/https?:\/\/[^\s]+/)) return t.link;
    // Library references [lib+0x...]
    if (stream.match(/\[[\w.-]+(?:\.(?:so|dylib|dll))?\+0x[0-9a-fA-F]+\]/)) return t.className;
    // Source file:line references in parens
    if (stream.match(/\([^)]+\.(?:cpp|hpp|c|h|java):\d+\)/)) return t.string;
    // Thread types
    if (stream.match(THREAD_TYPES_RE)) return t.typeName;
    // C/C++ type keywords (in native function signatures on problematic frame)
    if (stream.match(/\b(?:int|char|void|unsigned|signed|long|short|float|double|const|bool|size_t|uint\d+_t|int\d+_t)\b/)) return t.typeName;
    // Platform/compiler type identifiers (e.g. __siginfo)
    if (stream.match(/__\w+/)) return t.typeName;
  }

  // Register assignments (in registers block or register mapping)
  if (state.sub === 'registers' || state.sub === 'reg_mapping') {
    if (stream.match(REGISTER_RE)) {
      return t.variableName;
    }
  }

  // Summary: command-line option/value tokenization
  if (state.major === 'summary' && /^Command Line:/.test(line)) {
    if (stream.match(/-D[\w.$-]+(?==)/)) return t.tagName;
    if (stream.match(/=/)) return t.keyword;
    if (stream.match(/(?:-XX:[^\s]+|-X[a-zA-Z][^\s]*|-J-XX:[^\s]+|-J-X[^\s]*)/)) return t.keyword;
    if (stream.match(/(?:-ea|-esa|-da|-dsa|-J-ea|-J-esa|-J-da|-J-dsa)\b/)) return t.keyword;
    if (stream.match(/(?:[a-zA-Z_$][\w$]*\.)+[A-Za-z_$][\w$]*/)) return t.className;
    if (stream.match(/\/[\w./:@+-]+/)) return t.string;
  }

  // Summary: host architecture token
  if (state.major === 'summary' && /^Host:/.test(line)) {
    if (stream.match(CPU_ARCH_RE)) return t.typeName;
  }

  // Event log inline tokens (keep these before generic number/hex handlers)
  if (state.sub === 'event_log') {
    if (stream.match(/\b(?:Loading class|Unloading class|Internal exceptions|Exception|VM Operation|VM operation|Protecting memory|Thread added|Thread exited|JVMTI - enter|Uncommon trap:|DEOPT\s+(?:PACKING|UNPACKING)|Implicit null exception at|Concurrent\b|Pause\b|Control thread transition\b|GC heap before|GC heap after|nmethod|flushing nmethod|Cancelling GC:|code|done|to|invocations)\b/)) {
      return t.tagName;
    }
    if (stream.match(/\b(?:Before|After)\b(?=\s+GC\b)/)) {
      return t.tagName;
    }
    if (stream.match(/\b(?:heap|metaspace)\b/)) {
      return t.tagName;
    }
    if (stream.match(/\b(?:[!bns]|c[12])\b/)) {
      return t.keyword;
    }
    if (stream.match(/\b\d+%(?=\b)/)) {
      return t.number;
    }
    if (stream.match(/(?:[a-zA-Z_$][\w$]*\.)+[A-Za-z_$][\w$]*/)) {
      return t.className;
    }
    if (stream.match(/::/)) {
      return t.keyword;
    }
    if (stream.match(/[A-Za-z_$][\w$<>]*/)) {
      return t.function(t.name);
    }
    if (stream.match(/\[[^\]]+\]/)) {
      return t.string;
    }
    if (stream.match(/'[^']+'/)) {
      return t.className;
    }
  }

  // Hex addresses (global)
  if (stream.match(HEX_RE)) {
    return t.number;
  }

  // Memory sizes
  if (stream.match(MEMSIZE_RE)) {
    return t.number;
  }

  // Floats
  if (stream.match(FLOAT_RE)) {
    return t.number;
  }

  // Integers
  if (stream.match(/\d+/)) {
    return t.number;
  }

  // Thread types
  if (stream.match(THREAD_TYPES_RE)) {
    return t.typeName;
  }

  // Thread states
  if (stream.match(THREAD_STATES_RE)) {
    return t.literal;
  }

  // Signal names
  if (stream.match(SIGNALS_RE)) {
    return t.literal;
  }

  // GC types
  if (stream.match(GC_TYPES_RE)) {
    return t.typeName;
  }

  // Boolean
  if (stream.match(/\b(?:true|false)\b/)) {
    return t.literal;
  }

  // Compilation tier
  if (stream.match(/\bc[12]\b/)) {
    return t.keyword;
  }

  // daemon keyword
  if (stream.match(/\bdaemon\b/)) {
    return t.keyword;
  }

  // Linux permissions
  if (state.sub === 'dyn_libs' && stream.match(/[r-][w-][x-][ps-]/)) {
    return t.keyword;
  }

  // Quoted strings
  if (stream.peek() === '"') {
    stream.next(); // consume opening "
    while (!stream.eol()) {
      const ch = stream.next();
      if (ch === '"') break;
    }
    return t.string;
  }

  // Flag attributes {product} {ergonomic}
  if (state.sub === 'global_flags' && stream.peek() === '{') {
    stream.next();
    while (!stream.eol()) {
      const ch = stream.next();
      if (ch === '}') break;
    }
    return t.typeName;
  }

  // NMT category labels
  if (state.sub === 'nmt') {
    if (stream.match(/\b(?:reserved|committed)\b/)) return t.tagName;
  }

  // Metaspace labels
  if (state.sub === 'metaspace') {
    if (stream.match(/\b(?:used|committed|reserved)\b/)) return t.tagName;
  }

  // Code cache stat labels
  if (state.sub === 'code_cache') {
    if (stream.match(/(?:size|used|max_used|free|total_blobs|nmethods|adapters|full_count)(?==)/)) {
      return t.tagName;
    }
  }

  // rlimit in OS info
  if (state.sub === 'os_info' && stream.match(/rlimit/)) {
    return t.tagName;
  }

  // Library name in frames: [libname+0x...]
  if ((state.sub === 'frames' || state.major === 'thread') && stream.match(/\[[\w.-]+(?:\.(?:so|dylib|dll))?(?=\+)/)) {
    return t.className;
  }

  // Java method signatures: (...)V, (...)I, etc.
  if (stream.match(/\([^)]*\)[VZBCSIJFDL\[]\S*/)) {
    return t.typeName;
  }

  // Java class.method before signature
  if (stream.match(/[\w.$]+(?=\()/)) {
    return t.function(t.name);
  }

  // Module@version
  if (stream.match(/\S+@\S+/)) {
    return t.className;
  }

  // File paths
  if (stream.match(/\/[\w./-]+/)) {
    return t.string;
  }

  // Default: consume one character
  stream.next();
  return null;
}

// ---------------------------------------------------------------------------
// Export
// ---------------------------------------------------------------------------

export const hserr = {
  name: 'hserr',

  startState(): State {
    return {
      major: null,
      sub: null,
      inCodeBlob: false,
      inHexDumpLine: false,
    };
  },

  token: tokenize,

  languageData: {
    commentTokens: { line: '#' }
  }
};

export default hserr;
