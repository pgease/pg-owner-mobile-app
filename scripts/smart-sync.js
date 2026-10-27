import { execSync } from 'child_process';
import fs from 'fs';

console.log('🔄 Syncing mobile platform assets...');

// Check if Xcode.app exists
const hasXcode = fs.existsSync('/Applications/Xcode.app');

try {
  if (hasXcode) {
    execSync('npx cap sync', { stdio: 'inherit' });
  } else {
    console.log('🤖 Syncing Android platform...');
    execSync('npx cap sync android', { stdio: 'inherit' });
    console.log('\n💡 Note: Full Xcode is not installed in /Applications/Xcode.app.');
    console.log('   Android is 100% synced and ready! To sync iOS later, install Xcode and run: npm run sync:ios\n');
  }
} catch (error) {
  process.exit(1);
}
