export interface AuthRequest {
  username: string
  password: string
  nickname?: string
  challengeId?: string
  challengeCode?: string
}

export type EmailCodePurpose = 'LOGIN' | 'REGISTER'

export interface EmailCodeRequest {
  email: string
  purpose: EmailCodePurpose
}

export interface EmailCodeResponse {
  success: boolean
  message: string
  cooldownSeconds: number
}

export interface EmailAvailabilityResponse {
  success: boolean
  available: boolean
  bound: boolean
  message: string
}

export interface EmailLoginRequest {
  email: string
  code: string
}

export interface EmailBindRequest {
  email: string
  code: string
}

export interface UserPasswordUpdateRequest {
  /** 有密码时默认使用原密码验证；切换邮箱验证时不传。 */
  currentPassword?: string
  /** 必须是当前账号已经绑定的邮箱。 */
  email?: string
  emailCode?: string
  newPassword: string
  confirmPassword: string
}

export interface EmailRegisterRequest extends EmailLoginRequest {
  username: string
  password: string
  nickname?: string
}

export interface LoginChallenge {
  challengeId: string
  imageDataUrl: string
  expiresInSeconds: number
}

export interface AuthResponse {
  success: boolean
  message: string
  id: number | null
  /** 对外展示的 FIKA 账号号码；不等同于数据库自增 id。 */
  accountNo?: string | null
  username: string | null
  nickname: string | null
  totalSpent: number
  memberLevel: string | null
  avatarUrl?: string | null
  phone?: string | null
  birthday?: string | null
  wechatId?: string | null
  qqNumber?: string | null
  email?: string | null
  emails?: string[] | null
  /** 仅表示是否已设置密码，不包含任何密码敏感信息。 */
  passwordSet?: boolean
  otherInfo?: string | null
  accessToken?: string | null
}

export interface UserProfileUpdateRequest {
  nickname: string
  phone: string
  birthday: string | null
  wechatId: string
  qqNumber: string
  otherInfo: string
}

export interface StoreRecommendation {
  storeId: number
  code: string
  name: string
  address: string | null
  latitude: number
  longitude: number
  distanceKm: number
}

export interface ForgotPasswordRequest {
  username: string
}

export interface ResetPasswordRequest {
  token: string
  newPassword: string
}

export interface ForgotPasswordResponse {
  success: boolean
  message: string
  /** 仅在开发环境显式开启 expose-reset-token 时返回；生产环境通过站外渠道发送。 */
  token?: string
}

export interface AssignSeatRequest {
  /** 店铺 id（座位按店隔离，用户端当前店铺） */
  storeId?: number | null
  peopleCount: number
  userId?: number | null
  guestId?: string | null
}

export interface OccupySeatRequest {
  userId?: number | null
  guestId?: string | null
}

export type SeatStatus = 'FREE' | 'ASSIGNED' | 'OCCUPIED'

export interface SeatResponse {
  seatId: number
  /** 店铺 id（校验座位归属店铺） */
  storeId: number
  storeName: string
  seatNo: string
  code: string
  capacity: number
  status: SeatStatus
  /** 占用者用户 ID（null=无），用于归属校验 */
  assignedUserId: number | null
  /** 占用者游客 ID（null=无），用于归属校验 */
  assignedGuestId: string | null
  /** 分配时间（商家端展示） */
  assignedAt?: string | number[] | null
  /** 占用时间（商家端展示） */
  occupiedAt?: string | number[] | null
  qrContent: string | null
  qrBase64: string | null
}

export interface Product {
  id: number
  /** 归属店铺 id（按店隔离后商家端需要） */
  storeId?: number
  code: string
  name: string
  /** 分类外键（menu_category.id，与 categoryCode 一致） */
  categoryId?: number | null
  categoryCode: string
  basePrice: number
  /** 三档规格定价（商家可编辑；缺省时回退 basePrice） */
  priceSmall?: number | null
  priceMedium?: number | null
  priceLarge?: number | null
  description: string
  imageUrl: string
  temperature: string
  /** 是否上架（商家端可下架，用户端只显示上架商品） */
  available?: boolean
  allowedCondiments: string[]
  /** 定制规格单位（coffee/tea/ice → ml，dessert/food → g） */
  customUnit?: string
  /** 凑单标记：1=凑单推荐品（配料/小料/小饮品/试吃品） */
  topup?: number
}

export interface Category {
  /** 类目 id（menu_category.id） */
  id?: number
  code: string
  name: string
  icon: string
  /** 归属：0=共享类目，N=商家自定义类目 */
  storeId?: number
}

export interface MenuResponse {
  products: Product[]
  sizes: string[]
  observers: string[]
  /** 定制规格计价规则（基准量：饮品 ml / 甜点轻食 g） */
  customRule?: { baseMl: number; baseG: number }
  /** 店铺可见类目：共享类目 + 该店自定义类目 */
  categories?: Category[]
}

export interface FlashSaleActivity {
  id: number
  productCode: string
  title: string
  flashPrice: number
  availableStock: number
  startAt: string
  endAt: string
  /** 当前是否仍可参与：售罄或活动结束后为 false，但仍保留卡片给用户查看。 */
  claimable?: boolean
  closeReason?: 'SOLD_OUT' | 'ENDED'
}

export interface FlashSaleClaim {
  productCode: string
  flashPrice: number
  claimNo: string
  message: string
}

export interface FlashSaleClaimRecord {
  claimNo: string
  status: 'CLAIMED' | 'USED' | 'EXPIRED'
  claimedAt: string | number[]
  expiresAt: string | number[]
  productCode: string
  title: string
  flashPrice: number
}

export interface GrowthAgentSignal {
  label: string
  value: string
  note: string
}

export interface GrowthAgentProposal {
  actionType: 'NOTIFY_MEMBERS' | 'CREATE_VOUCHERS'
  title: string
  summary: string
  reason: string
  proposal: Record<string, unknown>
}

export interface GrowthAgentAnalysis {
  answer: string
  signals: GrowthAgentSignal[]
  understanding?: string
  dataSources?: string[]
  executionPlan?: string[]
  toolCalls?: GrowthAgentToolCall[]
  requiresConfirmation?: boolean
  planningMode?: string
  analysisId?: string
  suggestedAction: GrowthAgentProposal
  snapshot: { todayOrders: number; todayRevenue: number; pendingOrders: number; weekRevenue: number; flashSaleStock: number }
  storeId: number
  storeName: string
  engine: string
  disclaimer: string
}

export interface GrowthAgentToolCall {
  name: string
  readOnly: boolean
  status: string
  latencyMs: number
  resultCount: number
  note: string
}

export interface BusinessAgentPlanStep {
  tool: string
  arguments: Record<string, unknown>
  purpose: string
}

export interface BusinessAgentPlan {
  intent: string
  steps: BusinessAgentPlanStep[]
  requiresConfirmation: boolean
  rationale: string
  source: string
}

export interface BusinessAgentToolResult {
  name: string
  success: boolean
  data: Record<string, unknown>[]
  note: string
  callId: string
  latencyMs: number
  readOnly: boolean
  arguments: Record<string, unknown>
}

export interface BusinessAgentAnswer {
  sessionId: string
  plan: string[]
  tools: BusinessAgentToolResult[]
  answer: string
  engine: string
  runId: string
  structuredPlan: BusinessAgentPlan
}

export interface GrowthAgentAction {
  id: number
  actionType: string
  title: string
  status: 'PENDING' | 'EXECUTING' | 'EXECUTED' | 'CANCELED' | 'FAILED'
  createdAt: string | number[]
  executedAt?: string | number[] | null
}

export interface CustomerAgentItem {
  productCode: string
  name: string
  description?: string
  imageUrl?: string
  categoryCode: string
  temperature: string
  size: string
  quantity: number
  estimatedPrice: number
  reason: string
}

export interface CustomerAgentOption {
  title: string
  items: CustomerAgentItem[]
  planToken: string
  promotion?: CustomerAgentPromotion
}

export interface CustomerAgentPromotion {
  type: 'QUALIFIED' | 'NEAR' | 'NONE'
  text: string
  threshold: number
  amount: number
  /** 服务端已封装在当前计划令牌里的真实凑单项，不由浏览器自行决定商品。 */
  suggestedItems?: CustomerAgentItem[]
  canAddOn?: boolean
}

export interface CustomerAgentPlan {
  reply: string
  items: CustomerAgentItem[]
  options?: CustomerAgentOption[]
  signals: { label: string; value: string; used: boolean }[]
  /** 服务端从自然语言中解析出的、不可由模型文案覆盖的业务约束。 */
  understanding?: string
  promotion?: CustomerAgentPromotion
  note: string
  engine?: string
  storeName?: string
  planToken: string
  /** 本次点单 Agent 运行编号，用于把最终下单结果回写到观测轨迹。 */
  runId?: string
  expiresInSeconds: number
}

export type CustomerAssistantActionType =
  | 'ORDER_PLAN'
  | 'VIEW_ORDERS'
  | 'OPEN_FEEDBACK'
  | 'OPEN_AFTER_SALE'
  | 'CHOOSE_FEEDBACK_ORDER'
  | 'NONE'

export interface CustomerAssistantAction {
  type: CustomerAssistantActionType | string
  label?: string
  payload?: Record<string, any>
}

export interface CustomerAssistantResponse {
  sessionId: string
  runId: string
  route: 'CONSULT' | 'ORDER' | 'ORDER_QUERY' | 'FEEDBACK' | 'UNSAFE' | string
  answer: string
  engine: string
  action?: CustomerAssistantAction
  memorySignals?: string[]
}

export interface CartItemRequest {
  productCode: string
  size: string
  /** 定制尺寸输入（如 "300"），size=CUSTOM 时有效 */
  customSize?: string
  condiments: string[]
  quantity: number
}

export interface OrderRequest {
  items: CartItemRequest[]
  userId: number | null
  guestId: string | null
  /** 下单店铺 id（用户端当前店铺） */
  storeId: number | null
  couponCode?: string | null
  /** 秒杀抢购码；仅允许单件单独结算，不可叠加优惠券 */
  flashSaleClaimNo?: string | null
  fulfillmentType: string
  /** 外卖配送收货地址 id；仅 fulfillmentType=DELIVERY 时提交 */
  deliveryAddressId?: number | null
  note?: string
}

export interface OrderItem {
  productId?: number
  /** 订单明细列表和下单响应都会返回商品快照信息。 */
  productCode?: string
  /** 下单时保存的商品图片快照；历史订单由迁移脚本回填。 */
  imageUrl?: string | null
  beverageName: string
  categoryCode?: string
  size?: string
  condiments?: string
  quantity: number
  /** 单件折后价 */
  unitPrice: number
  /** 单件原价（折前，划线展示用；老数据可能为 null） */
  originalUnitPrice?: number
  /** 行小计（折后） */
  subtotal: number
}

export interface OrderResponse {
  id: number
  orderId?: number
  /** 支付单号（下单时由支付模块创建，供拉起支付） */
  paymentNo?: string
  /** 外卖配送单 id；非外卖订单为空 */
  deliveryOrderId?: number | null
  beverageName: string
  originalPrice: number
  finalPrice: number
  pricingStrategy: string
  status: string
  message: string
  totalSpent: number
  memberLevel: string
  totalCups: number
  items: OrderItem[]
  categoryCode: string
  memberDiscount: number
  couponDiscount: number
  couponName: string
  earnedPoints: number
}

export interface OrderRecord {
  id: number
  /** 详细订单号：YYMMDD-商家6位-类目3位-店铺当日顺序3位（如 260807-687257-001-001） */
  orderNo?: string
  storeId?: number
  beverageName: string
  size: string
  /** 定制尺寸输入（如 "300"），size=CUSTOM 时有效 */
  customSize?: string
  condiments: string
  originalPrice: number
  finalPrice: number
  status: string
  createdAt: string | number[]
  categoryCode?: string
  /** user=用户订单 / guest=游客订单 */
  orderType?: string
  /** PICKUP 到店自取 / DINE_IN 店内用餐 / DELIVERY 外卖配送 */
  fulfillmentType?: string
  note?: string
  estimatedReadyTime?: string
  /** 订单明细（order_item），批量订单 = 1 单 N 明细，每行是购物车行级 */
  items?: OrderItem[]
}

// ============================================================
// 外卖模块
// ============================================================

export interface DeliveryAddressRequest {
  /** 地址标签，如：家、公司、学校 */
  label: string
  receiverName: string
  receiverPhone: string
  detailAddress: string
  isDefault?: boolean
}

export interface DeliveryAddress {
  id: number
  label: string
  receiverName: string
  receiverPhone: string
  detailAddress: string
  isDefault: boolean
  createdAt?: string | number[]
  updatedAt?: string | number[]
}

export type DeliveryOrderStatus = 'WAITING_MERCHANT' | 'OPEN' | 'CLAIMED' | 'PICKED_UP' | 'DELIVERING' | 'DELIVERED' | 'CANCELED'

export interface DeliveryOrderItem {
  productCode?: string | null
  beverageName: string
  imageUrl?: string | null
  size?: string | null
  condiments?: string | null
  quantity: number
  unitPrice?: number | null
  originalUnitPrice?: number | null
  subtotal?: number | null
}

export interface DeliveryOrder {
  id: number
  deliveryOrderId: number
  orderId: number
  orderNo: string
  userId?: number
  storeId: number
  storeName: string
  amount: number
  itemSummary: string
  /** 下单时保存的商品图片快照，历史配送单也会由迁移脚本回填。 */
  items?: DeliveryOrderItem[]
  note?: string | null
  addressLabel: string
  receiverName: string
  /** 顾客订单可见；骑手接口固定返回 null，避免暴露真实电话。 */
  receiverPhone?: string | null
  detailAddress: string
  status: DeliveryOrderStatus | string
  statusLabel: string
  riderId?: number | null
  riderName?: string | null
  createdAt: string | number[]
  claimedAt?: string | number[] | null
  pickedUpAt?: string | number[] | null
  deliveredAt?: string | number[] | null
  updatedAt?: string | number[] | null
}

export interface UserNotification {
  id: number
  type: string
  title: string
  content: string
  readStatus?: number | boolean
  /** 订单状态通知关联的主订单 id；营销类通知为空。 */
  orderId?: number | null
  createdAt: string | number[]
}

export interface DeliveryRiderLoginRequest {
  username: string
  password: string
}

export interface DeliveryRiderRegisterRequest extends DeliveryRiderLoginRequest {
  nickname?: string
  phone?: string
}

export interface DeliveryRiderResponse {
  success: boolean
  message: string
  id: number | null
  username: string | null
  nickname: string | null
  phone: string | null
  avatarUrl?: string | null
  birthday?: string | null
  email?: string | null
  otherInfo?: string | null
  status: string | null
  accessToken?: string | null
}

export interface DeliveryRiderProfileUpdateRequest {
  nickname: string
  phone: string
  birthday: string | null
  email: string
  otherInfo: string
}

export type DeliveryPerformanceRange = '7d' | '14d' | '28d' | '12w'

/** 配送员个人业绩；金额是已送达配送单的订单金额，不等同于骑手收入。 */
export interface DeliveryRiderPerformance {
  range: DeliveryPerformanceRange | string
  rangeLabel: string
  bucket: 'DAY' | 'WEEK' | string
  rangeAssigned: number
  rangeDelivered: number
  rangeAmount: number
  averageOrderAmount: number
  todayAssigned: number
  todayDelivered: number
  activeOrders: number
  weekDelivered: number
  totalDelivered: number
  totalDeliveredAmount: number
  deliveryFeeConfigured: boolean
  deliveryFeeLabel: string
  daily: Array<{ day: string; delivered: number; amount: number }>
}

/** 虚拟电话中介会话；当前为未配置占位状态，不包含真实号码。 */
export interface VirtualCallResponse {
  status: 'NOT_CONFIGURED' | 'READY' | 'STARTED' | string
  relayId: string
  provider: string
  oneTime: boolean
  dialable: boolean
  message: string
  expiresAt: string | number[] | null
}

/** 售后单 */
export interface AfterSaleRecord {
  id: number
  /** 关联订单 id（取餐号） */
  orderId: number
  /** 订单详细订单号 */
  orderNo?: string
  /** 订单商品名快照 */
  orderName?: string
  /** 售后类型：REFUND 退款 / REMAKE 重做 / EXCHANGE 换货 / OTHER 其他 */
  type: string
  reason: string
  /** 状态：PENDING 待处理 / PROCESSING 处理中 / RESOLVED 已解决 / REJECTED 已拒绝 / CLOSED 已关闭 */
  status: string
  /** 商家处理备注 */
  handlerNote?: string | null
  createdAt: string
  updatedAt?: string
}

/** 订单反馈 */
export interface FeedbackRecord {
  id: number
  orderId: number
  /** 反馈归属商品 id（订单第一个明细的商品） */
  productId?: number
  orderNo?: string
  orderName?: string
  /** 反馈用户名（来自 coffee_user.username） */
  username?: string
  content: string
  rating?: number | null
  createdAt: string
}

export const AFTER_SALE_TYPE_LABELS: Record<string, string> = {
  REFUND: '退款', REMAKE: '重做', EXCHANGE: '换货', OTHER: '其他'
}

export const AFTER_SALE_STATUS_LABELS: Record<string, string> = {
  PENDING: '待处理', PROCESSING: '处理中', RESOLVED: '已解决', REJECTED: '已拒绝', CLOSED: '已关闭'
}

export interface Coupon {
  code: string
  name: string
  minimum: number
  discount: number
  description: string
}

/** 卡券包里的券（积分兑换所得） */
export interface Voucher {
  id: number
  voucherNo: string
  name: string
  discount: number
  minimum: number
  status: number
  source: string
  createdAt: string
  expiresAt?: string | null
}

/** 积分兑换项（接口 /membership/redeem-items 下发） */
export interface RedeemItem {
  code: string
  name: string
  costPoints: number
  /** 拆分明细：兑换后按张发放 */
  grants?: { name: string; discount: number; count: number }[]
}

/** 积分兑换结果 */
export interface RedeemResult {
  itemCode: string
  itemName: string
  costPoints: number
  remainingPoints: number
  vouchers: Voucher[]
  message: string
}

export interface MemberDashboard {
  nickname: string
  totalSpent: number
  /** 累计已省金额（已完成订单 原价-实付 之和） */
  totalSaved: number
  memberLevel: string
  points: number
  pointsLevel: string
  nextThreshold: number
  amountToNext: number
  progress: number
  coupons: Coupon[]
}

export interface CartItem {
  productCode: string
  productName: string
  categoryCode: string
  size: string
  /** 定制尺寸输入（如 "300"），size=CUSTOM 时有效 */
  customSize?: string
  condiments: string[]
  quantity: number
  unitPrice: number
}

export interface CondimentOption {
  code: string
  name: string
  price: number
}

export const CONDIMENTS: Record<string, CondimentOption> = {
  mocha:       { code: 'mocha',        name: '摩卡',        price: 6 },
  whip:        { code: 'whip',          name: '奶油',        price: 4 },
  caramel:     { code: 'caramel',       name: '焦糖',        price: 5 },
  vanilla:     { code: 'vanilla',       name: '香草',        price: 3 },
  ice:         { code: 'ice',           name: '加冰',        price: 2 },
  oat_milk:    { code: 'oat_milk',     name: '燕麦奶',      price: 4 },
  taro_ball:   { code: 'taro_ball',    name: '芋圆',        price: 5 },
  coconut:     { code: 'coconut',       name: '椰果',        price: 3 },
  cheese_foam: { code: 'cheese_foam',  name: '芝士奶盖',    price: 6 },
  extra_sugar: { code: 'extra_sugar',  name: '加糖',        price: 1 }
}

export const SIZE_LABELS: Record<string, string> = {
  SMALL: '小份',
  MEDIUM: '中份',
  LARGE: '大份',
  CUSTOM: '定制'
}

/** 定制规格单位：coffee/tea/ice → ml（毫升），dessert/food → g（克） */
export function customUnitOf(categoryCode?: string): string {
  return categoryCode === 'coffee' || categoryCode === 'tea' || categoryCode === 'ice' ? 'ml' : 'g'
}

/** 规格展示文本：定制显示为"定制 300ml"，其余按 SIZE_LABELS */
export function sizeText(o: { size?: string; customSize?: string; categoryCode?: string }): string {
  if (o.size === 'CUSTOM') {
    return o.customSize ? `定制 ${o.customSize}${customUnitOf(o.categoryCode)}` : '定制'
  }
  return SIZE_LABELS[o.size || 'MEDIUM'] || o.size || ''
}

export const SIZE_EXTRAS: Record<string, Record<string, number>> = {
  coffee:   { SMALL: 0, MEDIUM: 2, LARGE: 4 },
  tea:      { SMALL: 0, MEDIUM: 2, LARGE: 4 },
  dessert:  { SMALL: 0, MEDIUM: 1, LARGE: 2 },
  food:     { SMALL: 0, MEDIUM: 1, LARGE: 2 },
  ice:      { SMALL: 0, MEDIUM: 2, LARGE: 4 }
}

export const CATEGORY_META: Record<string, [string, string]> = {
  coffee:   ['咖啡',   '☕'],
  tea:      ['茶饮',   '🍵'],
  dessert:  ['甜点',   '🍰'],
  food:     ['轻食',   '🥪'],
  ice:      ['冰沙',   '🧊']
}

export const STATUS_LABELS: Record<string, string> = {
  UNPAID:    '待支付',
  PENDING:   '等待商家接单',
  ACCEPTED:  '商家已接单，等待制作',
  PREPARING: '商家制作中',
  READY_FOR_DELIVERY: '商家制作完毕，待骑手接单',
  RIDER_ASSIGNED: '骑手已接单',
  DELIVERING: '骑手配送中',
  DELIVERED:  '骑手已送达，请取餐',
  COMPLETED:  '已完成',
  CANCELED:   '已取消'
}

// ============================================================
// 支付模块
// ============================================================

/** 支付渠道 */
export type PayChannel = 'WECHAT' | 'ALIPAY' | 'BANK' | 'MOCK'

export const PAY_CHANNEL_LABELS: Record<string, string> = {
  WECHAT: '微信支付',
  ALIPAY: '支付宝',
  BANK:   '银行卡支付',
  MOCK:   '模拟支付'
}

export const PAY_STATUS_LABELS: Record<string, string> = {
  PENDING:  '待支付',
  PAID:     '已支付',
  FAILED:   '支付失败',
  CLOSED:   '已关闭',
  REFUNDED: '已退款'
}

export interface PaymentRecord {
  paymentId: number
  paymentNo: string
  orderId: number
  userId: number | null
  channel: PayChannel | null
  amount: number
  status: string
  statusDesc: string
  transactionNo: string | null
  paidAt: string | null
  createdAt: string
}

/** 凑单进度（购物袋满减进度条数据） */
export interface TopupProgress {
  /** 是否已满足全部满减门槛（true 时无需凑单） */
  reached: boolean
  /** 目标门槛金额（最近一张未达成的满减券门槛） */
  threshold: number
  /** 还差金额 = threshold - amount */
  gap: number
  /** 目标券名（如"下午茶立减 ¥8"） */
  couponName: string
  /** 目标券编码（固定权益券 FIKA8/SWEET12/BEAN15） */
  couponCode: string
  /** 目标券面额 */
  discount: number
}

// ============================================================
// 店铺 / 商家模块
// ============================================================

export type StoreStatus = 'OPEN' | 'CLOSED'

export interface StoreResponse {
  storeId: number
  code: string
  name: string
  address: string | null
  phone: string | null
  businessHours: string | null
  status: StoreStatus
  merchantId: number | null
}

export interface StoreRequest {
  code?: string
  name?: string
  address?: string
  phone?: string
  businessHours?: string
  status?: StoreStatus
  merchantId?: number
}

export type MerchantStatus = 'ACTIVE' | 'DISABLED'

export interface MerchantResponse {
  success: boolean
  message: string
  id: number
  /** 商家编号 sj-开头（登录账号） */
  merchantNo: string
  nickname: string | null
  phone: string | null
  avatarUrl?: string | null
  operatorName?: string | null
  email?: string | null
  businessLicenseNo?: string | null
  businessLicenseUrl?: string | null
  otherInfo?: string | null
  /** 绑定的店名（入驻后非空） */
  storeName?: string | null
  status: MerchantStatus
  accessToken?: string | null
}

export interface MerchantRegisterRequest {
  /** 用户端账号（coffee_user.username），须已注册且未注册过商家 */
  username: string
  /** 与用户端登录密码一致 */
  password: string
  nickname?: string
  phone?: string
  /** 入驻现有店铺时必填（激活该店预分配的占位商家记录并绑定）；为空 = 开新店模式 */
  storeId?: number
}

export interface MerchantLoginRequest {
  merchantNo: string
  password: string
  challengeId?: string
  challengeCode?: string
}

export interface MerchantProfileUpdateRequest {
  nickname?: string
  phone?: string
  operatorName?: string
  email?: string
  businessLicenseNo?: string
  otherInfo?: string
}

/** 商家端菜单新增/编辑请求（对应后端 MenuItemRequest） */
export interface StoreMenuRequest {
  code?: string
  name: string
  /** 分类外键（menu_category.id；新建类目后提交新类目 id） */
  categoryId?: number | null
  categoryCode: string
  basePrice: number
  /** 三档规格定价（缺省回退 basePrice） */
  priceSmall?: number | null
  priceMedium?: number | null
  priceLarge?: number | null
  description?: string
  imageUrl?: string
  temperature?: string
  available: boolean
}

/** 商家创建自定义类目请求（对应后端 MenuCategoryRequest） */
export interface CategoryRequest {
  name: string
  icon?: string
}

/** 商家端工作台数据（GET /api/merchant/{id}/dashboard?range=7d|14d|28d|12w） */
export interface MerchantDashboard {
  store: StoreResponse
  todayRevenue: number
  todayOrders: number
  pendingOrders: number
  /** 营业额柱状图序列：day=YYYYMMDD（12w 为周起日期），缺日/周已补 0 */
  sales: { day: string; amount: number }[]
  /** 近 7 天已完成订单热销榜 */
  hotProducts: { name: string; quantity: number; amount: number }[]
  recentOrders: OrderRecord[]
}
