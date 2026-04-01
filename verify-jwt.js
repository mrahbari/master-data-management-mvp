const crypto = require('crypto');
const fs = require('fs');

// Read tokens from file
const tokens = JSON.parse(fs.readFileSync('jwt-tokens.json', 'utf8'));
const adminToken = tokens.admin;

console.log('\n========================================');
console.log('  JWT Token Verification');
console.log('========================================\n');

// Split token
const parts = adminToken.split('.');
console.log('✅ Token has 3 parts:', parts.length === 3 ? 'YES' : 'NO');

// Decode header
const header = JSON.parse(Buffer.from(parts[0], 'base64').toString());
console.log('\n📋 Header:');
console.log(JSON.stringify(header, null, 2));

// Decode payload
const payload = JSON.parse(Buffer.from(parts[1], 'base64').toString());
console.log('\n📋 Payload (Claims):');
console.log(JSON.stringify(payload, null, 2));

// Verify signature
const secret = 'mdm-secret-key';
const signatureInput = `${parts[0]}.${parts[1]}`;
const expectedSignature = crypto
  .createHmac('sha256', secret)
  .update(signatureInput)
  .digest();
const expectedSignatureBase64 = expectedSignature.toString('base64')
  .replace(/\+/g, '-')
  .replace(/\//g, '_')
  .replace(/=/g, '');

console.log('\n✅ Signature Verification:');
console.log('Expected:', expectedSignatureBase64.substring(0, 50) + '...');
console.log('Actual:  ', parts[2].substring(0, 50) + '...');
console.log('Valid:   ', expectedSignatureBase64 === parts[2] ? '✅ YES' : '❌ NO');

// Check expiration
const now = Math.floor(Date.now() / 1000);
const isExpired = payload.exp < now;
console.log('\n⏰ Token Expiration:');
console.log('Expires at:', new Date(payload.exp * 1000).toISOString());
console.log('Current time:', new Date(now * 1000).toISOString());
console.log('Is Expired:', isExpired ? '❌ YES' : '✅ NO');

// Check required claims
console.log('\n📋 Required Claims:');
console.log('sub (subject):', payload.sub ? '✅' : '❌');
console.log('roles:', payload.roles ? '✅' : '❌');
console.log('aud (audience):', payload.aud ? '✅' : '❌');
console.log('iss (issuer):', payload.iss ? '✅' : '❌');
console.log('exp (expiration):', payload.exp ? '✅' : '❌');

console.log('\n========================================');
console.log('🎯 Token Status: ' + (!isExpired && expectedSignatureBase64 === parts[2] ? '✅ VALID' : '❌ INVALID'));
console.log('========================================\n');

console.log('📝 Usage:');
console.log('curl -X POST http://localhost:8080/api/customers \\');
console.log('  -H "Authorization: Bearer ' + adminToken + '" \\');
console.log('  -H "Content-Type: application/json" \\');
console.log('  -d \'{"email": "test@example.com", "sourceSystem": "test"}\'');
console.log('');
