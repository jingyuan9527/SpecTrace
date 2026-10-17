<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '@/api'

const groups = ref<any[]>([])
const defs = ref<any[]>([])
const activeGroup = ref<number | undefined>()

const groupDialog = ref(false)
const groupForm = reactive<any>({ id: null, name: '', sortOrder: 0 })

const defDialog = ref(false)
const defForm = reactive<any>({
  id: null, paramKey: '', paramName: '', groupId: undefined,
  paramType: 'text', enumOptions: '', unit: '', sortOrder: 0, enabled: 1,
})

const types = ['text', 'number', 'image', 'date', 'select']

async function loadGroups() {
  groups.value = await adminApi.paramGroups()
  if (!activeGroup.value && groups.value.length) activeGroup.value = groups.value[0].id
}

async function loadDefs() {
  defs.value = await adminApi.paramDefs(activeGroup.value)
}

onMounted(async () => {
  await loadGroups()
  await loadDefs()
})
watch(activeGroup, loadDefs)

function openGroup(row?: any) {
  Object.assign(groupForm, row ? { ...row } : { id: null, name: '', sortOrder: 0 })
  groupDialog.value = true
}
async function saveGroup() {
  await adminApi.saveParamGroup({ ...groupForm })
  ElMessage.success('保存成功')
  groupDialog.value = false
  await loadGroups()
  loadDefs()
}
async function removeGroup(row: any) {
  await ElMessageBox.confirm(`确认删除分组「${row.name}」？`, '提示', { type: 'warning' })
  await adminApi.deleteParamGroup(row.id)
  loadGroups()
  loadDefs()
}

function openDef(row?: any) {
  Object.assign(defForm, row ? { ...row } : {
    id: null, paramKey: '', paramName: '', groupId: activeGroup.value,
    paramType: 'text', enumOptions: '', unit: '', sortOrder: 0, enabled: 1,
  })
  defDialog.value = true
}
async function saveDef() {
  defForm.groupId = defForm.groupId || activeGroup.value
  await adminApi.saveParamDef({ ...defForm })
  ElMessage.success('保存成功')
  defDialog.value = false
  loadDefs()
}
async function removeDef(row: any) {
  await ElMessageBox.confirm(`确认删除参数「${row.paramName}」？`, '提示', { type: 'warning' })
  await adminApi.deleteParamDef(row.id)
  loadDefs()
}
</script>

<template>
  <div class="flex gap-4">
    <!-- 分组 -->
    <div class="w-64 shrink-0 rounded-xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 p-3">
      <div class="flex items-center mb-3">
        <span class="font-semibold">参数分组</span>
        <el-button size="small" type="primary" text class="ml-auto" @click="openGroup()">+ 分组</el-button>
      </div>
      <ul>
        <li
          v-for="g in groups"
          :key="g.id"
          class="flex items-center px-3 py-2 rounded-lg cursor-pointer text-sm"
          :class="activeGroup === g.id ? 'bg-brand/10 text-brand font-medium' : 'hover:bg-slate-100 dark:hover:bg-slate-700'"
          @click="activeGroup = g.id"
        >
          <span>{{ g.name }}</span>
          <span class="ml-auto flex gap-1">
            <el-button size="small" text @click.stop="openGroup(g)">改</el-button>
            <el-button size="small" text type="danger" @click.stop="removeGroup(g)">删</el-button>
          </span>
        </li>
      </ul>
    </div>

    <!-- 参数定义 -->
    <div class="flex-1 rounded-xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 p-4">
      <div class="flex items-center mb-3">
        <span class="font-semibold">参数定义</span>
        <el-button type="primary" class="ml-auto" @click="openDef()">新增参数</el-button>
      </div>
      <el-table :data="defs" border stripe>
        <el-table-column prop="paramKey" label="参数 Key" min-width="150" />
        <el-table-column prop="paramName" label="参数名称" min-width="120" />
        <el-table-column prop="paramType" label="类型" width="90" />
        <el-table-column prop="unit" label="单位" width="80" />
        <el-table-column label="启用" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'">{{ row.enabled === 1 ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button size="small" @click="openDef(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="removeDef(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="groupDialog" :title="groupForm.id ? '编辑分组' : '新增分组'" width="420">
      <el-form :model="groupForm" label-width="80px">
        <el-form-item label="分组名称"><el-input v-model="groupForm.name" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="groupForm.sortOrder" :min="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="groupDialog = false">取消</el-button>
        <el-button type="primary" @click="saveGroup">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="defDialog" :title="defForm.id ? '编辑参数' : '新增参数'" width="520">
      <el-form :model="defForm" label-width="100px">
        <el-form-item label="参数 Key"><el-input v-model="defForm.paramKey" placeholder="如 screen_size（英文小写下划线）" /></el-form-item>
        <el-form-item label="参数名称"><el-input v-model="defForm.paramName" placeholder="如 屏幕尺寸" /></el-form-item>
        <el-form-item label="所属分组">
          <el-select v-model="defForm.groupId" class="w-full">
            <el-option v-for="g in groups" :key="g.id" :label="g.name" :value="g.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="参数类型">
          <el-select v-model="defForm.paramType" class="w-full">
            <el-option v-for="t in types" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="defForm.paramType === 'select'" label="枚举选项">
          <el-input v-model="defForm.enumOptions" placeholder='JSON 数组，如 ["支持","不支持"]' />
        </el-form-item>
        <el-form-item label="单位"><el-input v-model="defForm.unit" placeholder="如 英寸 / mAh" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="defForm.sortOrder" :min="0" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="defForm.enabled" :active-value="1" :inactive-value="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="defDialog = false">取消</el-button>
        <el-button type="primary" @click="saveDef">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
