<script setup lang="ts">
/**
 * 日期选择器（yyyy-MM-dd 年-月-日顺序）。
 *
 * <p>没有直接用 reka-ui 的 DatePicker/Calendar：那套 API 的日期值是
 * `@internationalized/date` 的 DateValue，而该包只是 reka-ui 的传递依赖，
 * 直接引入等于隐式新增依赖。这里复用 reka 的 Popover 定位与关闭行为，
 * 日历面板按国内习惯自行渲染（周一为首日、可切年/月、今天/清空）。</p>
 */
import { computed, ref, watch } from 'vue'
import { CalendarDays, ChevronLeft, ChevronRight, X } from '@lucide/vue'
import { PopoverContent, PopoverPortal, PopoverRoot, PopoverTrigger } from 'reka-ui'
import { toast } from 'vue-sonner'
import { normalizeDateText, parseIsoDate, toIsoDate } from '@/lib/date'
import { cn } from '@/lib/utils'
import Input from './Input.vue'

const props = withDefaults(
  defineProps<{
    modelValue?: string | null
    id?: string
    placeholder?: string
    disabled?: boolean
    class?: string
  }>(),
  { placeholder: '选择日期', disabled: false },
)
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

// 输入框文本与外部值分离：允许敲到一半的中间态，失焦/回车才归一化提交
const text = ref(props.modelValue ?? '')
watch(
  () => props.modelValue,
  (value) => {
    if ((value ?? '') !== text.value) text.value = value ?? ''
  },
)

/** 手输内容归一化：合法则回写 `yyyy-MM-dd`，非法提示并回退到上次有效值。 */
function commitText() {
  const normalized = normalizeDateText(text.value)
  if (normalized === null) {
    toast.warning('日期格式应为 2025-09-10')
    text.value = props.modelValue ?? ''
    return
  }
  text.value = normalized
  if (normalized !== (props.modelValue ?? '')) emit('update:modelValue', normalized)
}

const todayIso = toIsoDate(new Date())
const weekLabels = ['一', '二', '三', '四', '五', '六', '日']
const open = ref(false)
const viewYear = ref(new Date().getFullYear())
const viewMonth = ref(new Date().getMonth() + 1)
const selectedIso = computed(() => normalizeDateText(props.modelValue) || '')

// 上限放到明年，方便预录即将发布的机型；下限覆盖早期机型
const yearOptions = computed(() => {
  const years: number[] = []
  for (let year = new Date().getFullYear() + 1; year >= 1980; year--) years.push(year)
  return years
})
const monthOptions = Array.from({ length: 12 }, (_, index) => index + 1)

// 打开面板时定位到已选日期（没有则今天），避免每次都从当月翻起
watch(open, (value) => {
  if (!value) return
  const base = parseIsoDate(props.modelValue) ?? new Date()
  viewYear.value = base.getFullYear()
  viewMonth.value = base.getMonth() + 1
})

/** 周一为每周首日（国内日历习惯）；固定 6 行，切换月份时面板高度不跳动。 */
const days = computed(() => {
  const first = new Date(viewYear.value, viewMonth.value - 1, 1)
  const mondayOffset = (first.getDay() + 6) % 7
  return Array.from({ length: 42 }, (_, index) => {
    const date = new Date(viewYear.value, viewMonth.value - 1, 1 - mondayOffset + index)
    return { iso: toIsoDate(date), day: date.getDate(), inMonth: date.getMonth() === viewMonth.value - 1 }
  })
})

function shiftMonth(delta: number) {
  const date = new Date(viewYear.value, viewMonth.value - 1 + delta, 1)
  viewYear.value = date.getFullYear()
  viewMonth.value = date.getMonth() + 1
}

function pick(iso: string) {
  text.value = iso
  emit('update:modelValue', iso)
  open.value = false
}

function clear() {
  text.value = ''
  emit('update:modelValue', '')
  open.value = false
}
</script>

<template>
  <div :class="cn('relative', props.class)">
    <Input
      :id="props.id"
      v-model="text"
      :placeholder="props.placeholder"
      :disabled="props.disabled"
      autocomplete="off"
      inputmode="numeric"
      class="pr-16"
      @blur="commitText"
      @keyup.enter="commitText"
    />
    <div class="absolute inset-y-0 right-1 flex items-center gap-0.5">
      <!-- mousedown.prevent：避免点清空时先触发输入框 blur，导致非法文本先弹一次提示 -->
      <button
        v-if="text"
        type="button"
        class="rounded-sm p-1 text-muted-foreground transition-colors hover:text-foreground disabled:opacity-50"
        aria-label="清空日期"
        :disabled="props.disabled"
        @mousedown.prevent
        @click="clear"
      >
        <X class="h-3.5 w-3.5" />
      </button>
      <PopoverRoot v-model:open="open">
        <PopoverTrigger as-child>
          <button
            type="button"
            class="rounded-sm p-1 text-muted-foreground transition-colors hover:text-foreground disabled:opacity-50"
            aria-label="打开日期选择"
            :disabled="props.disabled"
          >
            <CalendarDays class="h-4 w-4" />
          </button>
        </PopoverTrigger>
        <PopoverPortal>
          <PopoverContent
            align="end"
            :side-offset="4"
            class="z-50 w-[300px] rounded-md border bg-popover p-3 text-popover-foreground shadow-md outline-none data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95"
          >
            <div class="mb-2 flex items-center gap-1">
              <button
                type="button"
                class="inline-flex h-7 w-7 shrink-0 items-center justify-center rounded-md text-muted-foreground hover:bg-accent hover:text-foreground"
                aria-label="上个月"
                @click="shiftMonth(-1)"
              >
                <ChevronLeft class="h-4 w-4" />
              </button>
              <select
                v-model.number="viewYear"
                aria-label="年份"
                class="h-8 min-w-0 flex-1 rounded-md border border-input bg-transparent px-1 text-sm outline-none focus:ring-1 focus:ring-ring"
              >
                <option v-for="year in yearOptions" :key="year" :value="year">{{ year }} 年</option>
              </select>
              <select
                v-model.number="viewMonth"
                aria-label="月份"
                class="h-8 w-[76px] shrink-0 rounded-md border border-input bg-transparent px-1 text-sm outline-none focus:ring-1 focus:ring-ring"
              >
                <option v-for="month in monthOptions" :key="month" :value="month">{{ month }} 月</option>
              </select>
              <button
                type="button"
                class="inline-flex h-7 w-7 shrink-0 items-center justify-center rounded-md text-muted-foreground hover:bg-accent hover:text-foreground"
                aria-label="下个月"
                @click="shiftMonth(1)"
              >
                <ChevronRight class="h-4 w-4" />
              </button>
            </div>

            <div class="grid grid-cols-7 gap-1 text-center text-[11px] text-muted-foreground">
              <span v-for="label in weekLabels" :key="label">{{ label }}</span>
            </div>
            <div class="mt-1 grid grid-cols-7 gap-1">
              <button
                v-for="day in days"
                :key="day.iso"
                type="button"
                class="h-8 rounded-md text-sm tabular-nums transition-colors hover:bg-accent hover:text-accent-foreground"
                :class="
                  cn(
                    !day.inMonth && 'text-muted-foreground/50',
                    day.iso === selectedIso &&
                      'bg-primary text-primary-foreground hover:bg-primary hover:text-primary-foreground',
                    day.iso === todayIso && day.iso !== selectedIso && 'ring-1 ring-inset ring-primary/60',
                  )
                "
                @click="pick(day.iso)"
              >
                {{ day.day }}
              </button>
            </div>

            <div class="mt-2 flex items-center justify-between border-t pt-2 text-xs">
              <button
                type="button"
                class="rounded-sm px-1 py-0.5 text-muted-foreground hover:text-foreground"
                @click="pick(todayIso)"
              >
                今天
              </button>
              <button
                type="button"
                class="rounded-sm px-1 py-0.5 text-muted-foreground hover:text-foreground"
                @click="clear"
              >
                清空
              </button>
            </div>
          </PopoverContent>
        </PopoverPortal>
      </PopoverRoot>
    </div>
  </div>
</template>
