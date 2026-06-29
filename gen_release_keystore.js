const { execSync } = require('child_process');
const fs = require('fs');

try {
  if (fs.existsSync('my-upload-key.jks')) fs.unlinkSync('my-upload-key.jks');
  
  execSync('keytool -genkeypair -keystore my-upload-key.jks -storepass android -alias upload -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Release,O=Android,C=US"', { stdio: 'inherit' });
  console.log("Release Keystore generated successfully.");
} catch (e) {
  console.error("Failed to generate release keystore:", e);
}
