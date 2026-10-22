<script setup lang="ts">
import { ref, watch } from 'vue'

const props = defineProps<{
  src?: string | null
  alt?: string
  imgClass?: string
  fallbackClass?: string
}>()

const failed = ref(false)
watch(
  () => props.src,
  () => {
    failed.value = false
  },
)
</script>

<template>
  <img
    v-if="props.src && !failed"
    :src="props.src"
    :alt="props.alt ?? ''"
    :class="props.imgClass"
    loading="lazy"
    @error="failed = true"
  />
  <slot v-else name="fallback">
    <span :class="props.fallbackClass" />
  </slot>
</template>
