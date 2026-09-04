<script setup lang="ts">
import { ref } from 'vue'

import robotImg from '@/assets/images/codex-robot-1.png'

const showTooltip = ref(false)

function toggleTooltip() {
  showTooltip.value = !showTooltip.value
}
</script>


<template>

  <div
      class="floating-robot"
      @click="toggleTooltip"
  >


    <!-- Codex 官方机器人图片 -->
    <img
        class="robot-image"
        :src="robotImg"
        alt="Codex Robot"
    />


    <!-- 提示框 -->
    <Transition name="tooltip">

      <div
          v-if="showTooltip"
          class="robot-tooltip"
      >

        <p class="tooltip-title">
          ☕ FIKA 点单助手
        </p>

        <p class="tooltip-desc">
          随时为你服务
        </p>

      </div>

    </Transition>


  </div>


</template>


<style scoped lang="scss">


.floating-robot {

  position:fixed;

  right:30px;

  bottom:30px;

  z-index:9999;

  cursor:pointer;

}


/* 真实机器人图片 */

.robot-image {

  width:90px;

  height:90px;

  object-fit:contain;

  display:block;


  transition:
      transform .25s ease,
      filter .25s ease;


  animation:
      float 3s ease-in-out infinite;

}



.floating-robot:hover
.robot-image {

  transform:
      scale(1.08);


  filter:
      drop-shadow(
          0 12px 25px
          rgba(37,99,235,.35)
      );

}



@keyframes float {

  0%,100%{

    transform:
        translateY(0);

  }


  50%{

    transform:
        translateY(-8px);

  }

}



/* Tooltip */


.robot-tooltip {


  position:absolute;

  right:0;

  bottom:100px;


  background:white;

  border-radius:16px;

  padding:14px 18px;


  width:160px;


  box-shadow:
      0 10px 30px rgba(0,0,0,.12);


}


.robot-tooltip::after {


  content:"";

  position:absolute;

  right:25px;

  bottom:-8px;


  border-left:
      8px solid transparent;

  border-right:
      8px solid transparent;

  border-top:
      8px solid white;


}



.tooltip-title{

  font-size:14px;

  font-weight:700;

  color:#19352d;

  margin:0 0 4px;


}


.tooltip-desc{

  font-size:12px;

  color:#999;

  margin:0;

}




.tooltip-enter-active,
.tooltip-leave-active{

  transition:.2s;

}


.tooltip-enter-from,
.tooltip-leave-to{

  opacity:0;

  transform:
      translateY(10px);

}


</style>