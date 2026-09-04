<script setup lang="ts">
import { computed } from 'vue'
import { useAppStore } from '@/stores/app'
import { CONDIMENTS } from '@/api/types'

const props = defineProps<{ modelValue: boolean }>()

const store = useAppStore()

const m = computed(() => store.memberDashboard)

function fmtMoney(v: number) {
  return `¥${Number(v || 0).toFixed(2)}`
}

const emit = defineEmits<{
  'update:modelValue': [v: boolean]
  'select-coupon': [coupon: any]
}>()

/** 选择优惠券：通知父组件并关闭弹窗 */
function chooseCoupon(c: any) {
  emit('select-coupon', c)
  emit('update:modelValue', false)
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="会员中心"
    width="620px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-if="m" class="member-content">
      <section class="member-hero">
        <p class="eyebrow">FIKA MEMBERSHIP</p>
        <h2>{{ m.memberLevel }}</h2>
        <p>{{ m.nickname }}，每一次好好喝咖啡都值得被记录。</p>
        <div class="point-balance">
          <div><b>{{ m.points }}</b><small>可用积分</small></div>
          <div><b>{{ fmtMoney(m.totalSpent) }}</b><small>累计消费<em>已省 {{ fmtMoney(m.totalSaved || 0) }}</em></small></div>
          <div><b>{{ m.pointsLevel }}</b><small>积分等级</small></div>
        </div>
      </section>

      <section class="member-progress">
        <div class="member-progress-top">
          <b>会员成长</b>
          <span>{{ m.progress }}%</span>
        </div>
        <div class="progress-track">
          <span :style="{ width: Math.min(100, m.progress) + '%' }"></span>
        </div>
        <p v-if="m.amountToNext > 0">
          再消费 {{ fmtMoney(m.amountToNext) }}，解锁 {{ m.nextThreshold === 500 ? 'SVIP 9 折' : 'VIP 95 折' }}。
        </p>
        <p v-else>已解锁最高会员折扣，感谢你的长期陪伴。</p>
      </section>

      <h3 class="coupon-title">会员权益券</h3>
      <div class="member-coupons">
        <div
          v-for="c in m.coupons"
          :key="c.code"
          class="member-coupon"
          @click="chooseCoupon(c)"
        >
          <div>
            <b>{{ c.name }}</b>
            <small>{{ c.description }}</small>
          </div>
          <span>立即使用 →</span>
        </div>
        <p v-if="!m.coupons?.length" class="no-coupon">暂无可用优惠券，多喝几杯就有啦</p>
      </div>
    </div>
    <div v-else class="member-empty">
      <p>正在加载会员权益…</p>
      <small>若长时间无响应，请确认已登录会员账号</small>
    </div>
  </el-dialog>
</template>

<style lang="scss" scoped>
.member-content { padding: 0; }

.member-hero {
  background: linear-gradient(130deg, #203a2e, #44614f);
  color: #fff;
  border-radius: 16px;
  padding: 24px;
  position: relative;
  overflow: hidden;
  &::after {
    content: "✦";
    font-size: 120px;
    position: absolute;
    right: 10px;
    top: -24px;
    color: rgba(255,255,255,0.08);
  }
}

.eyebrow {
  font-size: 11px;
  letter-spacing: 0.16em;
  font-weight: 700;
  margin: 0 0 4px;
  color: var(--orange);
}

h2 {
  font-size: 30px;
  margin: 6px 0;
}

p {
  color: #d5e0d8;
  margin: 0;
  font-size: 13px;
}

.point-balance {
  display: flex;
  gap: 28px;
  margin-top: 20px;
  b { display: block; font-size: 23px; }
  small { color: #d5e0d8; font-size: 10px; }
  em {
    display: block;
    margin-top: 2px;
    font-style: normal;
    color: #ffd9a8;
    font-size: 9px;
  }
}

.member-progress {
  margin: 24px 0;
}

.member-progress-top {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
}

.progress-track {
  height: 8px;
  border-radius: 9px;
  background: #ebe5da;
  margin: 8px 0;
  overflow: hidden;
  span {
    height: 100%;
    display: block;
    border-radius: 9px;
    background: var(--orange);
    transition: width 0.3s;
  }
}

p { font-size: 11px; color: var(--muted); margin: 0; }

.coupon-title {
  font-size: 20px;
  margin: 20px 0 10px;
  font-weight: normal;
}

.member-coupons {
  display: grid;
  gap: 9px;
  margin-bottom: 16px;
}

.member-coupon {
  border: 1px solid #ead8bc;
  background: #fffaf0;
  border-radius: 11px;
  padding: 11px;
  display: flex;
  justify-content: space-between;
  gap: 10px;
  cursor: pointer;
  b { display: block; font-size: 13px; }
  small { display: block; font-size: 11px; color: var(--muted); margin-top: 2px; }
  span { color: var(--orange); font-size: 12px; font-weight: bold; align-self: center; }
  &:hover { background: #fff3dc; }
}

.no-coupon {
  text-align: center;
  color: var(--muted);
  font-size: 12px;
  padding: 12px 0;
}

.member-empty {
  text-align: center;
  padding: 48px 12px;
  color: var(--muted);
  p { font-size: 14px; font-weight: 600; color: var(--ink); margin: 0 0 6px; }
  small { font-size: 12px; }
}
</style>
