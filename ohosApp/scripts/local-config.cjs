const fs = require('node:fs');
const path = require('node:path');

const KEYS = {
    QWEN_API_KEY: 'qwenApiKey',
    MIMO_VOICE_API_KEY: 'mimoVoiceApiKey',
    AI_PROXY_BASE_URL: 'aiProxyBaseUrl',
    AI_PROXY_TOKEN: 'aiProxyToken',
};

function readProperties(content) {
    const values = {};
    let pending = '';
    for (const physicalLine of content.split(/\r?\n/)) {
        const line = pending + physicalLine.trimStart();
        const slashes = line.match(/\\+$/)?.[0].length || 0;
        if (slashes % 2) {
            pending = line.slice(0, -1);
            continue;
        }
        pending = '';
        const match = line.match(/^(QWEN_API_KEY|MIMO_VOICE_API_KEY|AI_PROXY_BASE_URL|AI_PROXY_TOKEN)(?:\s*[=:]\s*|\s+)(.*)$/);
        if (!match) continue;
        values[match[1]] = match[2].replace(/\\u([\da-fA-F]{4})|\\(.)/g, (_, unicode, escaped) =>
            unicode ? String.fromCharCode(parseInt(unicode, 16)) :
                ({ t: '\t', n: '\n', r: '\r', f: '\f' }[escaped] || escaped)).trim();
    }
    return values;
}

function generateLocalConfig({ projectRoot, outputPath, buildMode, environment }) {
    const config = {};
    if (buildMode === 'debug') {
        const propertiesPath = path.join(projectRoot, 'local.properties');
        const local = fs.existsSync(propertiesPath) ? readProperties(fs.readFileSync(propertiesPath, 'latin1')) : {};
        for (const [key, parameter] of Object.entries(KEYS)) {
            config[parameter] = (environment[key] || '').trim() || local[key] || '';
        }
    }
    fs.mkdirSync(path.dirname(outputPath), { recursive: true });
    // Always replace, including Release, to prevent retaining stale Debug keys.
    fs.writeFileSync(outputPath, JSON.stringify(config), { mode: 0o600 });
    fs.chmodSync(outputPath, 0o600);
}

module.exports = { readProperties, generateLocalConfig };
