import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const root = path.resolve(__dirname, '..');

const sources = [
  {
    from: path.resolve(root, '../hserr-grammar/textmate/hserr.tmLanguage.json'),
    to: path.resolve(root, 'syntaxes/hserr.tmLanguage.json'),
  },
  {
    from: path.resolve(root, '../hserr-grammar/language-configuration.json'),
    to: path.resolve(root, 'language-configuration.json'),
  },
  {
    from: path.resolve(root, '../hserr-grammar/icon.png'),
    to: path.resolve(root, 'icon.png'),
  },
  {
    from: path.resolve(root, '../hserr-grammar/icons/hserr-file-light.svg'),
    to: path.resolve(root, 'icons/hserr-file-light.svg'),
  },
  {
    from: path.resolve(root, '../hserr-grammar/icons/hserr-file-dark.svg'),
    to: path.resolve(root, 'icons/hserr-file-dark.svg'),
  },
];

for (const item of sources) {
  if (!fs.existsSync(item.from)) {
    throw new Error(`Missing source asset: ${item.from}`);
  }
  fs.mkdirSync(path.dirname(item.to), { recursive: true });
  fs.copyFileSync(item.from, item.to);
  console.log(`copied: ${path.relative(root, item.to)}`);
}
