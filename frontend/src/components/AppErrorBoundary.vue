<script setup lang="ts">
import { onErrorCaptured, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { House, RotateCcw, TriangleAlert } from '@lucide/vue'
import { Button, Card, CardContent, Empty } from '@/components/ui'

/**
 * 应用级错误边界：任一子组件渲染失败时只降级当前页面，而不是整站白屏。
 *
 * <p>存在理由：本项目没有组件自动导入，模板里漏写组件导入会直接抛错（T64 事故），
 * 而 mock e2e 与 typecheck 都拦不住渲染期失败——必须有运行期兜底，否则线上表现就是一片空白且无迹可查。</p>
 */
const route = useRoute()
const captured = ref<Error | null>(null)
const boundaryKey = ref(0)

onErrorCaptured((error, instance, info) => {
  captured.value = error instanceof Error ? error : new Error(String(error))
  console.error('[AppErrorBoundary] 页面渲染失败', {
    info,
    component: instance?.$options?.name ?? null,
    route: route.fullPath,
  }, error)
  // 返回 false 阻止继续冒泡：错误已由边界接管，不再触发全局 errorHandler 的重复提示
  return false
})

// 路由切换即复位：一次页面内的错误不应把后续导航也锁在错误态
watch(() => route.fullPath, () => {
  captured.value = null
})

/** 重试靠重建子树实现：出错组件的内部状态已不可信，重新挂载比打补丁可靠 */
function retry() {
  captured.value = null
  boundaryKey.value += 1
}

function backHome() {
  window.location.assign('/')
}
</script>

<template>
  <div v-if="captured" class="mx-auto flex min-h-[60vh] max-w-2xl items-center px-4">
    <Card class="w-full">
      <CardContent class="p-0">
        <Empty
          title="页面加载出错"
          description="这一页在渲染时出现异常，你可以重试；若反复失败请刷新页面或稍后再来。"
        >
          <template #icon>
            <TriangleAlert class="mb-1 h-8 w-8 text-muted-foreground/40" />
          </template>
          <div class="mt-2 flex flex-wrap items-center justify-center gap-2">
            <Button @click="retry">
              <RotateCcw />
              重试
            </Button>
            <Button variant="outline" @click="backHome">
              <House />
              返回首页
            </Button>
          </div>
        </Empty>
      </CardContent>
    </Card>
  </div>

  <div v-else :key="boundaryKey">
    <slot />
  </div>
</template>
