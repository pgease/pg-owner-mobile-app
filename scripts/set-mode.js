import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const configPath = path.resolve(__dirname, '../capacitor.config.json');

const args = process.argv.slice(2);
const mode = args[0]; // 'live' or 'local'
const liveUrl = args[1];

if (!fs.existsSync(configPath)) {
  console.error('❌ capacitor.config.json not found!');
  process.exit(1);
}

const config = JSON.parse(fs.readFileSync(configPath, 'utf-8'));

if (mode === 'live') {
  const targetUrl = liveUrl || 'https://app.pgease.in';
  config.server = config.server || {};
  config.server.url = targetUrl;
  config.server.cleartext = true;
  console.log(`🌐 Switched mobile app to LIVE MODE: pointing to ${targetUrl}`);
} else if (mode === 'local') {
  if (config.server && config.server.url) {
    delete config.server.url;
  }
  console.log(`📦 Switched mobile app to LOCAL BUNDLE MODE: running bundled assets from dist/`);
} else {
  console.log('Usage: node scripts/set-mode.js [live <url> | local]');
  console.log('Current configuration:');
  console.log(`  Mode: ${config.server?.url ? 'LIVE URL (' + config.server.url + ')' : 'LOCAL BUNDLED (dist/)'}`);
  process.exit(0);
}

fs.writeFileSync(configPath, JSON.stringify(config, null, 2), 'utf-8');
console.log('💾 Updated capacitor.config.json. Run "npm run sync" to apply changes to native platforms.');
