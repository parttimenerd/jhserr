jhserr
======

[![CI](https://github.com/parttimenerd/jhserr/actions/workflows/ci.yml/badge.svg)](https://github.com/parttimenerd/jhserr/actions/workflows/ci.yml) [![Maven Central Version](https://img.shields.io/maven-central/v/me.bechberger/jhserr)](https://search.maven.org/artifact/me.bechberger/jhserr)

__This is an early prototype of the SapMachine team, use at your own risk.__
__We don't provide any guarantees regarding functionality or security.__

A Java library and CLI for parsing, transforming, and redacting HotSpot hs_err crash report files.

## Quick Start

**Parse an hs_err file:**

```java
import me.bechberger.jhserr.HsErrReport;
import me.bechberger.jhserr.parser.HsErrParser;

HsErrReport report = HsErrParser.parse(Path.of("hs_err_pid12345.log"));
System.out.println(report.header().crashReason());
```

**Redact sensitive information:**

```java
import me.bechberger.jhserr.transform.RedactionTransformer;

RedactionTransformer redactor = RedactionTransformer.withDefaults();
HsErrReport redacted = redactor.transform(report);
Files.writeString(Path.of("redacted.log"), redacted.toString());
```

## Features

- **Full-fidelity parsing**: Parse any hs_err crash report into a typed model.
  Round-trip: `parse(text).toString()` reproduces the original file.
- **Typed model**: 20+ section item types including thread info, stack frames,
  registers, signal info, stack bounds, memory info, OS info, CPU info,
  dynamic libraries, VM arguments, environment variables, heap info, and more.
- **Visitor pattern**: Walk the model tree with `HsErrVisitor` — override only
  the visit methods you need.
- **Transformer pattern**: `HsErrTransformer` provides identity-transforming base class.
  Subclass to selectively modify report contents.
- **Redaction**: `RedactionTransformer` strips usernames, paths, hostnames,
  PIDs, IP addresses, env vars, and other sensitive data from crash reports.
  Configurable via `RedactionConfig` with presets (minimal, aggressive) and JSON config files.
  `sensitivePathPrefixes` supports glob patterns (`*`, `**`, `?`) alongside literal prefixes.
  Path matching is boundary-aware — `/priv` matches `/priv/var` but not `/private`.
- **JSON serialization**: Full Jackson-based JSON serialization and deserialization
  via `HsErrJson`. Supports round-trip JSON ↔ model.
- **CLI tool**: Two commands — `redact` for stripping sensitive data,
  `json` for converting between text and JSON (with `to`, `from`, `schema` subcommands).

## Installation

### Maven

```xml
<dependency>
    <groupId>me.bechberger</groupId>
    <artifactId>jhserr</artifactId>
    <version>0.0.0</version>
</dependency>
```

## Usage

### CLI

The CLI has three commands: `redact`, `json`, and `roundtrip`.

#### Redact

```bash
# Redact sensitive data (output on stdout, summary on stderr)
java -jar jhserr.jar redact hs_err_pid12345.log

# Redact with output to file
java -jar jhserr.jar redact -o redacted.log hs_err_pid12345.log

# Scan for sensitive information only (no output)
java -jar jhserr.jar redact --scan hs_err_pid12345.log

# Minimal preset (usernames + hostnames only)
java -jar jhserr.jar redact --minimal hs_err_pid12345.log

# Aggressive preset (also strips libs, events, thread names)
java -jar jhserr.jar redact --aggressive hs_err_pid12345.log

# Redact with extra hints
java -jar jhserr.jar redact --username jdoe --hostname myhost \
    --sensitive-path /home/jdoe/project hs_err_pid12345.log

# Redact with a config file
java -jar jhserr.jar redact --config redaction.json hs_err_pid12345.log

# Redact and output as JSON
java -jar jhserr.jar redact --json hs_err_pid12345.log

# Generate a default redaction config
java -jar jhserr.jar redact --generate-config
java -jar jhserr.jar redact --generate-config -o redaction.json
```

#### JSON

```bash
# Convert hs_err to JSON
java -jar jhserr.jar json to hs_err_pid12345.log

# Convert hs_err to JSON file
java -jar jhserr.jar json to -o report.json hs_err_pid12345.log

# Convert JSON back to hs_err text
java -jar jhserr.jar json from report.json

# Convert JSON back to text file
java -jar jhserr.jar json from -o restored.log report.json

# Print the JSON schema
java -jar jhserr.jar json schema
java -jar jhserr.jar json schema -o schema.json
```

#### Roundtrip

```bash
# Run text roundtrip checks for all hs_err files in a directory
java -jar jhserr.jar roundtrip sample-hserrs/new

# Run text + JSON roundtrip checks
java -jar jhserr.jar roundtrip --json sample-hserrs/new
```

### Library

```java
// Parse
HsErrReport report = HsErrParser.parse(Path.of("hs_err_pid12345.log"));

// Access typed sections
ThreadSection thread = report.thread();
SignalInfo signal = thread.signalInfo();       // parsed signal name, number, address
StackBoundsInfo stack = thread.stackBoundsInfo(); // stack bottom, top, sp, free space
List<StackFrame> frames = thread.nativeFrames().frames();

ProcessSection process = report.process();
int uid = process.uidInfo().uid();

SystemSection system = report.system();
OsInfo os = system.osInfo();
String uname = os.uname();                     // full uname string
String loadAvg = os.loadAverage();              // parsed load average
CpuInfo cpu = system.cpuInfo();
int cores = cpu.totalCpus();

// Walk with visitor
report.accept(new HsErrVisitor() {
    @Override
    public void visitNamedSection(NamedSection section) {
        System.out.println(section.name());
    }
});

// Transform
HsErrTransformer transformer = new HsErrTransformer() {
    @Override
    protected SectionItem transformNamedSection(NamedSection section) {
        return new NamedSection(section.name().toUpperCase(), section.lines());
    }
};
HsErrReport transformed = transformer.transform(report);

// Scan for sensitive information
RedactionEngine engine = new RedactionEngine();
report.accept(engine);
System.out.println("Usernames: " + engine.usernames());
System.out.println("Hostnames: " + engine.hostnames());

// Redact with config
RedactionConfig config = new RedactionConfig()
    .addAdditionalUsername("jdoe")
    .setRedactThreadNames(true)
    .setRemoveDynamicLibraries(true);
RedactionTransformer redactor = new RedactionTransformer(config);
HsErrReport redacted = redactor.transform(report);
System.err.println("Redacted: " + redactor.redactions());

// JSON round-trip
String json = HsErrJson.toJson(report);
HsErrReport fromJson = HsErrJson.fromJson(json);
```

### Redaction Config

The redaction config can be loaded from JSON. Generate a default config:

```bash
java -jar jhserr.jar redact --generate-config
```

Categories (all enabled by default unless noted):

| Category | Default | Description |
|---|---|---|
| `redactUsernames` | on | Usernames found in paths (`/Users/X/`, `/home/X/`) |
| `redactHostnames` | on | Hostnames from summary and uname |
| `redactPids` | on | PIDs and thread IDs |
| `redactEnvVars` | on | Environment variable values (except safe list) |
| `redactPaths` | on | Sensitive path prefixes (supports globs: `*`, `**`, `?`) |
| `redactIpAddresses` | on | IPv4 addresses |
| `pathMode` | `KEEP_FILENAME` | `KEEP_FILENAME` / `REDACT_ALL` / `KEEP_ALL` |
| `redactThreadNames` | off | Thread names |
| `removeDynamicLibraries` | off | Remove entire dynamic libraries section |
| `removeEventLogs` | off | Remove event log sections |

Presets: `RedactionConfig.minimal()` (usernames + hostnames only),
`RedactionConfig.aggressive()` (everything + strip libs/events/thread names).

## Model Overview

The parsed report is an `HsErrReport` containing:

- **Header** — crash reason, signal, JRE version, PID
- **Summary** — command line, hostname, elapsed time
- **ThreadSection** — current thread info, signal info, stack bounds, memory info,
  native/Java frames, registers
- **ProcessSection** — thread lists, dynamic libraries, VM arguments, environment variables,
  UID/umask, VM state, heap address info, compilation info, event logs
- **SystemSection** — OS info (uname, uptime, load average, rlimit),
  CPU info (cores, features, brand), VM info, and other system details

Section items are modeled as a `sealed interface SectionItem` with 20+ implementations
including `BlankLine`, `NamedSection`, `ThreadInfo`, `FrameList`, `Registers`,
`SignalInfo`, `StackBoundsInfo`, `MemoryInfo`, `OsInfo`, `CpuInfo`, `VmInfo`, and more.

## Development

```bash
# Build
mvn clean package

# Run tests
mvn test

# Release (requires GPG key + OSSRH credentials)
python3 release.py patch
```

### Web Tool (`hserr-web/`)

A browser-based version of the parser built with [GraalVM Web Image](https://www.graalvm.org/latest/reference-manual/web-image/)
(compiles Java to WASM). Drag & drop an hs_err file to parse, redact, and view a diff — all client-side.

Features:
- **4 tabs**: editable source, JSON with syntax highlighting, redacted view, line-level diff
- **Inline change highlighting**: the redacted tab marks only the changed substrings within each line
- **CodeMirror JSON editor**: syntax-highlighted config editing with bracket matching
- **Live config updates**: changes to the redaction config apply immediately (debounced)
- **Example files**: three bundled hs_err files to try without uploading
- **Download buttons**: export JSON or redacted text
- **Persistent config**: redaction settings saved in localStorage

The submodule lives in `hserr-web/` and requires the following to build:

- **GraalVM 25+** (install via `sdk install java 25.0.2-graal`)
- **binaryen** v119+ for `wasm-opt` (install via `brew install binaryen` on macOS or `apt install binaryen` on Linux)
- **Maven 3.9+**

```bash
# First, install the parser library into the local Maven repo
mvn install -DskipTests

# Then build the WASM module
cd hserr-web
mvn package
# Outputs: web/hserr.js + web/hserr.js.wasm
# Open web/index.html in a browser
```

Or use the all-in-one script (auto-detects the latest sdkman GraalVM):

```bash
./launch.sh          # builds everything and starts a dev server on :8080
PORT=3000 ./launch.sh  # use a custom port
```

The web tool is automatically deployed to GitHub Pages on pushes to main.

Support, Feedback, Contributing
-------------------------------
This project is open to feature requests/suggestions, bug reports etc. via <a href="https://github.com/parttimenerd/jhserr/issues">GitHub issues</a>. Contribution and feedback are encouraged and always welcome.

License
-------
MIT, Copyright 2026 SAP SE or an SAP affiliate company, Johannes Bechberger and contributors