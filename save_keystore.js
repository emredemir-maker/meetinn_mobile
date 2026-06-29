const fs = require('fs');
if (fs.existsSync('debug.keystore')) {
  const buf = fs.readFileSync('debug.keystore');
  fs.writeFileSync('debug.keystore.base64', buf.toString('base64'));
  console.log('Successfully created debug.keystore.base64');
} else {
  console.error('debug.keystore not found');
}
