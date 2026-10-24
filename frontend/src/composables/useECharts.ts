import * as echarts from 'echarts/core'
import type { EChartsType } from 'echarts/core'
import { BarChart, LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, MarkPointComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { onBeforeUnmount } from 'vue'

// 按需注册两个页面用到的全部图表能力（多注册的组件不参与渲染，体积影响可忽略），
// 页面因此不再各自维护 echarts.use 列表
echarts.use([BarChart, LineChart, GridComponent, LegendComponent, MarkPointComponent, TooltipComponent, CanvasRenderer])

/**
 * ECharts 实例的统一生命周期：按 key 维护「元素 → 实例」注册表，
 * 覆盖初始化、容器替换重挂、窗口 resize、批量销毁与组件卸载清理。
 *
 * <p>History 单图与 Evolution 多图共用；主题切换由页面自行 watch 后重算 option 再调
 * {@link render}，颜色逻辑留在页面。</p>
 */
export function useECharts() {
  const els = new Map<string | number, HTMLElement>()
  const charts = new Map<string | number, EChartsType>()

  /** 模板 ref 回调：绑定（el 非空）或解绑（el 为 null）图表容器。 */
  function bind(key: string | number) {
    return (el: Element | unknown | null) => {
      if (el instanceof HTMLElement) {
        els.set(key, el)
      } else {
        els.delete(key)
      }
    }
  }

  /**
   * 确保 key 对应的实例存在且挂在当前容器上，随后 setOption(option, true) 全量替换并 resize。
   * 容器销毁重建（v-if 卸载再挂载）后元素会变，此时旧实例必须废弃，否则会画在脱离文档的 canvas 上。
   */
  function render(key: string | number, option: echarts.EChartsCoreOption) {
    const el = els.get(key)
    if (!el) return
    let chart = charts.get(key)
    if (chart && chart.getDom() !== el) {
      chart.dispose()
      charts.delete(key)
      chart = undefined
    }
    if (!chart) {
      chart = echarts.init(el)
      charts.set(key, chart)
    }
    chart.setOption(option, true)
    chart.resize()
    // 容器可能在入场动画结束后才完成布局，补一次 resize 触发重绘
    window.setTimeout(() => charts.get(key)?.resize(), 120)
  }

  /** 丢弃不再需要的图表（如参数被取消勾选）。 */
  function drop(key: string | number) {
    charts.get(key)?.dispose()
    charts.delete(key)
  }

  /** 只保留 keep 中的 key，其余图表销毁（多图页面在勾选集合变化后调用）。 */
  function prune(keep: Set<string | number>) {
    for (const key of [...charts.keys()]) {
      if (!keep.has(key)) drop(key)
    }
  }

  /** 销毁全部实例：加载态会整体卸载容器，重挂后必须重来。 */
  function disposeAll() {
    charts.forEach((chart) => chart.dispose())
    charts.clear()
  }

  function resizeAll() {
    charts.forEach((chart) => chart.resize())
  }

  window.addEventListener('resize', resizeAll)
  onBeforeUnmount(() => {
    window.removeEventListener('resize', resizeAll)
    disposeAll()
  })

  return { bind, render, drop, prune, disposeAll, resizeAll }
}
