<script setup lang="ts">
/**
 * 列表页筛选/批量操作条。
 * 吸顶是为了长列表滚动时筛选条件与批量动作始终可点，不必先滚回页面顶部；
 * 手机竖屏控件换行后条子过高，吸顶会吃掉半个屏幕，故只从 sm 开始吸顶。
 * 右侧插槽放计数等辅助信息，保证与左侧操作同一视觉基线。
 */
</script>

<template>
  <div
    class="z-20 flex flex-wrap items-center gap-2 rounded-lg border bg-background/90 px-3 py-2 shadow-sm backdrop-blur sm:sticky sm:top-0 supports-[backdrop-filter]:bg-background/75"
  >
    <slot />
    <div v-if="$slots.right" class="ml-auto flex flex-wrap items-center gap-2">
      <slot name="right" />
    </div>
  </div>
</template>
