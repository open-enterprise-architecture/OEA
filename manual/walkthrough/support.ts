// Helpers of the walkthrough tests (change s2c-manual-release, design D1)

import { createHmac } from 'node:crypto'
import { mkdirSync, readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import type { Locator, Page } from '@playwright/test'

const manualDir = join(dirname(fileURLToPath(import.meta.url)), '..')
export const repositoryDir = join(manualDir, '..')
export const language = process.env.OEA_WALKTHROUGH_LANGUAGE || 'en'
export const backendUrl = process.env.OEA_WALKTHROUGH_BACKEND_URL || 'http://localhost:8080'

/**
 * Takes the screenshot `name` of the manual into manual/screenshots/<language>/<name>.png. Regions
 * that change from run to run (QR codes, secrets, timestamps) are passed in `mask`.
 */
export async function shoot(page: Page, name: string, mask: Locator[] = []): Promise<void> {
  if (!/^[a-z0-9-]+$/.test(name)) throw new Error(`invalid screenshot name ${name}`)
  await page.waitForLoadState('networkidle')
  await page.evaluate(() => document.fonts.ready)
  const path = join(manualDir, 'screenshots', language, `${name}.png`)
  mkdirSync(dirname(path), { recursive: true })
  await page.screenshot({ path, mask, animations: 'disabled', caret: 'hide' })
}

/** RFC 6238 code (SHA-1, 6 digits, 30 s) for a Base32 secret, as scripts/dev_seed.py computes it. */
export function totp(secret: string, offset = 0): string {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'
  let bits = ''
  for (const char of secret.replace(/=+$/, '').toUpperCase()) {
    bits += alphabet.indexOf(char).toString(2).padStart(5, '0')
  }
  const key = Buffer.from(bits.match(/.{8}/g)!.map((byte) => parseInt(byte, 2)))
  const counter = Buffer.alloc(8)
  counter.writeBigUInt64BE(BigInt(Math.floor(Date.now() / 30_000) + offset))
  const digest = createHmac('sha1', key).update(counter).digest()
  const start = digest[digest.length - 1] & 0x0f
  return String((digest.readUInt32BE(start) & 0x7fffffff) % 1_000_000).padStart(6, '0')
}

interface RealmUser {
  username: string
  credentials?: { type: string; value?: string; secretData?: string }[]
}

/** Password and, if present, TOTP secret of a user of the development realm (ADR-036 §4). */
export function realmUser(username: string): { password: string; otpSecret?: string } {
  const realm = JSON.parse(readFileSync(join(repositoryDir, 'dev', 'keycloak', 'realm-oea-dev.json'), 'utf8'))
  const user = (realm.users as RealmUser[]).find((candidate) => candidate.username === username)
  if (!user) throw new Error(`realm user ${username} not found`)
  const password = user.credentials?.find((credential) => credential.type === 'password')?.value
  const otp = user.credentials?.find((credential) => credential.type === 'otp')?.secretData
  if (!password) throw new Error(`realm user ${username} has no password`)
  return { password, otpSecret: otp ? JSON.parse(otp).value : undefined }
}
