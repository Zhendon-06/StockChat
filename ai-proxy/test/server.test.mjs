import test from 'node:test';
import assert from 'node:assert/strict';

// Smoke-test the repository contract without contacting DashScope.
test('proxy package exposes the expected runtime entrypoint', async () => {
  const packageJson = await import('../package.json', { with: { type: 'json' } });
  assert.equal(packageJson.default.type, 'module');
  assert.equal(packageJson.default.scripts.start, 'node src/server.mjs');
});
