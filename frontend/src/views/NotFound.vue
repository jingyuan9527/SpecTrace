<script setup lang="ts">
import { Compass, House } from '@lucide/vue'
import PageHeader from '@/components/PageHeader.vue'
import { Button, Card, CardContent, Empty } from '@/components/ui'
</script>

<template>
  <div class="space-y-4">
    <PageHeader title="页面不存在" description="你访问的地址可能已被移动或删除" />

    <Card>
      <CardContent class="p-0">
        <Empty title="404" description="没有找到对应页面，可以回到首页继续浏览机型与对比。">
          <template #icon><Compass class="mb-1 h-8 w-8 text-muted-foreground/40" /></template>
        </Empty>
      </CardContent>
    </Card>

    <div class="flex justify-center">
      <Button variant="outline" @click="$router.push('/')">
        <House />
        返回首页
      </Button>
    </div>
  </div>
</template>
