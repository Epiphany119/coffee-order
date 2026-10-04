import { createIdempotencyKey } from '@/api'

type CheckoutAttempt = {
  fingerprint: string
  key: string
}

const STORAGE_KEY = 'fika:pending-cart-checkout:v1'
let memoryAttempt: CheckoutAttempt | null = null

/**
 * Reuses a key for the same checkout payload, including after a page reload in the same tab.
 * Only a request digest and opaque key are stored; order notes and other request fields are not.
 */
export async function getPendingOrderIdempotencyKey(payload: unknown): Promise<string> {
  const serialized = JSON.stringify(payload) ?? 'null'
  const fingerprint = await fingerprintPayload(serialized)

  if (memoryAttempt?.fingerprint === fingerprint) {
    return memoryAttempt.key
  }

  const stored = readStoredAttempt()
  if (stored?.fingerprint === fingerprint) {
    memoryAttempt = stored
    return stored.key
  }

  const attempt = { fingerprint, key: createIdempotencyKey() }
  memoryAttempt = attempt
  try {
    window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(attempt))
  } catch {
    // Keep the key in memory for this page if storage is unavailable.
  }
  return attempt.key
}

export function clearPendingOrderIdempotencyKey(key: string): void {
  if (memoryAttempt?.key === key) {
    memoryAttempt = null
  }
  try {
    const stored = readStoredAttempt()
    if (stored?.key === key) {
      window.sessionStorage.removeItem(STORAGE_KEY)
    }
  } catch {
    // Storage may be unavailable in privacy-restricted browser contexts.
  }
}

async function fingerprintPayload(serialized: string): Promise<string> {
  try {
    if (typeof window !== 'undefined' && window.crypto?.subtle) {
      const digest = await window.crypto.subtle.digest('SHA-256', new TextEncoder().encode(serialized))
      const value = Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, '0')).join('')
      return 'sha256:' + value
    }
  } catch {
    // Use a deterministic non-cryptographic digest for retry matching when Web Crypto is unavailable.
  }
  return 'fallback:' + fallbackFingerprint(serialized)
}

function fallbackFingerprint(value: string): string {
  let first = 0xdeadbeef ^ value.length
  let second = 0x41c6ce57 ^ value.length
  for (let index = 0; index < value.length; index += 1) {
    const code = value.charCodeAt(index)
    first = Math.imul(first ^ code, 2654435761)
    second = Math.imul(second ^ code, 1597334677)
  }
  first = Math.imul(first ^ (first >>> 16), 2246822507)
    ^ Math.imul(second ^ (second >>> 13), 3266489909)
  second = Math.imul(second ^ (second >>> 16), 2246822507)
    ^ Math.imul(first ^ (first >>> 13), 3266489909)
  return (second >>> 0).toString(16) + (first >>> 0).toString(16) + ':' + value.length
}

function readStoredAttempt(): CheckoutAttempt | null {
  try {
    const raw = window.sessionStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const value = JSON.parse(raw) as Partial<CheckoutAttempt>
    if (typeof value.fingerprint !== 'string'
      || typeof value.key !== 'string'
      || !/^[A-Za-z0-9_-]{16,128}$/.test(value.key)) {
      window.sessionStorage.removeItem(STORAGE_KEY)
      return null
    }
    return { fingerprint: value.fingerprint, key: value.key }
  } catch {
    return null
  }
}