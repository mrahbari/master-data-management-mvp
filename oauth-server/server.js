const express = require('express');
const crypto = require('crypto');
const fs = require('fs');
const cors = require('cors');

const app = express();
const PORT = 9999;

// Load config
const config = JSON.parse(fs.readFileSync('config.json', 'utf8'));

app.use(cors());
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// Generate RSA key pair for JWT signing
const rsaKey = crypto.generateKeyPairSync('rsa', {
  modulusLength: 2048,
  publicKeyEncoding: {
    type: 'spki',
    format: 'pem'
  },
  privateKeyEncoding: {
    type: 'pkcs8',
    format: 'pem'
  }
});

const privateKey = crypto.createPrivateKey(rsaKey.privateKey);
const publicKey = crypto.createPublicKey(rsaKey.publicKey);

// JWT Helper functions
function base64UrlEncode(str) {
  return str.toString('base64')
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=/g, '');
}

function createRS256JWT(payload) {
  const header = { alg: 'RS256', typ: 'JWT', kid: 'oauth-server-rsa-key' };
  const encodedHeader = base64UrlEncode(Buffer.from(JSON.stringify(header)));
  const encodedPayload = base64UrlEncode(Buffer.from(JSON.stringify(payload)));
  const signatureInput = `${encodedHeader}.${encodedPayload}`;
  
  const sign = crypto.createSign('RSA-SHA256');
  sign.update(signatureInput);
  sign.end();
  const signature = sign.sign(privateKey);
  const encodedSignature = base64UrlEncode(Buffer.from(signature));
  
  return `${encodedHeader}.${encodedPayload}.${encodedSignature}`;
}

// OAuth2 Token Endpoint
app.post('/oauth/token', (req, res) => {
  const { grant_type, username, password, client_id, client_secret, scope } = req.body;
  
  console.log(`\n📋 Token Request:`);
  console.log(`  grant_type: ${grant_type}`);
  console.log(`  client_id: ${client_id}`);
  if (username) console.log(`  username: ${username}`);
  
  // Validate client
  const client = config.clients.find(c => c.client_id === client_id && c.client_secret === client_secret);
  if (!client) {
    return res.status(401).json({ error: 'invalid_client', error_description: 'Invalid client credentials' });
  }
  
  // Handle different grant types
  if (grant_type === 'password') {
    // Resource Owner Password Credentials
    const user = config.users.find(u => u.username === username && u.password === password);
    if (!user) {
      return res.status(401).json({ error: 'invalid_grant', error_description: 'Invalid username or password' });
    }
    
    const now = Math.floor(Date.now() / 1000);
    const accessToken = createRS256JWT({
      sub: user.username,
      roles: user.roles,
      aud: 'mdm-api',
      iss: 'http://host.docker.internal:9999',
      exp: now + 3600,
      iat: now,
      jti: `access-${username}-${now}`,
      scope: scope || ''
    });

    const refreshToken = createRS256JWT({
      sub: user.username,
      type: 'refresh',
      exp: now + 86400 * 7,
      iat: now
    });
    
    console.log(`\n✅ Token issued for user: ${username}`);
    console.log(`   Roles: ${user.roles.join(', ')}`);
    
    res.json({
      access_token: accessToken,
      token_type: 'Bearer',
      expires_in: 3600,
      refresh_token: refreshToken,
      scope: scope || ''
    });
    
  } else if (grant_type === 'client_credentials') {
    // Client Credentials
    const now = Math.floor(Date.now() / 1000);
    const accessToken = createRS256JWT({
      sub: client_id,
      roles: ['ADMIN'],
      aud: 'mdm-api',
      iss: 'http://host.docker.internal:9999',
      exp: now + 3600,
      iat: now,
      jti: `client-${client_id}-${now}`
    });
    
    console.log(`\n✅ Client credentials token issued for: ${client_id}`);
    
    res.json({
      access_token: accessToken,
      token_type: 'Bearer',
      expires_in: 3600
    });
    
  } else {
    res.status(400).json({ error: 'unsupported_grant_type' });
  }
});

// OAuth2 Authorization Endpoint (for authorization code flow)
app.get('/oauth/authorize', (req, res) => {
  const { client_id, redirect_uri, response_type, state, scope } = req.query;
  
  console.log(`\n📋 Authorization Request:`);
  console.log(`  client_id: ${client_id}`);
  console.log(`  redirect_uri: ${redirect_uri}`);
  console.log(`  response_type: ${response_type}`);
  
  // Simple HTML login form
  res.send(`
    <!DOCTYPE html>
    <html>
    <head><title>OAuth2 Login</title>
    <style>
      body { font-family: Arial; max-width: 400px; margin: 50px auto; padding: 20px; }
      input { width: 100%; padding: 10px; margin: 10px 0; }
      button { width: 100%; padding: 10px; background: #007bff; color: white; border: none; cursor: pointer; }
      button:hover { background: #0056b3; }
    </style>
    </head>
    <body>
      <h2>🔐 OAuth2 Login</h2>
      <form method="POST" action="/oauth/authorize">
        <input type="hidden" name="client_id" value="${client_id}">
        <input type="hidden" name="redirect_uri" value="${redirect_uri}">
        <input type="hidden" name="response_type" value="${response_type}">
        <input type="hidden" name="state" value="${state}">
        <input type="hidden" name="scope" value="${scope}">
        
        <label>Username:</label>
        <input type="text" name="username" required>
        
        <label>Password:</label>
        <input type="password" name="password" required>
        
        <button type="submit">Authorize</button>
      </form>
      
      <h3>Test Users:</h3>
      <ul>
        <li><b>admin</b> / admin123 (Full Access)</li>
        <li><b>user</b> / user123 (Write Access)</li>
        <li><b>readonly</b> / readonly123 (Read Only)</li>
      </ul>
    </body>
    </html>
  `);
});

app.post('/oauth/authorize', (req, res) => {
  const { client_id, redirect_uri, response_type, state, username, password } = req.body;
  
  const user = config.users.find(u => u.username === username && u.password === password);
  if (!user) {
    return res.status(401).send('Invalid credentials');
  }
  
  // Generate authorization code
  const code = crypto.randomBytes(32).toString('hex');
  
  res.redirect(`${redirect_uri}?code=${code}&state=${state}`);
});

// User Info Endpoint
app.get('/oauth/userinfo', (req, res) => {
  const authHeader = req.headers.authorization;
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({ error: 'missing_authorization' });
  }
  
  const token = authHeader.substring(7);
  try {
    const payload = JSON.parse(Buffer.from(token.split('.')[1], 'base64').toString());
    res.json({
      sub: payload.sub,
      roles: payload.roles,
      aud: payload.aud,
      iss: payload.iss
    });
  } catch (e) {
    res.status(401).json({ error: 'invalid_token' });
  }
});

// JWKS Endpoint (for OAuth2 resource servers)
app.get('/.well-known/jwks.json', (req, res) => {
  // Export public key as JWK
  const jwk = publicKey.export({ type: 'jwk', format: 'jwk' });
  
  res.json({
    keys: [{
      kty: 'RSA',
      kid: 'oauth-server-rsa-key',
      use: 'sig',
      alg: 'RS256',
      n: jwk.n,
      e: jwk.e
    }]
  });
});

// Discovery Endpoint
app.get('/.well-known/openid-configuration', (req, res) => {
  res.json({
    issuer: 'http://host.docker.internal:9999',
    authorization_endpoint: 'http://host.docker.internal:9999/oauth/authorize',
    token_endpoint: 'http://host.docker.internal:9999/oauth/token',
    userinfo_endpoint: 'http://host.docker.internal:9999/oauth/userinfo',
    jwks_uri: 'http://host.docker.internal:9999/.well-known/jwks.json',
    response_types_supported: ['code', 'token'],
    grant_types_supported: ['authorization_code', 'password', 'client_credentials'],
    token_endpoint_auth_methods_supported: ['client_secret_basic']
  });
});

// Start server
app.listen(PORT, () => {
  console.log('\n========================================');
  console.log('  OAuth2 Authorization Server Started');
  console.log('========================================');
  console.log(`\n📍 Server running on: http://localhost:${PORT}`);
  console.log('\n📋 Endpoints:');
  console.log(`  Token:        POST http://localhost:${PORT}/oauth/token`);
  console.log(`  Authorize:    GET  http://localhost:${PORT}/oauth/authorize`);
  console.log(`  User Info:    GET  http://localhost:${PORT}/oauth/userinfo`);
  console.log(`  JWKS:         GET  http://localhost:${PORT}/.well-known/jwks.json`);
  console.log(`  Discovery:    GET  http://localhost:${PORT}/.well-known/openid-configuration`);
  console.log('\n👥 Test Users:');
  config.users.forEach(u => {
    console.log(`  ${u.username} / ${u.password} (${u.roles.join(', ')})`);
  });
  console.log('\n========================================\n');
});
