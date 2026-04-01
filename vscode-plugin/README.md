# VS Code Plugin (hs_err Syntax)

This folder contains the VS Code extension package for hs_err syntax highlighting.

## Source of Truth

The TextMate grammar source is maintained in:

- `../hserr-grammar/textmate/hserr.tmLanguage.json`

During build/package, assets are copied into this folder via:

- `scripts/sync-assets.mjs`

Copied assets:

- `syntaxes/hserr.tmLanguage.json`
- `language-configuration.json`
- `icon.png`
- `icons/hserr-file-light.svg`
- `icons/hserr-file-dark.svg`

## Build and Package

```bash
cd vscode-plugin
npm install
npm run compile
npm run package
```

Install the generated VSIX:

```bash
code --install-extension hserr-syntax-*.vsix
```
