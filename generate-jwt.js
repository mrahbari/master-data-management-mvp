const crypto = require('crypto');

// JWT Token Generator for MDM MVP Testing
// This generates valid JWT tokens for local testing

function base64UrlEncode(str) {
  return str.toString('base64')
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=/g, '');
}

function createJWT(payload, secret = 'mdm-secret-key') {
  const header = {
    alg: 'HS256',
    typ: 'JWT'
  };

  const encodedHeader = base64UrlEncode(Buffer.from(JSON.stringify(header)));
  const encodedPayload = base64UrlEncode(Buffer.from(JSON.stringify(payload)));
  
  const signatureInput = `${encodedHeader}.${encodedPayload}`;
  const signature = crypto
    .createHmac('sha256', secret)
    .update(signatureInput)
    .digest();
  
  const encodedSignature = base64UrlEncode(Buffer.from(signature));
  
  return `${encodedHeader}.${encodedPayload}.${encodedSignature}`;
}

// Generate tokens for different roles
const now = Math.floor(Date.now() / 1000);
const oneHourLater = now + 3600;

// Admin Token
const adminToken = createJWT({
  sub: 'admin-user',
  roles: ['ADMIN', 'CUSTOMER_WRITE', 'CUSTOMER_READ'],
  aud: 'mdm-api',
  iss: 'https://mdm-demo.auth0.com/',
  exp: oneHourLater,
  iat: now,
  jti: 'admin-token-' + now
});

// Regular User Token (Write Access)
const userToken = createJWT({
  sub: 'regular-user',
  roles: ['CUSTOMER_WRITE'],
  aud: 'mdm-api',
  iss: 'https://mdm-demo.auth0.com/',
  exp: oneHourLater,
  iat: now,
  jti: 'user-token-' + now
});

// Read-Only Token
const readOnlyToken = createJWT({
  sub: 'readonly-user',
  roles: ['CUSTOMER_READ'],
  aud: 'mdm-api',
  iss: 'https://mdm-demo.auth0.com/',
  exp: oneHourLater,
  iat: now,
  jti: 'readonly-token-' + now
});

console.log('\n========================================');
console.log('  MDM MVP - JWT Tokens Generated');
console.log('========================================\n');

console.log('🔑 ADMIN TOKEN (Full Access):');
console.log(adminToken);
console.log('\nClaims:');
console.log(JSON.stringify({
  sub: 'admin-user',
  roles: ['ADMIN', 'CUSTOMER_WRITE', 'CUSTOMER_READ'],
  aud: 'mdm-api',
  iss: 'https://mdm-demo.auth0.com/',
  exp: oneHourLater,
  iat: now
}, null, 2));

console.log('\n----------------------------------------\n');

console.log('🔑 USER TOKEN (Write Access):');
console.log(userToken);
console.log('\nClaims:');
console.log(JSON.stringify({
  sub: 'regular-user',
  roles: ['CUSTOMER_WRITE'],
  aud: 'mdm-api',
  iss: 'https://mdm-demo.auth0.com/',
  exp: oneHourLater,
  iat: now
}, null, 2));

console.log('\n----------------------------------------\n');

console.log('🔑 READ-ONLY TOKEN (Read Access):');
console.log(readOnlyToken);
console.log('\nClaims:');
console.log(JSON.stringify({
  sub: 'readonly-user',
  roles: ['CUSTOMER_READ'],
  aud: 'mdm-api',
  iss: 'https://mdm-demo.auth0.com/',
  exp: oneHourLater,
  iat: now
}, null, 2));

console.log('\n========================================');
console.log('Usage Examples:');
console.log('========================================\n');

console.log('curl with Admin Token:');
console.log(`curl -X POST http://localhost:8080/api/customers \\`);
console.log(`  -H "Authorization: Bearer ${adminToken}" \\`);
console.log(`  -H "Content-Type: application/json" \\`);
console.log(`  -d '{"email": "test@example.com", "sourceSystem": "test"}'`);

console.log('\n----------------------------------------\n');

console.log('Postman Configuration:');
console.log('1. Go to Authorization tab');
console.log('2. Type: Bearer Token');
console.log(`3. Token: ${adminToken.substring(0, 50)}...`);

console.log('\n========================================\n');

// Save tokens to file
const fs = require('fs');
const tokens = {
  admin: adminToken,
  user: userToken,
  readonly: readOnlyToken,
  generated_at: new Date().toISOString(),
  expires_in: '1 hour'
};

fs.writeFileSync('jwt-tokens.json', JSON.stringify(tokens, null, 2));
console.log('✅ Tokens saved to jwt-tokens.json\n');
