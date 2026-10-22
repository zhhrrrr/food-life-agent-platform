import { spawn } from 'node:child_process'
import { randomUUID } from 'node:crypto'
import { mkdir, writeFile } from 'node:fs/promises'
import { preview } from 'vite'

// Run the production bundle through the CLI, without adding another test framework.
if (!process.env.npm_execpath) throw new Error('Run this audit with npm run test:ui')
const session = `foodlife-ui-${randomUUID().slice(0, 8)}`
const server = await preview({ logLevel: 'error', preview: { host: '127.0.0.1', port: 0, strictPort: true } })
const origin = `http://127.0.0.1:${server.httpServer.address().port}`

function cli(...args) {
  return new Promise((resolve, reject) => {
    const child = spawn(process.execPath, [process.env.npm_execpath, 'exec', '--yes', '--package=@playwright/cli@0.1.22', '--',
      'playwright-cli', '--raw', `-s=${session}`, ...args], { windowsHide: true, timeout: 180_000 })
    let output = '', errors = ''
    child.stdout.on('data', chunk => { output += chunk })
    child.stderr.on('data', chunk => { errors += chunk })
    child.on('error', reject)
    child.on('close', code => code === 0 ? resolve(output) : reject(new Error(output || errors || `Browser command exited ${code}`)))
  })
}

try {
  await mkdir('output/playwright', { recursive: true })
  await cli('open', `${origin}/login`)
  const output = await cli('run-code', '--filename', 'tests/browser/uiAudit.js')
  const line = output.split(/\r?\n/).find(value => value.startsWith('{"checks":'))
  if (!line) throw new Error(`Browser audit returned no verification result: ${output}`)
  const result = JSON.parse(line)
  await writeFile('output/playwright/verification.json', JSON.stringify({ ...result, origin, bundle: 'dist', fixtures: 'controlled HTTP/SSE' }, null, 2))
  const gallery = result.screenshots.map(path => {
    const file = path.split('/').at(-1)
    return `<figure><a href="${file}"><img src="${file}" alt="${file}" loading="lazy"></a><figcaption>${file}</figcaption></figure>`
  }).join('\n')
  await writeFile('output/playwright/index.html', `<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>FoodLife 界面验收</title><style>body{margin:32px;background:#f8f6f2;color:#302923;font:14px system-ui,sans-serif}h1{margin-bottom:8px}p{color:#756b62}main{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,360px),1fr));gap:24px}figure{margin:0;padding:16px;background:#fffefa;border:1px solid #e9e2d9;border-radius:20px}img{display:block;width:100%;max-height:600px;object-fit:contain;object-position:top}figcaption{margin-top:16px;overflow-wrap:anywhere}</style><h1>FoodLife 界面验收</h1><p>${result.routes} 个页面 · ${result.viewports.join(' / ')} px · ${result.count} 项浏览器检查通过。点击图片查看完整页面。</p><main>${gallery}</main></html>`)
  console.log(`Browser audit: ${result.count} checks passed; ${result.errors.length} uncaught errors.`)
  console.log('Screenshots and results: output/playwright/')
} finally {
  await cli('close').catch(error => console.error(error.message))
  await new Promise(resolve => server.httpServer.close(resolve))
}
