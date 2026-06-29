const { execSync } = require('child_process');
const fs = require('fs');

try {
  if (fs.existsSync('debug.keystore')) fs.unlinkSync('debug.keystore');
  if (fs.existsSync('debug.keystore.base64')) fs.unlinkSync('debug.keystore.base64');
  
  execSync('keytool -genkeypair -keystore debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"', { stdio: 'inherit' });
  console.log("Keystore generated successfully.");
} catch (e) {
  console.error("Failed to generate keystore:", e);
}
