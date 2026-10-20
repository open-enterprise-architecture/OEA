// Renders the screen images of docs/screens/ from a static Storybook build (ADR-031 §3).
// For every screen story file (title "Screens/SCR-NNN <Name>") the first story is rendered in
// light and dark mode to SCR-NNN-light.png and SCR-NNN-dark.png; further stories of the file
// are variants and not rendered.
//
// Usage (in frontend/, after `npm run build-storybook`):
//   npx playwright install chromium   # once
//   npm run render:screens
import fs from 'node:fs'
import http from 'node:http'
import path from 'node:path'
import { chromium } from 'playwright'

const [root = 'storybook-static', out = '../docs/screens'] = process.argv.slice(2)
const VIEWPORT = { width: 1280, height: 800 }
const TYPES = {
  '.html': 'text/html',
  '.js': 'text/javascript',
  '.css': 'text/css',
  '.json': 'application/json',
  '.svg': 'image/svg+xml',
  '.woff2': 'font/woff2',
}

const index = JSON.parse(fs.readFileSync(path.join(root, 'index.json'), 'utf8'))
const screens = new Map()
for (const entry of Object.values(index.entries)) {
  const match = /^Screens\/(SCR-\d{3}) /.exec(entry.title)
  if (entry.type === 'story' && match && !screens.has(match[1])) {
    screens.set(match[1], entry.id)
  }
}
if (screens.size === 0) {
  console.error(`no screen stories in ${root}/index.json`)
  process.exit(1)
}

const server = http
  .createServer((request, response) => {
    const file = path.join(root, decodeURIComponent(new URL(request.url, 'http://localhost').pathname))
    const target = fs.existsSync(file) && fs.statSync(file).isFile() ? file : path.join(root, 'index.html')
    response.writeHead(200, { 'Content-Type': TYPES[path.extname(target)] ?? 'application/octet-stream' })
    fs.createReadStream(target).pipe(response)
  })
  .listen(0, '127.0.0.1')
await new Promise((resolve) => server.once('listening', resolve))
const base = `http://127.0.0.1:${server.address().port}`

const browser = await chromium.launch()
let failed = false
try {
  for (const [screen, id] of [...screens].sort()) {
    for (const colorScheme of ['light', 'dark']) {
      const page = await browser.newPage({ viewport: VIEWPORT, colorScheme, reducedMotion: 'reduce' })
      const errors = []
      page.on('pageerror', (error) => errors.push(error.message))
      await page.goto(`${base}/iframe.html?id=${id}&viewMode=story`)
      await page.waitForSelector('#storybook-root > *', { timeout: 15000 })
      await page.waitForLoadState('networkidle')
      await page.evaluate(() => document.fonts.ready)
      await page.waitForTimeout(500)
      const file = path.join(out, `${screen}-${colorScheme}.png`)
      await page.screenshot({ path: file, fullPage: true, animations: 'disabled' })
      if (errors.length > 0) {
        failed = true
        console.error(`${screen} ${colorScheme}: ${errors.join('; ')}`)
      } else {
        console.log(`${screen} ${colorScheme} → ${file}`)
      }
      await page.close()
    }
  }
} finally {
  await browser.close()
  server.close()
}
process.exit(failed ? 1 : 0)
