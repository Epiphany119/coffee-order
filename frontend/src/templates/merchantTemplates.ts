import type { OrderRecord, Product, SeatResponse } from '@/api/types'

/**
 * 商家后台模板数据（静安店专属）
 *
 * 需求：静安店（store id=1）作为演示模板店，各页面展示一套内置示例数据
 * （菜单 12 商品 / 订单 15 单 / 座位 99 桌 / 仪表盘 KPI 与图表），
 * 不写入数据库，只在前端文件内置。其他店铺走真实接口数据。
 */
export const TEMPLATE_STORE_ID = 1

/** 当前店铺是否为模板店（静安店） */
export function isTemplateStore(storeId: number | null | undefined): boolean {
  return storeId != null && storeId === TEMPLATE_STORE_ID
}

// ============================================================
// 菜单模板：12 个商品（截图原样：咖啡 6 + 轻食 3 + 甜点 3）
// ============================================================

export const templateProducts: Product[] = [
  { id: 1, storeId: 1, code: 'latte', name: '经典拿铁', categoryCode: 'coffee', basePrice: 28, description: '浓缩咖啡与绵密奶泡的经典结合', imageUrl: '', temperature: 'HOT', available: true, allowedCondiments: [] },
  { id: 2, storeId: 1, code: 'oat-latte', name: '燕麦拿铁', categoryCode: 'coffee', basePrice: 26, description: '植物基燕麦奶，轻盈无负担', imageUrl: '', temperature: 'HOT', available: true, allowedCondiments: [] },
  { id: 3, storeId: 1, code: 'flat-white', name: '澳白', categoryCode: 'coffee', basePrice: 26, description: '双份浓缩，丝滑奶咖', imageUrl: '', temperature: 'HOT', available: true, allowedCondiments: [] },
  { id: 4, storeId: 1, code: 'pour-over', name: '手冲单品', categoryCode: 'coffee', basePrice: 38, description: '当季精选豆，风味纯净', imageUrl: '', temperature: 'HOT', available: true, allowedCondiments: [] },
  { id: 5, storeId: 1, code: 'iced-americano', name: '冰美式', categoryCode: 'coffee', basePrice: 18, description: '冰爽提神，夏日必备', imageUrl: '', temperature: 'COLD', available: true, allowedCondiments: [] },
  { id: 6, storeId: 1, code: 'cold-brew', name: '冷萃', categoryCode: 'coffee', basePrice: 22, description: '12 小时低温慢萃', imageUrl: '', temperature: 'COLD', available: false, allowedCondiments: [] },
  { id: 7, storeId: 1, code: 'croissant', name: '黄油可颂', categoryCode: 'food', basePrice: 22, description: '法国黄油，层层酥脆', imageUrl: '', temperature: 'HOT', available: true, allowedCondiments: [] },
  { id: 8, storeId: 1, code: 'panini', name: '火腿芝士帕尼尼', categoryCode: 'food', basePrice: 32, description: '现烤热压，拉丝芝士', imageUrl: '', temperature: 'HOT', available: true, allowedCondiments: [] },
  { id: 9, storeId: 1, code: 'caesar-salad', name: '鸡肉凯撒沙拉', categoryCode: 'food', basePrice: 36, description: '低卡高蛋白，清爽酱汁', imageUrl: '', temperature: 'HOT', available: true, allowedCondiments: [] },
  { id: 10, storeId: 1, code: 'basque', name: '巴斯克芝士', categoryCode: 'dessert', basePrice: 28, description: '外焦里嫩，芝士浓郁', imageUrl: '', temperature: 'HOT', available: true, allowedCondiments: [] },
  { id: 11, storeId: 1, code: 'brownie', name: '布朗尼', categoryCode: 'dessert', basePrice: 24, description: '醇厚可可，微苦回甘', imageUrl: '', temperature: 'HOT', available: true, allowedCondiments: [] },
  { id: 12, storeId: 1, code: 'lemon-tart', name: '柠檬挞', categoryCode: 'dessert', basePrice: 26, description: '清新酸甜，酥脆挞底', imageUrl: '', temperature: 'HOT', available: false, allowedCondiments: [] }
]

// ============================================================
// 订单模板：15 单（截图原样：待接单 1 / 制作中 2 / 已完成 11 / 已取消 1）
// ============================================================

export const templateOrders: OrderRecord[] = [
  { id: 48, storeId: 1, orderNo: '260805-000001-000-015',beverageName: '燕麦拿铁 x2、可颂 x1', size: '', condiments: '', originalPrice: 74, finalPrice: 74, status: 'PENDING', createdAt: '2026-08-05T19:47:00', fulfillmentType: 'DINE_IN', note: '静安店-018' },
  { id: 47, storeId: 1, orderNo: '260805-000001-000-014',beverageName: '燕麦拿铁 x2、可颂 x1', size: '', condiments: '', originalPrice: 58, finalPrice: 58, status: 'PREPARING', createdAt: '2026-08-05T19:42:00', fulfillmentType: 'DINE_IN', note: '静安店-018' },
  { id: 46, storeId: 1, orderNo: '260805-000001-000-013',beverageName: '冰美式 x1', size: '', condiments: '', originalPrice: 18, finalPrice: 18, status: 'PREPARING', createdAt: '2026-08-05T19:31:00', fulfillmentType: 'PICKUP', note: '外带' },
  { id: 45, storeId: 1, orderNo: '260805-000001-000-012',beverageName: '拿铁 x1、巴斯克 x1', size: '', condiments: '', originalPrice: 56, finalPrice: 56, status: 'COMPLETED', createdAt: '2026-08-05T19:20:00', fulfillmentType: 'DINE_IN', note: '静安店-032' },
  { id: 44, storeId: 1, orderNo: '260805-000001-000-011',beverageName: '澳白 x2、可颂 x2', size: '', condiments: '', originalPrice: 76, finalPrice: 76, status: 'COMPLETED', createdAt: '2026-08-05T19:12:00', fulfillmentType: 'DINE_IN', note: '静安店-005' },
  { id: 43, storeId: 1, orderNo: '260805-000001-000-010',beverageName: '冷萃 x3', size: '', condiments: '', originalPrice: 54, finalPrice: 54, status: 'COMPLETED', createdAt: '2026-08-05T18:58:00', fulfillmentType: 'PICKUP', note: '外带' },
  { id: 42, storeId: 1, orderNo: '260805-000001-000-009',beverageName: '手冲单品 x1', size: '', condiments: '', originalPrice: 38, finalPrice: 38, status: 'COMPLETED', createdAt: '2026-08-05T18:45:00', fulfillmentType: 'PICKUP', note: '外带' },
  { id: 41, storeId: 1, orderNo: '260805-000001-000-008',beverageName: '黄油可颂 x2、柠檬挞 x1', size: '', condiments: '', originalPrice: 70, finalPrice: 70, status: 'COMPLETED', createdAt: '2026-08-05T18:32:00', fulfillmentType: 'DINE_IN', note: '静安店-021' },
  { id: 40, storeId: 1, orderNo: '260805-000001-000-007',beverageName: '冰美式 x2', size: '', condiments: '', originalPrice: 36, finalPrice: 36, status: 'COMPLETED', createdAt: '2026-08-05T18:20:00', fulfillmentType: 'DINE_IN', note: '静安店-006' },
  { id: 39, storeId: 1, orderNo: '260805-000001-000-006',beverageName: '鸡肉凯撒沙拉 x1、布朗尼 x1', size: '', condiments: '', originalPrice: 60, finalPrice: 60, status: 'COMPLETED', createdAt: '2026-08-05T18:05:00', fulfillmentType: 'PICKUP', note: '外带' },
  { id: 38, storeId: 1, orderNo: '260805-000001-000-005',beverageName: '经典拿铁 x1', size: '', condiments: '', originalPrice: 28, finalPrice: 28, status: 'CANCELED', createdAt: '2026-08-05T17:52:00', fulfillmentType: 'PICKUP', note: '顾客取消' },
  { id: 37, storeId: 1, orderNo: '260805-000001-000-004',beverageName: '巴斯克芝士 x2', size: '', condiments: '', originalPrice: 56, finalPrice: 56, status: 'COMPLETED', createdAt: '2026-08-05T17:38:00', fulfillmentType: 'DINE_IN', note: '静安店-015' },
  { id: 36, storeId: 1, orderNo: '260805-000001-000-003',beverageName: '燕麦拿铁 x1、黄油可颂 x1', size: '', condiments: '', originalPrice: 48, finalPrice: 48, status: 'COMPLETED', createdAt: '2026-08-05T17:25:00', fulfillmentType: 'DINE_IN', note: '静安店-011' },
  { id: 35, storeId: 1, orderNo: '260805-000001-000-002',beverageName: '冷萃 x1、布朗尼 x1', size: '', condiments: '', originalPrice: 46, finalPrice: 46, status: 'COMPLETED', createdAt: '2026-08-05T17:10:00', fulfillmentType: 'PICKUP', note: '外带' },
  { id: 34, storeId: 1, orderNo: '260805-000001-000-001',beverageName: '澳白 x2', size: '', condiments: '', originalPrice: 52, finalPrice: 52, status: 'COMPLETED', createdAt: '2026-08-05T16:55:00', fulfillmentType: 'DINE_IN', note: '静安店-003' }
]

// ============================================================
// 座位模板：99 桌（双人 70 / 四人 20 / 多人 9；空闲 86 / 待落座 3 / 已占用 10）
// 001-070 双人桌、071-090 四人桌、091-099 多人桌
// ============================================================

/** 已占用桌号（截图：012/025/037/055/066/079/083/091 等 10 桌） */
const OCCUPIED_SEATS = new Set([12, 25, 37, 55, 66, 79, 83, 91, 42, 68])
/** 待落座桌号（截图：003/047/089） */
const ASSIGNED_SEATS = new Set([3, 47, 89])

function buildTemplateSeats(): SeatResponse[] {
  const seats: SeatResponse[] = []
  for (let no = 1; no <= 99; no++) {
    const capacity = no <= 70 ? 2 : no <= 90 ? 4 : 6
    const status = OCCUPIED_SEATS.has(no) ? 'OCCUPIED' : ASSIGNED_SEATS.has(no) ? 'ASSIGNED' : 'FREE'
    seats.push({
      seatId: no,
      storeId: TEMPLATE_STORE_ID,
      storeName: 'Fika・静安店',
      seatNo: String(no).padStart(3, '0'),
      code: `Fika・静安店-${String(no).padStart(3, '0')}`,
      capacity,
      status,
      assignedUserId: status === 'FREE' ? null : 4,
      assignedGuestId: null,
      assignedAt: status === 'ASSIGNED' ? '2026-08-06T12:30:00' : null,
      occupiedAt: status === 'OCCUPIED' ? '2026-08-06T11:20:00' : null,
      qrContent: null,
      qrBase64: null
    })
  }
  return seats
}

export const templateSeats: SeatResponse[] = buildTemplateSeats()

// ============================================================
// 仪表盘模板（截图原样）
// ============================================================

/** 统计卡：今日营业额 / 今日订单 / 待处理订单 / 座位占用率 */
export const templateStats = [
  { label: '今日营业额', value: '¥3286', delta: '+12.6%', up: true },
  { label: '今日订单', value: '47', delta: '+8 单', up: true },
  { label: '待处理订单', value: '3', delta: '需及时接单', up: false },
  { label: '座位占用率', value: '68%', delta: '31 桌空闲', up: false }
]

/** 近 7 日营业额（周一 ~ 周日） */
export const templateWeekSales = [
  { day: '周一', amount: 1680 },
  { day: '周二', amount: 2150 },
  { day: '周三', amount: 1980 },
  { day: '周四', amount: 2760 },
  { day: '周五', amount: 2430 },
  { day: '周六', amount: 3286 },
  { day: '周日', amount: 2890 }
]

/** 座位占用概览（仪表盘截图原样：31 空闲 / 4 待落座 / 64 已占用） */
export const templateSeatOverview = [
  { label: '空闲', count: 31, color: '#dedbd2' },
  { label: '待落座', count: 4, color: '#e0a24b' },
  { label: '已占用', count: 64, color: '#4caf7d' }
]

/** 近期订单（仪表盘截图 5 条：47 制作中 / 46-43 已完成） */
export const templateRecentOrders: OrderRecord[] = [
  { id: 47, storeId: 1, orderNo: '260805-000001-000-014',beverageName: '燕麦拿铁 x2、可颂 x1', size: '', condiments: '', originalPrice: 58, finalPrice: 58, status: 'PREPARING', createdAt: '2026-08-05T19:42:00', fulfillmentType: 'DINE_IN', note: '静安店-018' },
  { id: 46, storeId: 1, orderNo: '260805-000001-000-013',beverageName: '冰美式 x1', size: '', condiments: '', originalPrice: 18, finalPrice: 18, status: 'COMPLETED', createdAt: '2026-08-05T19:31:00', fulfillmentType: 'PICKUP', note: '外带' },
  { id: 45, storeId: 1, orderNo: '260805-000001-000-012',beverageName: '拿铁 x1、巴斯克 x1', size: '', condiments: '', originalPrice: 56, finalPrice: 56, status: 'COMPLETED', createdAt: '2026-08-05T19:20:00', fulfillmentType: 'DINE_IN', note: '静安店-032' },
  { id: 44, storeId: 1, orderNo: '260805-000001-000-011',beverageName: '澳白 x2、可颂 x2', size: '', condiments: '', originalPrice: 76, finalPrice: 76, status: 'COMPLETED', createdAt: '2026-08-05T19:12:00', fulfillmentType: 'DINE_IN', note: '静安店-005' },
  { id: 43, storeId: 1, orderNo: '260805-000001-000-010',beverageName: '冷萃 x3', size: '', condiments: '', originalPrice: 54, finalPrice: 54, status: 'COMPLETED', createdAt: '2026-08-05T18:58:00', fulfillmentType: 'PICKUP', note: '外带' }
]
