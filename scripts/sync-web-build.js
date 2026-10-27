import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { execSync } from 'child_process';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const ROOT_MOBILE_DIR = path.resolve(__dirname, '..');
const WEB_APP_DIR = path.resolve(__dirname, '../../pg-owner-web-application');
const WEB_DIST_DIR = path.join(WEB_APP_DIR, 'dist');
const TARGET_DIST_DIR = path.join(ROOT_MOBILE_DIR, 'dist');

console.log('🚀 PG Ease Mobile Wrapper: Preparing Web Assets...');
console.log(`📁 Source Web Directory: ${WEB_APP_DIR}`);
console.log(`📁 Destination Directory: ${TARGET_DIST_DIR}`);

// 1. If source web directory exists, build & sync it
if (fs.existsSync(WEB_APP_DIR)) {
  if (!fs.existsSync(WEB_DIST_DIR) || process.argv.includes('--rebuild')) {
    console.log('📦 Building web application (pg-owner-web-application)...');
    execSync('npm run build', {
      cwd: WEB_APP_DIR,
      stdio: 'inherit'
    });
  }

  // Ensure target dist directory is clean
  if (fs.existsSync(TARGET_DIST_DIR)) {
    fs.rmSync(TARGET_DIST_DIR, { recursive: true, force: true });
  }

  // Copy dist from web app to mobile app
  console.log('📋 Copying production web build to mobile app...');
  fs.cpSync(WEB_DIST_DIR, TARGET_DIST_DIR, { recursive: true });
} else {
  console.log('ℹ️ Source web directory not found (standalone CI environment).');
  if (!fs.existsSync(TARGET_DIST_DIR)) {
    fs.mkdirSync(TARGET_DIST_DIR, { recursive: true });
    // Write minimal shell
    fs.writeFileSync(
      path.join(TARGET_DIST_DIR, 'index.html'),
      '<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1.0, viewport-fit=cover"><title>PG Ease Owner</title></head><body><div id="root">Loading PG Ease Owner...</div></body></html>',
      'utf-8'
    );
  }
}

// 4. Inject mobile bridge script into target index.html for native features
const targetIndexHtml = path.join(TARGET_DIST_DIR, 'index.html');
if (fs.existsSync(targetIndexHtml)) {
  let html = fs.readFileSync(targetIndexHtml, 'utf-8');

  // Inject viewport meta if needed to prevent zoom and ensure safe-area viewport-fit=cover
  if (!html.includes('viewport-fit=cover')) {
    html = html.replace(
      /<meta\s+name=["']viewport["'][^>]*>/i,
      '<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover">'
    );
  }

  // Inject mobile bridge logic into head
  const mobileBridgeSnippet = `
    <!-- PG Ease Native Mobile Bridge -->
    <style>
      :root {
        --sat: env(safe-area-inset-top, 0px);
        --sab: env(safe-area-inset-bottom, 0px);
        --sal: env(safe-area-inset-left, 0px);
        --sar: env(safe-area-inset-right, 0px);
      }
      body {
        padding-top: var(--sat);
        padding-bottom: var(--sab);
        padding-left: var(--sal);
        padding-right: var(--sar);
        -webkit-user-select: none;
        user-select: none;
        -webkit-tap-highlight-color: transparent;
        overscroll-behavior-y: none;
      }
      input, textarea, [contenteditable="true"] {
        -webkit-user-select: auto;
        user-select: auto;
      }
    </style>
    <script>
      (function() {
        function initMobileFeatures() {
          var Cap = window.Capacitor;
          if (!Cap) return;

          console.log('[PG Ease Mobile] Capacitor runtime active.');

          // Android Back Button handler
          if (Cap.Plugins && Cap.Plugins.App) {
            Cap.Plugins.App.addListener('backButton', function(event) {
              var currentPath = window.location.pathname;
              var isRoot = currentPath === '/' || currentPath === '/dashboard' || currentPath === '/login' || currentPath === '';
              
              if (!isRoot && window.history.length > 1) {
                window.history.back();
              } else {
                Cap.Plugins.App.exitApp();
              }
            });
          }

          // Splash screen auto-dismiss
          if (Cap.Plugins && Cap.Plugins.SplashScreen) {
            setTimeout(function() {
              Cap.Plugins.SplashScreen.hide();
            }, 600);
          }
        }

        if (document.readyState === 'complete' || document.readyState === 'interactive') {
          initMobileFeatures();
        } else {
          document.addEventListener('DOMContentLoaded', initMobileFeatures);
        }
      })();
    </script>
  `;

  html = html.replace('</head>', `${mobileBridgeSnippet}\n</head>`);
  fs.writeFileSync(targetIndexHtml, html, 'utf-8');
  console.log('✨ Enhanced index.html with native mobile safe-areas and viewport configuration.');
}

console.log('✅ Web assets successfully prepared and synced to mobile wrapper!');
