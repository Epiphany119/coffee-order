# FIKA 咖啡点单系统 - 前端

基于 Vue 3 + Vite + TypeScript + Element Plus + Pinia 的咖啡店点单系统前端，包含**用户端点单**与**商家端后台**两套界面。

## 技术栈

- Vue 3.4 + Composition API（`<script setup>`）
- Vite（开发服务器 + 构建）
- TypeScript（vue-tsc 类型检查）
- Pinia（状态管理：`app.ts` 用户端 / `merchant.ts` 商家端）
- Element Plus（UI 组件库）
- Axios（HTTP 客户端，统一 Result 解包）
- SCSS（样式，CSS 变量主题）

## 项目结构

```
src/
├── api/                      # API 调用层
│   ├── index.ts              # axios 实例 + 用户端/商家端 API 分组
│   └── types.ts              # 全部 TS 类型（Product/Category/Order/Store...）
├── views/                    # 页面
│   ├── HomeView.vue          # 用户端主页（菜单/购物车/座位/会员入口）
│   ├── MemberView.vue        # 用户端会员中心页
│   ├── MerchantAuthView.vue  # 商家登录/入驻
│   ├── MerchantJoinView.vue  # 商家入驻引导
│   ├── MerchantLayout.vue    # 商家后台框架（侧边导航）
│   ├── MerchantHomeView.vue  # 商家端首页/引导
│   ├── MerchantDashboardView.vue  # 经营数据工作台
│   ├── MerchantOrdersView.vue     # 订单管理（接单/完成/取消）
│   ├── MerchantMenuView.vue       # 菜单管理（商品 CRUD/类目/图片上传）
│   ├── MerchantSeatsView.vue      # 座位管理（状态/换桌）
│   └── MerchantSettingsView.vue   # 店铺设置（营业状态等）
├── components/               # 用户端组件
│   ├── SiteHeader.vue        # 顶部导航（选店/登录）
│   ├── HeroSection.vue       # Hero 横幅
│   ├── MenuSection.vue       # 菜单区（分类 tab + 商品卡）
│   ├── ProductCard.vue       # 商品卡片（分类渐变底 + 图片）
│   ├── ProductModal.vue      # 商品定制弹窗（规格/温度/加料）
│   ├── CartPanel.vue         # 购物车面板
│   ├── OrderHistory.vue      # 订单历史
│   ├── MemberModal.vue       # 会员中心弹窗（旧入口，已迁移至 MemberView）
│   ├── SeatPanel.vue         # 取号/落座面板
│   ├── AuthPage.vue          # 登录/注册
│   └── FloatingRobot.vue     # 悬浮助手
├── stores/                   # Pinia
│   ├── app.ts                # 用户端全局：店铺/菜单/购物车/座位/身份
│   └── merchant.ts           # 商家端：登录态/当前店铺/菜单
├── templates/
│   └── merchantTemplates.ts  # 模板店（静安店）只读演示数据
├── router/index.ts           # 路由：/ 用户端，/merchant/* 商家端
├── styles/global.scss        # 全局样式与 CSS 变量
├── App.vue / main.ts         # 入口
```

## 快速开始

### 1. 环境配置

`.env.development.local`（可选）：`VITE_API_TARGET=http://localhost:8088`，不配置时自动探测本机局域网 IP 拼 8088（支持手机同网调试、WiFi 切换免改代码）。

### 2. 启动

```bash
npm install
npm run dev        # http://localhost:5174
```

**必须确保后端已启动**（8088 端口）。Vite 代理规则（`vite.config.ts`）：

| 前缀 | 转发目标 | 说明 |
|---|---|---|
| `/api` | 后端 | 全部业务接口 |
| `/uploads` | 后端 | 商品图片静态资源（后端 `{user.home}/coffee-uploads/` 映射）。**缺了这条图片会 404** |

### 3. 生产构建

```bash
npm run build      # 输出 dist/，vue-tsc 类型检查在 build 中执行
```

> 生产部署（nginx/宝塔）需同时配置 `/api` 反向代理与 `/uploads` 静态代理/转发，否则接口与图片均不可用。

## 功能说明

**用户端（`/`）**：登录/注册（含忘记密码）、游客点单（guestId 身份隔离）、多店切换（记忆上次选店）、菜单分类浏览（共享类目 + 店铺自定义类目 tab）、商品定制（规格小/中/大 + 定制规格 ml/g + 温度 + 加料）、购物车（优惠券/会员折扣实时计算）、下单（自取/堂食/备注）、订单历史、会员中心（等级进度/积分/收藏/卡券）、座位取号（二维码落座）、收藏（登录/游客双轨，登录后自动合并）。

**商家端（`/merchant/*`）**：商家注册入驻（一商一店，占位商家激活）、经营数据工作台（今日营业额/订单/近 7 天曲线）、订单管理（按店过滤、接单/完成/取消）、**菜单管理**（新增/编辑/下架商品、自定义类目创建、本地图片上传、共享商品编辑自动懒复制为本店专属）、座位管理（状态总览）、店铺设置（营业状态切换）。

**模板店**：静安店为只读模板店（`isTemplateStore`），菜单展示内置模板数据，隐藏全部增删改入口。

## 设计约定

- **分类渐变**：商品卡与弹窗按分类显示暖色渐变底（coffee/food/dessert/tea/ice 五类），图片 `object-fit: contain` + padding 完整显示（勿用 cover 裁切）；商品卡不设固定高度，防内容裁切；图片加载失败隐藏 `<img>` 露出渐变底与分类 emoji。
- **自定义类目**：非模板店从 `GET /store/{storeId}/categories` 加载类目列表；新建类目走 `POST /store/{storeId}/category`（name/icon），创建成功自动选中。
- **Result 解包**：axios 拦截器统一处理 `Result<T>` 与直返对象两种格式，业务代码只拿 `data`。

## 配套后端

配套后端：`../coffee-order-system-pro_back`（接口见其 `docs/API.md`，表结构见 `docs/DATABASE.md`）。先启动后端再启动前端。
