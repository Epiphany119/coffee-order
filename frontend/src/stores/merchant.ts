import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { merchantApi } from '@/api'
import type { MerchantResponse, StoreResponse } from '@/api/types'

/**
 * 商家会话（与用户端 app store 完全隔离）
 * 持久化：fikaMerchant（商家信息）/ fikaMerchantStore（已入驻店铺）
 */
export const useMerchantStore = defineStore('merchant', () => {
  const merchant = ref<MerchantResponse | null>(null)
  /** 已入驻的店铺（一商一店，null = 未入驻） */
  const joinedStore = ref<StoreResponse | null>(null)

  const isLoggedIn = computed(() => merchant.value?.success === true && merchant.value?.id != null)
  const hasJoined = computed(() => joinedStore.value != null)

  function setMerchant(m: MerchantResponse) {
    merchant.value = m
    try { localStorage.setItem('fikaMerchant', JSON.stringify(m)) } catch {}
  }

  function setJoinedStore(s: StoreResponse | null) {
    joinedStore.value = s
    try {
      if (s) localStorage.setItem('fikaMerchantStore', JSON.stringify(s))
      else localStorage.removeItem('fikaMerchantStore')
    } catch {}
  }

  function loadSession() {
    try {
      const raw = localStorage.getItem('fikaMerchant')
      if (raw) {
        const m = JSON.parse(raw)
        // 旧版会话没有 accessToken，继续复用只会让所有受保护接口返回 401/403，
        // 显式清除并要求重新登录，避免界面误显示为“没有数据”。
        if (m && typeof m === 'object' && m.id != null && m.merchantNo && m.accessToken) merchant.value = m
      }
      const rawStore = localStorage.getItem('fikaMerchantStore')
      if (rawStore) {
        const s = JSON.parse(rawStore)
        if (s && typeof s === 'object' && s.storeId != null) joinedStore.value = s
      }
    } catch {
      clear()
    }
  }

  function clear() {
    merchant.value = null
    joinedStore.value = null
    try {
      localStorage.removeItem('fikaMerchant')
      localStorage.removeItem('fikaMerchantStore')
    } catch {}
  }

  /**
   * 从后端恢复店铺绑定（登录后/刷新页面时本地 joinedStore 丢失，但 DB 中商家已绑定店铺）
   * @returns 是否已入驻（已入驻则 joinedStore 已恢复）
   */
  async function ensureJoinedStore(): Promise<boolean> {
    if (joinedStore.value) return true
    if (!merchant.value?.id) return false
    try {
      const stores = await merchantApi.myStores(merchant.value.id)
      if (stores && stores.length > 0) {
        setJoinedStore(stores[0])
        return true
      }
      setJoinedStore(null)
      return false
    } catch {
      return false
    }
  }

  return { merchant, joinedStore, isLoggedIn, hasJoined, setMerchant, setJoinedStore, loadSession, ensureJoinedStore, clear }
})
