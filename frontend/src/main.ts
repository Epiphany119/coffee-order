import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import router from './router'
import App from './App.vue'
import './styles/global.scss'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(ElementPlus)
app.mount('#app')

// 通知小程序 WebView 已就绪，避免微信开发者工具模拟器 bindload 不触发导致蒙层卡住
// wx.miniProgram 只在小程序 WebView 内存在，浏览器内为 undefined，加保护
;(function notifyMiniProgramReady() {
  try {
    // @ts-ignore
    if (typeof wx !== 'undefined' && wx.miniProgram && typeof wx.miniProgram.postMessage === 'function') {
      // @ts-ignore
      wx.miniProgram.postMessage({ data: { type: 'h5-ready', ts: Date.now() } })
      // @ts-ignore
      wx.miniProgram.navigateBack && wx.miniProgram.navigateBack({ delta: 0 })
    }
  } catch (e) {
    // 浏览器环境或微信老版本无此 API，静默忽略
  }
})()
