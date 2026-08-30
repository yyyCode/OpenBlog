/**
 * 设备指纹单例：基于 @fingerprintjs/fingerprintjs 计算稳定 visitorId。
 * visitorId 由浏览器硬件信号（Canvas/WebGL/字体/音频等）哈希得出，与 Cookie/存储无关，
 * 无痕窗口、清空存储后计算仍得同值。
 * sessionStorage 仅作"免重算"缓存——值来自计算而非存储，删掉重算同值，不破坏稳定性；
 * 用 session 而非 local，避免把标识符永久落盘，隐私友好。
 */
import FingerprintJS from '@fingerprintjs/fingerprintjs'

const STORAGE_KEY = 'dfp_visitor_id'

let fpPromise = null
let cached = null
let inflight = null

function load() {
  if (!fpPromise) {
    fpPromise = FingerprintJS.load()
  }
  return fpPromise
}

/**
 * @returns {Promise<string|null>} 设备指纹；计算失败/不可用（隐私模式禁 API 等）时返回 null，
 *   由调用方按"无指纹"处理（网关降级 IP 限流）。并发调用共享同一 in-flight promise。
 */
export async function getDeviceFingerprint() {
  if (cached) return cached
  if (!inflight) {
    inflight = compute()
  }
  return inflight
}

/** 计算并缓存设备指纹（内部单例，供 getDeviceFingerprint 共享）。 */
async function compute() {
  try {
    const stored = readStored()
    if (stored) {
      cached = stored
      return stored
    }
    const fp = await load()
    const result = await fp.get()
    cached = result.visitorId
    writeStored(cached)
    return cached
  } catch {
    // 加载/计算失败：重置 fpPromise 允许下次重试；本次返回 null 由调用方按无指纹处理
    fpPromise = null
    return null
  } finally {
    inflight = null
  }
}

function readStored() {
  try {
    return window.sessionStorage.getItem(STORAGE_KEY)
  } catch {
    return null
  }
}

function writeStored(value) {
  try {
    window.sessionStorage.setItem(STORAGE_KEY, value)
  } catch {
    // 隐私模式禁用存储 → 忽略，仅本次内存缓存
  }
}
