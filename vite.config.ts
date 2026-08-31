import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'
import * as os from 'node:os'

// 支持多 IP 自动切换：
//   1. 优先从 .env.development.local 读 VITE_API_TARGET
//   2. 默认用当前主机 IP（auto-detect），保证同网段一定能用
//   3. 切换 WiFi 后无需改代码，重启 Vite 即可（Mac 自己的 IP 会变）
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const apiTarget = env.VITE_API_TARGET || `http://${getLocalIp()}:8088`

  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': resolve(__dirname, 'src')
      }
    },
    build: {
      rollupOptions: {
        output: {
          // 将稳定的第三方依赖从业务入口包拆出，浏览器可长期缓存，减少首屏更新下载量。
          manualChunks: {
            'vendor-vue': ['vue', 'vue-router', 'pinia'],
            'vendor-element': ['element-plus'],
            'vendor-http': ['axios']
          }
        }
      }
    },
    server: {
      port: 5174,
      // 固定端口：5174 被占用时直接报错而不是静默顺延端口（避免"昨天能访问、今天访问不了"的漂移问题）
      strictPort: true,
      host: '0.0.0.0',
      proxy: {
        '/api': {
          target: apiTarget,
          changeOrigin: true,
          // Agent 调用可能等待模型首包/平台限流恢复，不能在 10 秒时由开发代理提前断开。
          timeout: 60000,
          proxyTimeout: 60000,
          ws: false
        },
        // 商品图片静态资源（后端 {user.home}/coffee-uploads/ 映射 /uploads/**）
        '/uploads': {
          target: apiTarget,
          changeOrigin: true,
          timeout: 10000,
          proxyTimeout: 10000,
          ws: false
        }
      }
    }
  }
})

/**
 * 自动获取本机在当前网络下的 IPv4 地址（取第一个非 127.0.0.1 的 en0/en1）
 * 失败时回退 127.0.0.1
 */
function getLocalIp(): string {
  try {
    const nets = os.networkInterfaces()
    for (const name of Object.keys(nets)) {
      for (const net of nets[name] || []) {
        if (net.family === 'IPv4' && !net.internal) {
          return net.address
        }
      }
    }
  } catch {}
  return '127.0.0.1'
}
