/*
 * highlight.js grammar for HotSpot Error Report (hs_err) files.
 * Generated from hserr.grammar.yaml — the universal grammar spec.
 *
 * Scope mapping (TextMate → highlight.js):
 *   constant.numeric.*     → number
 *   constant.language.*    → literal
 *   string.*               → string
 *   comment.*              → comment
 *   keyword.*              → keyword
 *   entity.name.function.* → title.function
 *   entity.name.type.*     → type
 *   entity.name.tag.*      → attr
 *   entity.name.library.*  → title.class
 *   entity.name.namespace  → title.class
 *   variable.*             → variable
 *   variable.other.env.*   → variable
 *   markup.heading.*       → section
 *   markup.underline.link  → link
 *   storage.type.*         → type
 *   invalid.*              → deletion
 *   punctuation.*          → punctuation
 *
 * Usage:
 *   import hljs from 'highlight.js/lib/core';
 *   import hserr from './hserr';
 *   hljs.registerLanguage('hserr', hserr);
 */

/** @type {import('highlight.js').LanguageFn} */
const hserr = (hljs) => {

  // ---------------------------------------------------------------------------
  // Keyword lists (from YAML keywords section)
  // ---------------------------------------------------------------------------

  const SIGNALS = [
    'SIGSEGV','SIGBUS','SIGFPE','SIGILL','SIGTRAP','SIGABRT','SIGTERM','SIGINT',
    'SIGHUP','SIGQUIT','SIGPOLL','SIGKILL','SIGUSR1','SIGUSR2','SIGALRM','SIGCHLD',
    'SIGCONT','SIGSTOP','SIGTSTP','SIGTTIN','SIGTTOU','SIGXCPU','SIGXFSZ','SIGPROF',
    'EXCEPTION_ACCESS_VIOLATION','EXCEPTION_STACK_OVERFLOW','EXCEPTION_IN_PAGE_ERROR',
    'EXCEPTION_INVALID_HANDLE','EXCEPTION_ARRAY_BOUNDS_EXCEEDED',
    'EXCEPTION_FLT_DENORMAL_OPERAND','EXCEPTION_FLT_DIVIDE_BY_ZERO',
    'EXCEPTION_FLT_INEXACT_RESULT','EXCEPTION_FLT_INVALID_OPERATION',
    'EXCEPTION_FLT_OVERFLOW','EXCEPTION_FLT_UNDERFLOW',
    'EXCEPTION_INT_DIVIDE_BY_ZERO','EXCEPTION_INT_OVERFLOW',
    'EXCEPTION_DATATYPE_MISALIGNMENT'
  ];

  const THREAD_TYPES = [
    'JavaThread','VMThread','CompilerThread','GCTaskThread','WatcherThread',
    'ConcurrentGCThread','WorkerThread','CodeCacheSweeperThread','ServiceThread',
    'NotificationThread','StringDedupThread','ParallelCompileThread',
    'PeriodicalTaskThread','JfrRecorderThread','JfrThreadSampler',
    'AttachListenerThread','TrimThread','ZMarkStackExpandThread'
  ];

  const THREAD_STATES = [
    '_thread_in_vm','_thread_in_native','_thread_in_Java','_thread_blocked',
    '_thread_new','_thread_uninitialized','_thread_suspended',
    '_thread_in_conc_gc','_thread_in_native_trans','_thread_in_vm_trans'
  ];

  const FLAG_TYPES = [
    'bool','int','uint','intx','uintx','uint64_t','size_t','double','ccstr','ccstrlist'
  ];

  const GC_TYPES = [
    'garbage-first heap','Shenandoah Heap','ZHeap','PSYoungGen','PSOldGen',
    'ParOldGen','def new generation','tenured generation'
  ];

  const VM_STATES = ['not at safepoint', 'at safepoint', 'synchronizing'];

  const SIGNAL_RE = SIGNALS.join('|');
  const THREAD_TYPE_RE = THREAD_TYPES.join('|');
  const THREAD_STATE_RE = THREAD_STATES.join('|');
  const FLAG_TYPE_RE = FLAG_TYPES.join('|');

  // Register pattern covering x86, ARM64, PPC, s390x
  const REGISTER_RE =
    '(?:(?:R|E)?(?:AX|BX|CX|DX|SI|DI|BP|SP|IP)' +
    '|R(?:8|9|10|11|12|13|14|15)[DWB]?' +
    '|(?:XMM|YMM|ZMM)\\d+|ST\\d' +
    '|EFLAGS|RFLAGS|CSGSFS|ERR|TRAPNO|FSBASE|GSBASE' +
    '|CS|DS|SS|FS|GS|ES' +
    '|x\\d{1,2}|[xf]p|sp|pc|lr|msr|pstate|cpsr' +
    '|[vf]\\d{1,2}|r\\d{1,2}|cr\\d|ctr|xer|fpscr|psw' +
    '|(?:fr|f)\\d{1,2})';

  // ---------------------------------------------------------------------------
  // Re-usable sub-modes
  // ---------------------------------------------------------------------------

  const HEX_ADDRESS = {
    className: 'number',
    match: /(?<!\w)0x[0-9a-fA-F]{2,}(?!\w)/
  };

  const MEMORY_SIZE = {
    className: 'number',
    match: /\b\d+[KMG]B?\b/
  };

  const FLOAT_NUM = {
    className: 'number',
    match: /\b\d+\.\d+\b/
  };

  const INTEGER = {
    className: 'number',
    match: /(?<!\.)\b\d+\b(?![.KMGxX])/
  };

  const QUOTED_STRING = {
    className: 'string',
    begin: /"/,
    end: /"/,
    contains: []
  };

  const BOOLEAN = {
    className: 'literal',
    match: /\b(?:true|false)\b/
  };

  const THREAD_TYPE_MODE = {
    className: 'type',
    match: new RegExp('\\b(?:' + THREAD_TYPE_RE + ')\\b')
  };

  const THREAD_STATE_MODE = {
    className: 'literal',
    match: new RegExp('\\b(?:' + THREAD_STATE_RE + ')\\b')
  };

  const SIGNAL_MODE = {
    className: 'literal',
    match: new RegExp('\\b(?:' + SIGNAL_RE + ')\\b')
  };

  // Global patterns applied in most modes
  const GLOBAL_CONTAINS = [
    HEX_ADDRESS, MEMORY_SIZE, FLOAT_NUM, INTEGER,
    QUOTED_STRING, BOOLEAN, THREAD_TYPE_MODE, THREAD_STATE_MODE, SIGNAL_MODE
  ];

  // ---------------------------------------------------------------------------
  // Section banner (markup.heading.1)
  // ---------------------------------------------------------------------------
  const SECTION_BANNER = {
    className: 'section',
    match: /^-{10,}\s+[A-Z](?:\s+[A-Z])+\s+-{10,}$/
  };

  // ---------------------------------------------------------------------------
  // Header (# comment lines)
  // ---------------------------------------------------------------------------
  const HEADER = {
    className: 'comment',
    begin: /^#/,
    end: /$/,
    contains: [
      {
        className: 'literal',
        match: new RegExp('\\b(?:' + SIGNAL_RE + ')\\b')
      },
      {
        className: 'literal',
        match: /(?:Internal Error|Out of Memory|OutOfMemory|StackOverflow)/
      },
      {
        className: 'literal',
        match: /\b(?:\w+(?:Error|Exception|Failure|Violation|Overflow|Abort|Fault|Panic|Crash|Halt))\b/
      },
      {
        className: 'attr',
        match: /JRE version:|Java VM:|Problematic frame:/
      },
      {
        className: 'link',
        match: /https?:\/\/[^\s]+/
      },
      {
        className: 'number',
        match: /(?<!\w)0x[0-9a-fA-F]{2,}(?!\w)/
      },
      {
        className: 'number',
        match: /\b(?:pid|tid)=\d+/
      },
      {
        className: 'title.class',
        match: /\[[\w.-]+(?:\.(?:so|dylib|dll))?\+0x[0-9a-fA-F]+\]/
      },
      {
        className: 'string',
        match: /\([^)]+\.(?:cpp|hpp|c|h|java):\d+\)/
      },
      {
        // assert/guarantee(...) failed: message
        begin: /\b(?:assert|guarantee)/,
        beginCaptures: { 0: { className: 'keyword' } },
        end: /$/,
        contains: [
          {
            className: 'string',
            match: /\((?:[^()]|\((?:[^()]|\([^()]*\))*\))*\)/
          },
          { className: 'deletion', match: /\bfailed:/ },
          HEX_ADDRESS,
          INTEGER
        ]
      },
      { className: 'deletion', match: /\b(?:fatal error|Error):/ },
      THREAD_TYPE_MODE
    ]
  };

  // ---------------------------------------------------------------------------
  // Current thread line
  // ---------------------------------------------------------------------------
  const CURRENT_THREAD = {
    begin: /^Current thread\s+\(/,
    end: /$/,
    returnBegin: true,
    contains: [
      { className: 'attr', match: /^Current thread/ },
      HEX_ADDRESS,
      THREAD_TYPE_MODE,
      {
        className: 'string',
        begin: /"/,
        end: /"/
      },
      {
        className: 'keyword',
        match: /\bdaemon\b/
      },
      THREAD_STATE_MODE
    ]
  };

  // ---------------------------------------------------------------------------
  // Frame lines (V, v, C, J, j, A)
  // ---------------------------------------------------------------------------
  const VM_FRAME = {
    begin: /^V\s{2}/,
    end: /$/,
    returnBegin: true,
    contains: [
      { className: 'keyword', match: /^V/ },
      { className: 'title.class', match: /\[[\w.-]+(?:\.(?:so|dylib|dll))?/ },
      HEX_ADDRESS,
      { className: 'title.function', match: /(?<=\]\s+)\S+?(?=\+0x|\s+\(|\s*$)/ },
      { className: 'string', match: /\([^)]+\.(?:cpp|hpp|c|h):\d+\)/ }
    ]
  };

  const NATIVE_FRAME = {
    begin: /^C\s{2}/,
    end: /$/,
    returnBegin: true,
    contains: [
      { className: 'keyword', match: /^C/ },
      { className: 'title.class', match: /\[[\w.-]+(?:\.(?:so|dylib|dll))?/ },
      HEX_ADDRESS,
      { className: 'title.function', match: /(?<=\]\s+)\S+/ }
    ]
  };

  const STUB_FRAME = {
    begin: /^v\s{2}/,
    end: /$/,
    returnBegin: true,
    contains: [
      { className: 'keyword', match: /^v/ },
      { className: 'title.function', match: /~\S+(?:\s+\([^)]+\))?/ },
      HEX_ADDRESS
    ]
  };

  const COMPILED_JAVA_FRAME = {
    begin: /^J\s+\d+/,
    end: /$/,
    returnBegin: true,
    contains: [
      { className: 'keyword', match: /^J/ },
      { className: 'keyword', match: /\bc[12]\b/ },
      { className: 'title.function', match: /[\w.$]+(?=\()/ },
      { className: 'type', match: /\([^)]*\)[VZBCSIJFDL\[]\S*/ },
      HEX_ADDRESS,
      INTEGER
    ]
  };

  const INTERPRETED_JAVA_FRAME = {
    begin: /^j\s+/,
    end: /$/,
    returnBegin: true,
    contains: [
      { className: 'keyword', match: /^j/ },
      { className: 'title.function', match: /[\w.$]+(?=\()/ },
      { className: 'type', match: /\([^)]*\)[VZBCSIJFDL\[]\S*/ },
      { className: 'title.class', match: /\S+@\S+/ },
      HEX_ADDRESS,
      INTEGER
    ]
  };

  const GENERIC_FRAME = {
    match: /^[VvCJjA]\s{2}.*/,
    contains: [
      { className: 'keyword', match: /^[VvCJjA]/ },
      HEX_ADDRESS
    ]
  };

  const FRAME_LIST = {
    begin: /^(?:Native|Java) frames:/,
    end: /(?=^(?:[a-z]|Stack|siginfo|Register|Top of Stack|Instructions|Lock stack|Current Compile|Decoder))/,
    returnBegin: true,
    contains: [
      { className: 'section', match: /^(?:Native|Java) frames:(?:\s*\([^)]*\))?.*/ },
      VM_FRAME, NATIVE_FRAME, STUB_FRAME,
      COMPILED_JAVA_FRAME, INTERPRETED_JAVA_FRAME, GENERIC_FRAME,
      ...GLOBAL_CONTAINS
    ]
  };

  // ---------------------------------------------------------------------------
  // siginfo
  // ---------------------------------------------------------------------------
  const SIGINFO = {
    begin: /^siginfo:/,
    end: /$/,
    returnBegin: true,
    contains: [
      { className: 'attr', match: /siginfo:|si_signo:|si_code:|si_addr:|si_errno:|si_band:/ },
      SIGNAL_MODE,
      HEX_ADDRESS,
      INTEGER
    ]
  };

  // ---------------------------------------------------------------------------
  // Stack bounds
  // ---------------------------------------------------------------------------
  const STACK_BOUNDS = {
    begin: /^Stack:\s+\[/,
    end: /$/,
    returnBegin: true,
    contains: [
      { className: 'attr', match: /^Stack:|sp|free space/ },
      HEX_ADDRESS,
      MEMORY_SIZE,
      INTEGER
    ]
  };

  // ---------------------------------------------------------------------------
  // Registers block
  // ---------------------------------------------------------------------------
  const REGISTER_ASSIGNMENT = {
    match: new RegExp('(' + REGISTER_RE + ')\\s*=\\s*(0x[0-9a-fA-F]+)', 'i'),
    contains: [
      { className: 'variable', match: new RegExp(REGISTER_RE, 'i') },
      HEX_ADDRESS
    ]
  };

  const REGISTERS_BLOCK = {
    begin: /^Registers:\s*$/,
    end: /(?=^(?:[a-z]|Stack|siginfo|Register to memory|Top of Stack|Instructions))/,
    returnBegin: true,
    contains: [
      { className: 'section', match: /^Registers:/ },
      REGISTER_ASSIGNMENT,
      ...GLOBAL_CONTAINS
    ]
  };

  // ---------------------------------------------------------------------------
  // Register to memory mapping
  // ---------------------------------------------------------------------------
  const REGISTER_MEMORY_MAPPING = {
    begin: /^Register to memory mapping:\s*$/,
    end: /^$/,
    returnBegin: true,
    contains: [
      { className: 'section', match: /^Register to memory mapping:/ },
      REGISTER_ASSIGNMENT,
      { className: 'string', match: /(?<=in )\S+/ },
      ...GLOBAL_CONTAINS
    ]
  };

  // ---------------------------------------------------------------------------
  // Top of Stack / Instructions / Stack slot mapping / Lock stack
  // ---------------------------------------------------------------------------
  const TOP_OF_STACK = {
    begin: /^Top of Stack:\s+\(sp=/,
    end: /^$/,
    returnBegin: true,
    contains: [
      { className: 'section', match: /^Top of Stack:/ },
      ...GLOBAL_CONTAINS
    ]
  };

  const INSTRUCTIONS = {
    begin: /^Instructions:\s+\(pc=/,
    end: /^$/,
    returnBegin: true,
    contains: [
      { className: 'section', match: /^Instructions:/ },
      { className: 'keyword', match: /^=>/ },
      ...GLOBAL_CONTAINS
    ]
  };

  const STACK_SLOT_MAPPING = {
    begin: /^Stack slot to memory mapping:\s*$/,
    end: /(?=^\S)/,
    returnBegin: true,
    contains: [
      { className: 'section', match: /^Stack slot to memory mapping:/ },
      ...GLOBAL_CONTAINS
    ]
  };

  const LOCK_STACK = {
    begin: /^Lock stack of current Java thread.*:\s*$/,
    end: /^$/,
    returnBegin: true,
    contains: [
      { className: 'section', match: /^Lock stack of current Java thread.*:/ },
      ...GLOBAL_CONTAINS
    ]
  };

  // ---------------------------------------------------------------------------
  // Current CompileTask
  // ---------------------------------------------------------------------------
  const CURRENT_COMPILE_TASK = {
    begin: /^Current CompileTask:\s*$/,
    end: /^$/,
    returnBegin: true,
    contains: [
      { className: 'section', match: /^Current CompileTask:/ },
      { className: 'keyword', match: /\bc[12]\b/ },
      ...GLOBAL_CONTAINS
    ]
  };

  // ---------------------------------------------------------------------------
  // Error during error reporting
  // ---------------------------------------------------------------------------
  const ERROR_DURING_ERROR = {
    className: 'deletion',
    match: /^\[error occurred during error reporting \(.*?\), id 0x[0-9a-fA-F]+.*?\]/
  };

  // ---------------------------------------------------------------------------
  // Code blob (between --- separators)
  // ---------------------------------------------------------------------------
  const CODE_BLOB = {
    begin: /^-{80}$/,
    end: /^-{80}$/,
    contains: [
      { className: 'comment', match: /^-{80}$/ },
      ...GLOBAL_CONTAINS
    ]
  };

  // ---------------------------------------------------------------------------
  // Thread list (Java Threads / Other Threads)
  // ---------------------------------------------------------------------------
  const THREAD_LIST = {
    begin: /^(?:Java|Other) Threads:.*/,
    end: /^$/,
    returnBegin: true,
    contains: [
      { className: 'section', match: /^(?:Java|Other) Threads:.*/ },
      { className: 'keyword', match: /^=>/ },
      THREAD_TYPE_MODE,
      THREAD_STATE_MODE,
      { className: 'keyword', match: /\bdaemon\b/ },
      QUOTED_STRING,
      HEX_ADDRESS,
      INTEGER
    ]
  };

  // ---------------------------------------------------------------------------
  // Threads class SMR info
  // ---------------------------------------------------------------------------
  const THREADS_SMR = {
    begin: /^Threads class SMR info:\s*$/,
    end: /^$/,
    returnBegin: true,
    contains: [
      { className: 'section', match: /^Threads class SMR info:/ },
      ...GLOBAL_CONTAINS
    ]
  };

  // ---------------------------------------------------------------------------
  // Threads with active compile tasks
  // ---------------------------------------------------------------------------
  const THREADS_WITH_COMPILE = {
    begin: /^Threads with active compile tasks:\s*$/,
    end: /^$/,
    returnBegin: true,
    contains: [
      { className: 'section', match: /^Threads with active compile tasks:/ },
      { className: 'attr',    match: /^Total:/ },
      ...GLOBAL_CONTAINS
    ]
  };

  // ---------------------------------------------------------------------------
  // Event log
  // ---------------------------------------------------------------------------
  const EVENT_LOG = {
    begin: /^.*?\(\d+ events?\):\s*$/,
    end: /(?=^(?=[^ \t]|$)(?![E]vent:|No events|\{|\}))/,
    returnBegin: true,
    contains: [
      { className: 'section', match: /^.*?\(\d+ events?\):/ },
      {
        begin: /^Event:\s+\d+(?:\.\d+)?\s+Thread\s+0x[0-9a-fA-F]+\s+nmethod\s+\d+%?\s+0x[0-9a-fA-F]+\s+code\s+\[0x[0-9a-fA-F]+,\s*0x[0-9a-fA-F]+\]/,
        end: /$/,
        contains: [
          { className: 'attr', match: /^Event:|\bThread\b|\bcode\b/ },
          { className: 'keyword', match: /\bnmethod\b/ },
          { className: 'number', match: /\d+(?:\.\d+)?%?/ },
          HEX_ADDRESS
        ]
      },
      {
        begin: /^Event:\s+\d+(?:\.\d+)?\s+Thread\s+0x[0-9a-fA-F]+\s+\d+\s*%?\s*[!bns ]*\s+(?:[\w$]+\.)*[\w$]+::[\w$<>]+(?:\s+@\s+\d+)?\s+\(\d+ bytes\)/,
        end: /$/,
        contains: [
          { className: 'attr', match: /^Event:|\bThread\b/ },
          { className: 'keyword', match: /\b[!bnsc12]+\b|::|%/ },
          { className: 'title.class', match: /(?:[\w$]+\.)+[\w$]+(?=::)/ },
          { className: 'title.function', match: /[\w$<>]+(?=(?:\s+@\s+\d+)?\s+\(\d+ bytes\))/ },
          { className: 'number', match: /\d+(?:\.\d+)?/ },
          HEX_ADDRESS
        ]
      },
      {
        begin: /^Event:\s+\d+(?:\.\d+)?\s+Loading class\s+[\w/$]+(?:\s+done)?$/,
        end: /$/,
        contains: [
          { className: 'attr', match: /^Event:|\bLoading class\b|\bdone\b/ },
          { className: 'type', match: /[\w/$]+(?=\s*(?:done)?$)/ },
          { className: 'number', match: /\d+(?:\.\d+)?/ }
        ]
      },
      {
        begin: /^Event:\s+\d+(?:\.\d+)?\s+Thread\s+0x[0-9a-fA-F]+\s+Exception\s+<a\s+'[^']+'\{0x[0-9a-fA-F]+\}(?::\s+'[^']+')?>\s+\(0x[0-9a-fA-F]+\)/,
        end: /$/,
        contains: [
          { className: 'attr', match: /^Event:|\bThread\b/ },
          { className: 'deletion', match: /\bException\b/ },
          { className: 'type', match: /'[^']+'(?=\{)/ },
          { className: 'title.function', match: /'[^']+'(?=>)/ },
          { className: 'number', match: /\d+(?:\.\d+)?/ },
          HEX_ADDRESS
        ]
      },
      {
        begin: /^thrown\s+\[[^,\]]+,\s*line\s+\d+\]$/,
        end: /$/,
        contains: [
          { className: 'attr', match: /^thrown|\bline\b/ },
          { className: 'string', match: /\[[^,\]]+/ },
          INTEGER
        ]
      },
      {
        begin: /^Event:\s+\d+(?:\.\d+)?\s+Executing\s+(?:(?:non-)?safepoint\s+)?VM operation:\s+.+?(?:\s+done)?$/,
        end: /$/,
        contains: [
          { className: 'attr', match: /^Event:|\bExecuting\b|\bVM operation:\b|\bdone\b/ },
          { className: 'keyword', match: /\b(?:non-)?safepoint\b/ },
          { className: 'title.function', match: /(?<=VM operation:\s).+?(?=\s+done$|$)/ },
          { className: 'number', match: /\d+(?:\.\d+)?/ }
        ]
      },
      {
        begin: /^Event:\s+\d+(?:\.\d+)?\s+Protecting memory\s+\[0x[0-9a-fA-F]+,0x[0-9a-fA-F]+\]\s+with protection modes\s+\d+$/,
        end: /$/,
        contains: [
          { className: 'attr', match: /^Event:|\bProtecting memory\b|\bwith protection modes\b/ },
          { className: 'number', match: /\d+(?:\.\d+)?/ },
          HEX_ADDRESS
        ]
      },
      {
        begin: /^Event:\s+\d+(?:\.\d+)?\s+Thread\s+0x[0-9a-fA-F]+\s+(?:Thread added:|Thread exited:|JVMTI - enter).+$/,
        end: /$/,
        contains: [
          { className: 'attr', match: /^Event:|\bThread\b|\bThread added:|\bThread exited:|\bJVMTI - enter\b/ },
          { className: 'number', match: /\d+(?:\.\d+)?/ },
          HEX_ADDRESS
        ]
      },
      {
        begin: /^Event:\s+\d+(?:\.\d+)?\s+Thread\s+0x[0-9a-fA-F]+\s+Unloading class\s+0x[0-9a-fA-F]+\s+'[^']+'$/,
        end: /$/,
        contains: [
          { className: 'attr', match: /^Event:|\bThread\b|\bUnloading class\b/ },
          { className: 'type', match: /'[^']+'/ },
          { className: 'number', match: /\d+(?:\.\d+)?/ },
          HEX_ADDRESS
        ]
      },
      {
        begin: /^Event:\s+\d+(?:\.\d+)?\s+Thread\s+0x[0-9a-fA-F]+\s+Uncommon trap:\s+.+$/,
        end: /$/,
        contains: [
          { className: 'attr', match: /^Event:|\bThread\b|\bUncommon trap:\b/ },
          { className: 'string', match: /(?<=Uncommon trap:\s).+$/ },
          { className: 'number', match: /\d+(?:\.\d+)?/ },
          HEX_ADDRESS
        ]
      },
      {
        begin: /^Event:\s+\d+(?:\.\d+)?\s+Thread\s+0x[0-9a-fA-F]+\s+DEOPT\s+(?:PACKING|UNPACKING)\s+.+$/,
        end: /$/,
        contains: [
          { className: 'attr', match: /^Event:|\bThread\b/ },
          { className: 'keyword', match: /\bDEOPT\s+(?:PACKING|UNPACKING)\b/ },
          { className: 'string', match: /(?<=\b(?:PACKING|UNPACKING)\s).+$/ },
          { className: 'number', match: /\d+(?:\.\d+)?/ },
          HEX_ADDRESS
        ]
      },
      {
        begin: /^Event:\s+\d+(?:\.\d+)?\s+Thread\s+0x[0-9a-fA-F]+\s+Implicit null exception at\s+0x[0-9a-fA-F]+\s+to\s+0x[0-9a-fA-F]+$/,
        end: /$/,
        contains: [
          { className: 'attr', match: /^Event:|\bThread\b|\bto\b/ },
          { className: 'deletion', match: /\bImplicit null exception at\b/ },
          { className: 'number', match: /\d+(?:\.\d+)?/ },
          HEX_ADDRESS
        ]
      },
      {
        begin: /^Event:\s+\d+(?:\.\d+)?\s+(?:GC heap before|GC heap after|Concurrent\s+.+|Pause\s+.+|Control thread transition\s+.+)(?:\s+done)?$/,
        end: /$/,
        contains: [
          { className: 'attr', match: /^Event:|\bGC heap before\b|\bGC heap after\b|\bConcurrent\b|\bPause\b|\bControl thread transition\b|\bdone\b/ },
          { className: 'number', match: /\d+(?:\.\d+)?/ }
        ]
      },
      {
        begin: /^Event:\s+\d+(?:\.\d+)?\s+\{(?:heap|metaspace)\s+(?:Before|After)\s+GC\s+invocations=.*$/,
        end: /$/,
        contains: [
          { className: 'attr', match: /^Event:|\bheap\b|\bmetaspace\b|\bBefore\b|\bAfter\b|\bGC\b|\binvocations\b/ },
          { className: 'number', match: /\d+(?:\.\d+)?/ }
        ]
      },
      {
        begin: /^Event:\s+\d+(?:\.\d+)?\s+Thread\s+0x[0-9a-fA-F]+\s+flushing nmethod\s+0x[0-9a-fA-F]+$/,
        end: /$/,
        contains: [
          { className: 'attr', match: /^Event:|\bThread\b/ },
          { className: 'keyword', match: /\bflushing nmethod\b/ },
          { className: 'number', match: /\d+(?:\.\d+)?/ },
          HEX_ADDRESS
        ]
      },
      {
        begin: /^Event:\s+\d+(?:\.\d+)?\s+Thread\s+0x[0-9a-fA-F]+\s+Cancelling GC:\s+.+$/,
        end: /$/,
        contains: [
          { className: 'attr', match: /^Event:|\bThread\b|\bCancelling GC:\b/ },
          { className: 'string', match: /(?<=Cancelling GC:\s).+$/ },
          { className: 'number', match: /\d+(?:\.\d+)?/ },
          HEX_ADDRESS
        ]
      },
      {
        begin: /^Event:/,
        end: /$/,
        contains: [
          { className: 'attr', match: /^Event:/ },
          { className: 'number', match: /\d+(?:\.\d+)?/ },
          ...GLOBAL_CONTAINS
        ]
      },
      { className: 'comment', match: /^No events$/ },
      ...GLOBAL_CONTAINS
    ]
  };

  // ---------------------------------------------------------------------------
  // Dynamic libraries
  // ---------------------------------------------------------------------------
  const DYNAMIC_LIBRARIES = {
    begin: /^Dynamic libraries:\s*$/,
    end: /^$/,
    returnBegin: true,
    contains: [
      { className: 'section', match: /^Dynamic libraries:/ },
      {
        // macOS: addr\tpath
        match: /^(0x[0-9a-fA-F]+)\t(.+)/,
        contains: [
          HEX_ADDRESS,
          { className: 'string', match: /\t.+/ }
        ]
      },
      {
        // Linux /proc/self/maps: addr_range perms offset dev inode [path]
        match: /^[0-9a-fA-F]+-[0-9a-fA-F]+\s+([r-][w-][x-][ps-])/,
        contains: [
          HEX_ADDRESS,
          { className: 'keyword', match: /[r-][w-][x-][ps-]/ },
          { className: 'string', match: /\S+$/ }
        ]
      },
      ...GLOBAL_CONTAINS
    ]
  };

  // ---------------------------------------------------------------------------
  // VM Arguments
  // ---------------------------------------------------------------------------
  const VM_ARGUMENTS = {
    begin: /^VM Arguments:\s*$/,
    end: /^$/,
    returnBegin: true,
    contains: [
      { className: 'section', match: /^VM Arguments:/ },
      { className: 'attr', match: /^(?:jvm_args|java_command|java_class_path \(initial\)|Launcher Type):/ },
      { className: 'string', match: /(?<=:\s*).+/ }
    ]
  };

  // ---------------------------------------------------------------------------
  // Global flags
  // ---------------------------------------------------------------------------
  const GLOBAL_FLAGS = {
    begin: /^\[Global flags\]\s*$/,
    end: /^$/,
    returnBegin: true,
    contains: [
      { className: 'section', match: /^\[Global flags\]/ },
      {
        className: 'type',
        match: new RegExp('\\b(?:' + FLAG_TYPE_RE + ')\\b')
      },
      {
        className: 'type',
        match: /\{[^}]+\}/
      },
      QUOTED_STRING,
      BOOLEAN,
      ...GLOBAL_CONTAINS
    ]
  };

  // ---------------------------------------------------------------------------
  // Environment Variables
  // ---------------------------------------------------------------------------
  const ENVIRONMENT_VARIABLES = {
    begin: /^Environment Variables:\s*$/,
    end: /^$/,
    returnBegin: true,
    contains: [
      { className: 'section', match: /^Environment Variables:/ },
      {
        match: /^([A-Z_][A-Z0-9_]*)=(.*)/,
        contains: [
          { className: 'variable', match: /^[A-Z_][A-Z0-9_]*/ },
          { className: 'string', match: /(?<==).*/ }
        ]
      }
    ]
  };

  // ---------------------------------------------------------------------------
  // Signal Handlers
  // ---------------------------------------------------------------------------
  const SIGNAL_HANDLERS = {
    begin: /^Signal Handlers:\s*$/,
    end: /^$/,
    returnBegin: true,
    contains: [
      { className: 'section', match: /^Signal Handlers:/ },
      {
        className: 'title.function',
        match: /(?:SIG|EXCEPTION_)[A-Z_]+(?=:)/
      },
      ...GLOBAL_CONTAINS
    ]
  };

  // ---------------------------------------------------------------------------
  // Heap / Heap Regions
  // ---------------------------------------------------------------------------
  const HEAP_BLOCK = {
    begin: /^Heap:\s*$/,
    end: /^$/,
    returnBegin: true,
    contains: [
      { className: 'section', match: /^Heap:/ },
      {
        className: 'type',
        match: /\b(?:garbage-first heap|Shenandoah Heap|ZHeap|PSYoungGen|PSOldGen|ParOldGen|def new generation|tenured generation)\b/
      },
      ...GLOBAL_CONTAINS
    ]
  };

  const HEAP_REGIONS = {
    begin: /^Heap Regions:\s*$/,
    end: /^$/,
    returnBegin: true,
    contains: [
      { className: 'section', match: /^Heap Regions:/ },
      ...GLOBAL_CONTAINS
    ]
  };

  // ---------------------------------------------------------------------------
  // Card table / Marking Bits
  // ---------------------------------------------------------------------------
  const CARD_TABLE = {
    match: /^(Card table byte_map:)\s+(.*)/,
    contains: [
      { className: 'attr', match: /Card table byte_map:/ },
      ...GLOBAL_CONTAINS
    ]
  };

  const MARKING_BITS = {
    begin: /^Marking Bits:/,
    end: /^$/,
    returnBegin: true,
    contains: [
      { className: 'section', match: /^Marking Bits:/ },
      ...GLOBAL_CONTAINS
    ]
  };

  // ---------------------------------------------------------------------------
  // Metaspace
  // ---------------------------------------------------------------------------
  const METASPACE = {
    begin: /^Metaspace\b/,
    end: /(?=^(?!Metaspace|\s|Usage:|Virtual space:|Chunk freelists:|MaxMetaspaceSize:|CompressedClassSpaceSize:|Initial GC|Current GC|CDS:|Narrow klass|Encoding Range:|Klass Range:|Klass ID Range:|Protection zone:|UseCompressedClassPointers|Internal statistics:)(?:\S))/,
    returnBegin: true,
    contains: [
      { className: 'section', match: /^Metaspace\b.*/ },
      { className: 'attr', match: /\b(?:used|committed|reserved)\b/ },
      ...GLOBAL_CONTAINS
    ]
  };

  // ---------------------------------------------------------------------------
  // Code cache
  // ---------------------------------------------------------------------------
  const CODE_CACHE = {
    begin: /^Code(?:Heap|Cache)\b/,
    end: /(?=^(?!\s*(?:Code|bounds|total_blobs|nmethods|adapters|full_count|Compilation)))/,
    returnBegin: true,
    contains: [
      { className: 'section', match: /^Code(?:Heap|Cache)\b.*/ },
      { className: 'attr', match: /(?:size|used|max_used|free|total_blobs|nmethods|adapters|full_count)(?==)/ },
      { className: 'attr', match: /^Compilation:/ },
      ...GLOBAL_CONTAINS
    ]
  };

  // ---------------------------------------------------------------------------
  // GC Precious Log
  // ---------------------------------------------------------------------------
  const GC_PRECIOUS_LOG = {
    begin: /^GC Precious Log:\s*$/,
    end: /^$/,
    returnBegin: true,
    contains: [
      { className: 'section', match: /^GC Precious Log:/ },
      ...GLOBAL_CONTAINS
    ]
  };

  // ---------------------------------------------------------------------------
  // Native Memory Tracking
  // ---------------------------------------------------------------------------
  const NMT_REPORT = {
    begin: /^Native Memory Tracking:\s*$/,
    end: /(?=^[^ \t(-])/,
    returnBegin: true,
    contains: [
      { className: 'section', match: /^Native Memory Tracking:/ },
      {
        // Category: -  Name (reserved=NKB, committed=NKB)
        match: /^-\s+(.+?)\s+\(reserved=(\d+)([KMG]?B),\s+committed=(\d+)([KMG]?B)\)/,
        contains: [
          { className: 'type', match: /(?<=^-\s+).+?(?=\s+\()/ },
          { className: 'attr', match: /reserved|committed/ },
          { className: 'number', match: /\d+[KMG]?B/ }
        ]
      },
      {
        match: /^Total:\s+reserved=(\d+)([KMG]?B),\s+committed=(\d+)([KMG]?B)/,
        contains: [
          { className: 'attr', match: /^Total:/ },
          { className: 'number', match: /\d+[KMG]?B/ }
        ]
      },
      ...GLOBAL_CONTAINS
    ]
  };

  // ---------------------------------------------------------------------------
  // Compiler Memory / Internal statistics / Logging / JFR settings
  // ---------------------------------------------------------------------------
  const makeSimpleBlock = (headerRe) => ({
    begin: headerRe,
    end: /^$/,
    returnBegin: true,
    contains: [
      { className: 'section', match: headerRe },
      ...GLOBAL_CONTAINS
    ]
  });

  const COMPILER_MEMORY     = makeSimpleBlock(/^Compiler Memory Statistic.*/);
  const INTERNAL_STATISTICS = makeSimpleBlock(/^Internal statistics:\s*$/);
  const LOGGING_CONFIG      = makeSimpleBlock(/^Logging:\s*$/);
  const JFR_SETTINGS        = makeSimpleBlock(/^JFR settings:\s*$/);
  const VM_MUTEX            = makeSimpleBlock(/^VM Mutex\/Monitor.*/);
  const THREADS_SMR_BLOCK   = makeSimpleBlock(/^Threads class SMR info:\s*$/);

  // ---------------------------------------------------------------------------
  // Release file
  // ---------------------------------------------------------------------------
  const RELEASE_FILE = {
    begin: /^Release file:\s*$/,
    end: /^$/,
    returnBegin: true,
    contains: [
      { className: 'section', match: /^Release file:/ },
      { className: 'attr', match: /^\w+(?==)/ },
      QUOTED_STRING
    ]
  };

  // ---------------------------------------------------------------------------
  // Active Locale
  // ---------------------------------------------------------------------------
  const ACTIVE_LOCALE = {
    begin: /^Active Locale:\s*$/,
    end: /^$/,
    returnBegin: true,
    contains: [
      { className: 'section', match: /^Active Locale:/ },
      { className: 'variable', match: /^LC_\w+/ },
      { className: 'string', match: /(?<==).*/ }
    ]
  };

  // ---------------------------------------------------------------------------
  // Process single-line items
  // ---------------------------------------------------------------------------
  const PROCESS_SINGLE_LINE = {
    variants: [
      { match: /^uid\s+:\s+\d+\s+euid\s+:\s+\d+\s+gid\s+:\s+\d+\s+egid\s+:\s+\d+/ },
      { match: /^umask:\s+\d+\s+\([^)]+\)/ },
      { match: /^VM state:\s+.*$/ },
      { match: /^Heap address:\s+0x[0-9a-fA-F]+/ },
      { match: /^Compilation:\s+.*$/ },
      { match: /^CDS archive.*/ },
      { match: /^Polling page:\s+0x[0-9a-fA-F]+/ },
      { match: /^JVMTI agents:\s+.*$/ },
      { match: /^Preinit state:.*$/ },
      { match: /^Periodic native trim\s+.*$/ },
      { match: /^(?:Encoding Range|Klass Range|Klass ID Range|Protection zone|MallocLimit):\s+.*$/ },
      { match: /^Decoder state:\s+.*$/ },
      { match: /^VM Error Count:\s+\d+/ },
      { match: /^Current thread is native thread\s*$/ },
      { match: /^Sigstack overflow\s*$/ }
    ],
    contains: [
      { className: 'attr', match: /^(?:uid|euid|gid|egid|umask|VM state|Heap address|Compilation|CDS archive|Polling page|JVMTI agents|Preinit state|Periodic native trim|Encoding Range|Klass Range|Klass ID Range|Protection zone|MallocLimit|Decoder state|VM Error Count|Current thread is native thread|Sigstack overflow)(?::|\s)/ },
      HEX_ADDRESS,
      INTEGER
    ]
  };

  // ---------------------------------------------------------------------------
  // OS info (continues through blank lines until CPU:)
  // ---------------------------------------------------------------------------
  const OS_INFO = {
    begin: /^OS:\s*$/,
    end: /(?=^CPU:|^-{10,})/,
    returnBegin: true,
    contains: [
      { className: 'section', match: /^OS:/ },
      { className: 'attr', match: /^uname:|^load average:|rlimit/ },
      ...GLOBAL_CONTAINS
    ]
  };

  // ---------------------------------------------------------------------------
  // CPU info
  // ---------------------------------------------------------------------------
  const CPU_INFO = {
    begin: /^CPU:\s*/,
    end: /^$/,
    returnBegin: true,
    contains: [
      { className: 'section', match: /^CPU:/ },
      ...GLOBAL_CONTAINS
    ]
  };

  // ---------------------------------------------------------------------------
  // Memory info / vm_info (single-line)
  // ---------------------------------------------------------------------------
  const MEMORY_INFO = {
    match: /^(Memory:)\s+(.*)/,
    contains: [
      { className: 'section', match: /^Memory:/ },
      ...GLOBAL_CONTAINS
    ]
  };

  const VM_INFO = {
    match: /^(vm_info:)\s+(.*)/,
    contains: [
      { className: 'section', match: /^vm_info:/ }
    ]
  };

  // ---------------------------------------------------------------------------
  // Summary section key-value lines
  // ---------------------------------------------------------------------------
  const SUMMARY_KV = {
    match: /^(Command Line|Host|Time):(.*)/,
    contains: [
      { className: 'attr', match: /^(?:Command Line|Host|Time):/ },
      { className: 'type', match: /\b(?:AArch64|arm64|x86_64|amd64|x64|x86|ppc64le|ppc64|s390x|riscv64)\b/ },
      { className: 'keyword', match: /(?:-XX:[^\s]+|-X[a-zA-Z][^\s]*|-J-XX:[^\s]+|-J-X[^\s]*)/ },
      { className: 'keyword', match: /(?:-ea|-esa|-da|-dsa|-J-ea|-J-esa|-J-da|-J-dsa)\b/ },
      { className: 'attr', match: /-D[\w.$-]+(?==)/ },
      { className: 'keyword', match: /=/ },
      { className: 'title.class', match: /(?:[a-zA-Z_$][\w$]*\.)+[A-Za-z_$][\w$]*/ },
      { className: 'string', match: /\/[\w./:@+-]+/ },
      { className: 'string', match: /(?<=:\s*).*/ }
    ]
  };

  // ---------------------------------------------------------------------------
  // Generic subsection header fallback
  // ---------------------------------------------------------------------------
  const GENERIC_SUBSECTION = {
    className: 'section',
    match: /^\S[^:]*:\s*$/
  };

  // ---------------------------------------------------------------------------
  // END marker
  // ---------------------------------------------------------------------------
  const END_MARKER = {
    className: 'keyword',
    match: /^END\.$/
  };

  // ---------------------------------------------------------------------------
  // Top-level definition
  // ---------------------------------------------------------------------------
  return {
    name: 'HotSpot Error Report',
    aliases: ['hserr', 'hs_err'],
    case_insensitive: false,
    contains: [
      SECTION_BANNER,
      HEADER,
      SUMMARY_KV,
      CURRENT_THREAD,
      FRAME_LIST,
      SIGINFO,
      STACK_BOUNDS,
      REGISTERS_BLOCK,
      REGISTER_MEMORY_MAPPING,
      TOP_OF_STACK,
      INSTRUCTIONS,
      STACK_SLOT_MAPPING,
      LOCK_STACK,
      CURRENT_COMPILE_TASK,
      ERROR_DURING_ERROR,
      CODE_BLOB,
      THREAD_LIST,
      THREADS_SMR,
      THREADS_WITH_COMPILE,
      EVENT_LOG,
      DYNAMIC_LIBRARIES,
      VM_ARGUMENTS,
      GLOBAL_FLAGS,
      ENVIRONMENT_VARIABLES,
      SIGNAL_HANDLERS,
      HEAP_BLOCK,
      HEAP_REGIONS,
      CARD_TABLE,
      MARKING_BITS,
      METASPACE,
      CODE_CACHE,
      GC_PRECIOUS_LOG,
      NMT_REPORT,
      COMPILER_MEMORY,
      INTERNAL_STATISTICS,
      LOGGING_CONFIG,
      RELEASE_FILE,
      ACTIVE_LOCALE,
      VM_MUTEX,
      JFR_SETTINGS,
      PROCESS_SINGLE_LINE,
      OS_INFO,
      CPU_INFO,
      MEMORY_INFO,
      VM_INFO,
      GENERIC_SUBSECTION,
      END_MARKER,
      // Fallback global patterns
      ...GLOBAL_CONTAINS
    ]
  };
};

if (typeof module !== 'undefined' && module.exports) {
  module.exports = hserr;
} else if (typeof hljs !== 'undefined') {
  hljs.registerLanguage('hserr', hserr);
}
