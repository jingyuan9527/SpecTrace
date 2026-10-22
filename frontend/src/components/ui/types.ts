/** 选择类组件的统一选项结构。 */
export interface SelectOption {
  label: string
  value: string | number
  disabled?: boolean
}

/** 分段选项卡结构。 */
export interface TabItem {
  value: string
  label: string
}

/** 下拉菜单项。 */
export interface MenuItem {
  label: string
  icon?: import('vue').Component
  destructive?: boolean
  separatorBefore?: boolean
  onSelect: () => void | Promise<void>
}
