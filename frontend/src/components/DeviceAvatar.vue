<script setup lang="ts">
import { computed } from 'vue'
import ImageWithFallback from '@/components/ImageWithFallback.vue'
import { brandGradient } from '@/lib/brandTheme'

/**
 * 机型视觉的统一出口：有图显示图，无图渲染品牌色渐变文字卡。
 *
 * <p>产品决策是「无图为默认设计」——历史机型官图难觅，统一文字卡比参差的图库更有质感。
 * 组件占满父容器（h-full w-full），尺寸由调用方的容器控制。</p>
 */
const props = withDefaults(
  defineProps<{
    name: string
    thumbnail?: string | null
    /** 品牌名（中英文均可），决定无图时的渐变色 */
    brand?: string | null
    /** 影响无图文字卡的字号档位 */
    size?: 'sm' | 'md' | 'lg'
    /** 图片存在时的展示类（object-contain 等） */
    imgClass?: string
  }>(),
  { size: 'md' },
)

const gradient = computed(() => brandGradient(props.brand))

const nameClass = computed(() => {
  const length = props.name?.length ?? 0
  if (props.size === 'lg') {
    return length > 10 ? 'text-2xl' : length > 6 ? 'text-3xl' : 'text-4xl'
  }
  if (props.size === 'md') {
    return length > 12 ? 'text-sm' : length > 7 ? 'text-base' : 'text-lg'
  }
  return length > 12 ? 'text-[10px]' : length > 8 ? 'text-xs' : 'text-sm'
})
</script>

<template>
  <!-- 有图：中性底 + 产品图；图挂了整块退化为品牌渐变文字卡 -->
  <div v-if="props.thumbnail" class="flex h-full w-full items-center justify-center overflow-hidden bg-muted/50">
    <ImageWithFallback :src="props.thumbnail" :alt="props.name" :img-class="props.imgClass">
      <template #fallback>
        <div
          class="flex h-full w-full items-center justify-center overflow-hidden bg-gradient-to-br"
          :class="[gradient.from, gradient.to]"
        >
          <span
            class="px-2 text-center font-bold leading-tight text-white drop-shadow-sm"
            :class="nameClass"
          >
            {{ props.name }}
          </span>
        </div>
      </template>
    </ImageWithFallback>
  </div>
  <!-- 无图：品牌色渐变文字卡 -->
  <div
    v-else
    class="flex h-full w-full items-center justify-center overflow-hidden bg-gradient-to-br"
    :class="[gradient.from, gradient.to]"
  >
    <span
      class="px-2 text-center font-bold leading-tight text-white drop-shadow-sm"
      :class="nameClass"
    >
      {{ props.name }}
    </span>
  </div>
</template>
