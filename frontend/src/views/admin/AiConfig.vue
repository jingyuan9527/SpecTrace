<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '@/api'

const connections = ref<any[]>([])
const loading = ref(false)
const modelsMap = reactive<Record<number, any[]>>({})
const tableRef = ref()

// 连接编辑弹窗
const dialog = ref(false)
const form = reactive<any>({ id: null, name: '', provider: 'dashscope', baseUrl: '', apiKey: '', enabled: 1 })

// 拉取远端模型弹窗
const pullDialog = ref(false)
const pullConfig = ref<any>(null)
const remoteModels = ref<string[]>([])
const selectedModels = ref<string[]>([])
const pullLoading = ref(false)
const importLoading = ref(false)

const presets: Record<string, { label: string; baseUrl: string }> = {
  dashscope: { label: '阿里云百炼 DashScope', baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1' },
  openai: { label: 'OpenAI', baseUrl: 'https://api.openai.com/v1' },
  deepseek: { label: 'DeepSeek', baseUrl: 'https://api.deepseek.com/v1' },
  moonshot: { label: 'Moonshot (Kimi)', baseUrl: 'https://api.moonshot.cn/v1' },
  ollama: { label: 'Ollama（本地）', baseUrl: 'http://localhost:11434/v1' },
  custom: { label: '自定义 OpenAI 兼容', baseUrl: '' },
}
const providers = Object.keys(presets)

function onProviderChange(p: string) {
  const preset = presets[p]
  if (preset && p !== 'custom') form.baseUrl = preset.baseUrl
}

async function load() {
  loading.value = true
  try {
    connections.value = await adminApi.aiConfigs()
  } finally {
    loading.value = false
  }
}
onMounted(load)

async function loadModels(configId: number) {
  modelsMap[configId] = await adminApi.aiConfigModels(configId)
}

function onExpand(row: any, expandedRows: any[]) {
  if (expandedRows.includes(row)) loadModels(row.id)
}

// ---------- 连接增删改 ----------
function openCreate() {
  Object.assign(form, { id: null, name: '', provider: 'dashscope', baseUrl: presets.dashscope.baseUrl, apiKey: '', enabled: 1 })
  dialog.value = true
}
function openEdit(row: any) {
  Object.assign(form, { ...row, apiKey: '' })
  dialog.value = true
}
async function save() {
  if (!form.name) return ElMessage.warning('请填写连接名称')
  if (!form.baseUrl) return ElMessage.warning('请填写接口地址')
  await adminApi.saveAiConfig({ ...form })
  ElMessage.success('保存成功')
  dialog.value = false
  load()
}
async function remove(row: any) {
  await ElMessageBox.confirm(`确认删除连接「${row.name}」及其下所有模型？`, '提示', { type: 'warning' })
  await adminApi.deleteAiConfig(row.id)
  ElMessage.success('已删除')
  delete modelsMap[row.id]
  load()
}

// ---------- 拉取并导入模型 ----------
async function openPull(row: any) {
  pullConfig.value = row
  selectedModels.value = []
  remoteModels.value = []
  pullDialog.value = true
  await fetchRemote()
}
async function fetchRemote() {
  pullLoading.value = true
  try {
    remoteModels.value = await adminApi.pullRemoteModels(pullConfig.value.id)
    if (!remoteModels.value.length) ElMessage.warning('远端未返回任何模型')
  } catch (e: any) {
    remoteModels.value = []
  } finally {
    pullLoading.value = false
  }
}
async function confirmImport() {
  if (!selectedModels.value.length) return ElMessage.warning('请选择要导入的模型')
  importLoading.value = true
  try {
    await adminApi.importAiModels(pullConfig.value.id, { models: selectedModels.value })
    ElMessage.success(`已导入 ${selectedModels.value.length} 个模型`)
    pullDialog.value = false
    await loadModels(pullConfig.value.id)
    tableRef.value?.toggleRowExpansion(pullConfig.value, true)
  } finally {
    importLoading.value = false
  }
}

// ---------- 模型操作 ----------
async function setDefaultModel(model: any) {
  await adminApi.setDefaultAiModel(model.id)
  ElMessage.success(`已设为默认模型：${model.model}`)
  await loadModels(model.configId)
}
async function toggleModelEnabled(model: any) {
  await adminApi.updateAiModel(model.id, { enabled: model.enabled === 1 ? 0 : 1 })
  await loadModels(model.configId)
}
async function removeModel(model: any) {
  await ElMessageBox.confirm(`确认删除模型「${model.model}」？`, '提示', { type: 'warning' })
  await adminApi.deleteAiModel(model.id)
  ElMessage.success('已删除')
  await loadModels(model.configId)
}
</script>

<template>
  <div class="space-y-4">
    <div class="flex items-center">
      <h2 class="text-lg font-semibold">AI 连接与模型</h2>
      <span class="ml-3 text-sm text-slate-400">连接供应商后拉取远端模型，可导入多个模型并设置默认；抓取任务时也可单独选择</span>
      <el-button type="primary" class="ml-auto" @click="openCreate">新增连接</el-button>
    </div>

    <el-table ref="tableRef" v-loading="loading" :data="connections" border stripe row-key="id"
      @expand-change="onExpand">
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="px-8 py-2">
            <div class="flex items-center mb-2">
              <span class="text-sm font-medium text-slate-600">模型列表</span>
              <el-button size="small" type="primary" plain class="ml-3" @click="openPull(row)">拉取远端模型</el-button>
            </div>
            <el-table :data="modelsMap[row.id] || []" size="small" border>
              <el-table-column prop="model" label="模型名称" min-width="200" />
              <el-table-column prop="temperature" label="温度" width="90" />
              <el-table-column label="默认" width="90">
                <template #default="{ row: m }">
                  <el-tag v-if="m.isDefault === 1" type="danger" size="small">默认</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="启用" width="90">
                <template #default="{ row: m }">
                  <el-switch :model-value="m.enabled === 1" @change="toggleModelEnabled(m)" />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="200">
                <template #default="{ row: m }">
                  <el-button size="small" :disabled="m.isDefault === 1" @click="setDefaultModel(m)">设为默认</el-button>
                  <el-button size="small" type="danger" @click="removeModel(m)">删除</el-button>
                </template>
              </el-table-column>
              <template #empty>
                <span class="text-slate-400 text-sm">暂无模型，点击上方「拉取远端模型」导入</span>
              </template>
            </el-table>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="连接名称" min-width="130" />
      <el-table-column prop="provider" label="服务商" width="120" />
      <el-table-column prop="baseUrl" label="接口地址" min-width="240" show-overflow-tooltip />
      <el-table-column label="API Key" width="100">
        <template #default="{ row }">
          <el-tag :type="row.apiKeySet ? 'success' : 'warning'" size="small">{{ row.apiKeySet ? '已配置' : '未配置' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="启用" width="80">
        <template #default="{ row }">
          <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">{{ row.enabled === 1 ? '是' : '否' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="230" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="primary" plain @click="openPull(row)">拉取模型</el-button>
          <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="form.id ? '编辑连接' : '新增连接'" width="560">
      <el-form :model="form" label-width="100px">
        <el-form-item label="连接名称">
          <el-input v-model="form.name" placeholder="如 阿里云百炼 / 本地 Ollama" />
        </el-form-item>
        <el-form-item label="服务商">
          <el-select v-model="form.provider" class="w-full" @change="onProviderChange">
            <el-option v-for="p in providers" :key="p" :label="presets[p].label" :value="p" />
          </el-select>
        </el-form-item>
        <el-form-item label="接口地址">
          <el-input v-model="form.baseUrl" placeholder="OpenAI 兼容 base_url，如 https://api.deepseek.com/v1" />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input v-model="form.apiKey" type="password" show-password
            :placeholder="form.id ? '留空表示不修改' : '请输入 API Key'" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-alert type="info" :closable="false" show-icon
          title="保存后展开该连接可拉取远端模型、导入多个模型并设置默认" />
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="pullDialog" title="拉取远端模型" width="560">
      <div class="mb-3 flex items-center">
        <span class="text-sm text-slate-500 mr-2">连接：{{ pullConfig?.name }}</span>
        <el-button size="small" :loading="pullLoading" @click="fetchRemote">重新拉取</el-button>
        <el-button size="small" link type="primary" class="ml-auto"
          @click="selectedModels = selectedModels.length === remoteModels.length ? [] : [...remoteModels]">
          {{ selectedModels.length === remoteModels.length ? '取消全选' : '全选' }}
        </el-button>
      </div>
      <div v-loading="pullLoading" class="max-h-80 overflow-auto border rounded p-3">
        <el-empty v-if="!remoteModels.length && !pullLoading" description="未拉取到模型，请检查接口地址与 API Key" :image-size="70" />
        <el-checkbox-group v-model="selectedModels" class="flex flex-col gap-2">
          <el-checkbox v-for="m in remoteModels" :key="m" :value="m" :label="m" />
        </el-checkbox-group>
      </div>
      <template #footer>
        <span class="text-sm text-slate-400 mr-auto">已选 {{ selectedModels.length }} 个</span>
        <el-button @click="pullDialog = false">取消</el-button>
        <el-button type="primary" :loading="importLoading" @click="confirmImport">导入所选</el-button>
      </template>
    </el-dialog>
  </div>
</template>
