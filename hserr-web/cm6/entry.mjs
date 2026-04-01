// CM6 bundle entry point — re-exports everything the hserr-web app needs.
// Bundled by esbuild into web/lib/cm6-bundle.js during prepare-web-resources.sh.
export { basicSetup } from 'codemirror';
export { EditorView, keymap } from '@codemirror/view';
export { EditorState } from '@codemirror/state';
export { StreamLanguage, HighlightStyle, syntaxHighlighting } from '@codemirror/language';
export { json } from '@codemirror/lang-json';
export { tags } from '@lezer/highlight';
export { search, openSearchPanel } from '@codemirror/search';
