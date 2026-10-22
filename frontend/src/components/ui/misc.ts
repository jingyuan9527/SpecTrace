import { defineComponent, h } from 'vue'
import { cn } from '@/lib/utils'

/** 骨架屏：加载态占位，保持布局稳定。 */
export const Skeleton = defineComponent({
  name: 'Skeleton',
  props: { class: { type: String, default: '' } },
  setup(props) {
    return () => h('div', { class: cn('animate-pulse rounded-md bg-muted', props.class) })
  },
})

/** 空状态：统一列表/表格无数据时的呈现。 */
export const Empty = defineComponent({
  name: 'Empty',
  props: { title: { type: String, default: '暂无数据' }, description: { type: String, default: '' } },
  setup(props, { slots }) {
    return () =>
      h('div', { class: 'flex flex-col items-center justify-center gap-2 px-6 py-12 text-center' }, [
        slots.icon?.(),
        h('p', { class: 'text-sm font-medium' }, props.title),
        props.description ? h('p', { class: 'max-w-md text-sm text-muted-foreground' }, props.description) : null,
        slots.default?.(),
      ])
  },
})
