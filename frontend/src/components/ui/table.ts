import { defineComponent, h } from 'vue'
import { cn } from '@/lib/utils'

/** shadcn-vue Table 原语：语义化表格 + 统一边框/密度。 */
export const Table = defineComponent({
  name: 'Table',
  props: { class: { type: String, default: '' } },
  setup(props, { slots }) {
    return () =>
      h('div', { class: 'relative w-full overflow-auto' }, [
        h('table', { class: cn('w-full caption-bottom text-sm', props.class) }, slots.default?.()),
      ])
  },
})

export const TableHeader = defineComponent({
  name: 'TableHeader',
  props: { class: { type: String, default: '' } },
  setup(props, { slots }) {
    return () => h('thead', { class: cn('[&_tr]:border-b', props.class) }, slots.default?.())
  },
})

export const TableBody = defineComponent({
  name: 'TableBody',
  props: { class: { type: String, default: '' } },
  setup(props, { slots }) {
    return () => h('tbody', { class: cn('[&_tr:last-child]:border-0', props.class) }, slots.default?.())
  },
})

export const TableFooter = defineComponent({
  name: 'TableFooter',
  props: { class: { type: String, default: '' } },
  setup(props, { slots }) {
    return () =>
      h('tfoot', { class: cn('border-t bg-muted/50 font-medium [&>tr]:last:border-b-0', props.class) }, slots.default?.())
  },
})

export const TableRow = defineComponent({
  name: 'TableRow',
  props: { class: { type: String, default: '' } },
  setup(props, { slots }) {
    return () =>
      h(
        'tr',
        {
          class: cn('border-b transition-colors hover:bg-muted/50 data-[state=selected]:bg-muted', props.class),
        },
        slots.default?.(),
      )
  },
})

export const TableHead = defineComponent({
  name: 'TableHead',
  props: { class: { type: String, default: '' } },
  setup(props, { slots }) {
    return () =>
      h(
        'th',
        {
          class: cn(
            'h-10 px-3 text-left align-middle text-xs font-medium uppercase tracking-wide text-muted-foreground [&:has([role=checkbox])]:pr-0',
            props.class,
          ),
        },
        slots.default?.(),
      )
  },
})

export const TableCell = defineComponent({
  name: 'TableCell',
  props: { class: { type: String, default: '' } },
  setup(props, { slots }) {
    return () => h('td', { class: cn('px-3 py-3 align-middle [&:has([role=checkbox])]:pr-0', props.class) }, slots.default?.())
  },
})

export const TableCaption = defineComponent({
  name: 'TableCaption',
  props: { class: { type: String, default: '' } },
  setup(props, { slots }) {
    return () => h('caption', { class: cn('mt-4 text-sm text-muted-foreground', props.class) }, slots.default?.())
  },
})
