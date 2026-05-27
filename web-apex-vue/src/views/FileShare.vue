<template>
  <div class="file-share-container">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2 class="page-title">
        <el-icon :size="22"><FolderOpened /></el-icon>
        <span>文件共享</span>
      </h2>
      <p class="page-subtitle">上传、管理和分享文件，所有人均可查看和下载</p>
    </div>

    <!-- 上传区域 -->
    <el-card class="upload-card" shadow="hover">
      <el-upload
        ref="uploadRef"
        class="upload-area"
        drag
        multiple
        :auto-upload="false"
        :on-change="handleFileChange"
        :show-file-list="false"
        action="#"
        accept="*"
      >
        <el-icon :size="48" class="upload-icon"><UploadFilled /></el-icon>
        <div class="upload-text">
          <p class="upload-title">点击或拖拽文件到此区域上传</p>
          <p class="upload-hint">支持所有文件类型，单次可上传多个文件</p>
        </div>
      </el-upload>

      <!-- 待上传文件队列 -->
      <div v-if="pendingFiles.length > 0" class="pending-queue">
        <div class="queue-header">
          <span class="queue-title">待上传文件（{{ pendingFiles.length }}）</span>
          <el-button type="primary" :loading="uploading" @click="startUpload">
            <el-icon><Upload /></el-icon>
            全部上传
          </el-button>
        </div>
        <div v-for="(file, index) in pendingFiles" :key="index" class="queue-item">
          <div class="queue-item-info">
            <el-icon :size="18" color="#409eff"><Document /></el-icon>
            <span class="queue-item-name">{{ file.name }}</span>
            <span class="queue-item-size">{{ formatFileSize(file.size) }}</span>
          </div>
          <div class="queue-item-actions">
            <el-tag v-if="file.status === 'success'" type="success" size="small">已上传</el-tag>
            <el-tag v-else-if="file.status === 'uploading'" type="warning" size="small">上传中</el-tag>
            <el-tag v-else-if="file.status === 'error'" type="danger" size="small">失败</el-tag>
            <el-button
              v-else
              text
              type="danger"
              size="small"
              @click="removePendingFile(index)"
            >
              移除
            </el-button>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 文件列表 -->
    <el-card class="list-card" shadow="hover">
      <template #header>
        <div class="list-header">
          <span class="list-title">
            <el-icon :size="18"><Files /></el-icon>
            文件列表
          </span>
          <el-input
            v-model="searchKeyword"
            placeholder="搜索文件名称"
            clearable
            :prefix-icon="Search"
            style="width: 260px"
            size="default"
            @input="onSearch"
          />
        </div>
      </template>

      <el-table
        v-loading="loading"
        :data="fileList"
        stripe
        style="width: 100%"
        :default-sort="{ prop: 'createTime', order: 'descending' }"
        @sort-change="onSortChange"
      >
        <el-table-column prop="fileName" label="文件名称" min-width="280" sortable="custom">
          <template #default="{ row }">
            <div class="file-name-cell">
              <el-icon :size="18" :color="getFileIconColor(row.fileName)">
                <component :is="getFileIcon(row.fileName)" />
              </el-icon>
              <span :title="row.fileName" class="file-name-text">{{ row.fileName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="fileSize" label="文件大小" width="120" sortable="custom" align="center">
          <template #default="{ row }">
            <span class="file-size-text">{{ formatFileSize(row.fileSize) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="uploadEmpNo" label="创建人" width="110" align="center" />
        <el-table-column prop="uploadIp" label="上传 IP" width="150" align="center" />
        <el-table-column prop="createTime" label="上传时间" width="180" sortable="custom" align="center">
          <template #default="{ row }">
            <span class="time-text">{{ formatTime(row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="downloadFile(row)">
              <el-icon><Download /></el-icon>
              下载
            </el-button>
            <el-button link type="danger" size="small" @click="confirmDelete(row)">
              <el-icon><Delete /></el-icon>
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div v-if="total > 0" class="pagination-wrapper">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="fetchFileList"
          @current-change="fetchFileList"
        />
      </div>

      <el-empty v-if="!loading && fileList.length === 0" description="暂无共享文件，请上传" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import type { UploadFile, UploadInstance } from 'element-plus'
import { getAllFiles, getFileList, uploadFile, deleteFile, getDownloadUrl } from '@/api/fileShare'
import type { SharedFile, PageResult } from '@/api/fileShare'

// ==================== 状态 ====================
const loading = ref(false)
const uploading = ref(false)
const fileList = ref<SharedFile[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const searchKeyword = ref('')
const uploadRef = ref<UploadInstance>()

interface PendingFile {
  name: string
  size: number
  raw: File
  status: 'pending' | 'uploading' | 'success' | 'error'
}
const pendingFiles = ref<PendingFile[]>([])

// ==================== 文件大小格式化 ====================
function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const k = 1024
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + units[i]
}

// ==================== 时间格式化 ====================
function formatTime(time: string): string {
  if (!time) return '-'
  const d = new Date(time)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

// ==================== 文件图标 ====================
function getFileIcon(fileName: string): string {
  const ext = fileName.split('.').pop()?.toLowerCase() || ''
  if (['jpg', 'jpeg', 'png', 'gif', 'bmp', 'svg', 'webp'].includes(ext)) return 'Picture'
  if (['mp4', 'avi', 'mov', 'wmv', 'flv', 'mkv'].includes(ext)) return 'VideoCamera'
  if (['mp3', 'wav', 'aac', 'flac', 'ogg'].includes(ext)) return 'Headset'
  if (['pdf'].includes(ext)) return 'DocumentChecked'
  if (['doc', 'docx'].includes(ext)) return 'Document'
  if (['xls', 'xlsx', 'csv'].includes(ext)) return 'DataAnalysis'
  if (['ppt', 'pptx'].includes(ext)) return 'PieChart'
  if (['zip', 'rar', '7z', 'tar', 'gz'].includes(ext)) return 'FolderOpened'
  if (['txt', 'md', 'json', 'xml', 'yml', 'yaml', 'log'].includes(ext)) return 'Tickets'
  if (['js', 'ts', 'jsx', 'tsx', 'vue', 'java', 'py', 'go', 'rs'].includes(ext)) return 'Files'
  return 'Document'
}

function getFileIconColor(fileName: string): string {
  const ext = fileName.split('.').pop()?.toLowerCase() || ''
  if (['jpg', 'jpeg', 'png', 'gif', 'bmp', 'svg', 'webp'].includes(ext)) return '#e6a23c'
  if (['mp4', 'avi', 'mov', 'wmv', 'flv', 'mkv'].includes(ext)) return '#67c23a'
  if (['mp3', 'wav', 'aac', 'flac', 'ogg'].includes(ext)) return '#f56c6c'
  if (['pdf'].includes(ext)) return '#e04040'
  if (['zip', 'rar', '7z', 'tar', 'gz'].includes(ext)) return '#909399'
  return '#409eff'
}

// ==================== 获取文件列表 ====================
async function fetchFileList() {
  loading.value = true
  try {
    const res = await getFileList(currentPage.value, pageSize.value)
    const pageResult = res.data as PageResult<SharedFile>
    fileList.value = pageResult.records
    total.value = pageResult.total
  } catch {
    fileList.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

// ==================== 搜索 ====================
let searchTimer: ReturnType<typeof setTimeout> | null = null
function onSearch() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    // 简单前端过滤（也可改为后端搜索）
    currentPage.value = 1
    if (searchKeyword.value.trim() === '') {
      fetchFileList()
    }
  }, 300)
}

// ==================== 排序 ====================
function onSortChange(sort: { prop: string; order: string }) {
  if (sort.order) {
    fileList.value.sort((a, b) => {
      const key = sort.prop as keyof SharedFile
      let aVal: unknown = a[key]
      let bVal: unknown = b[key]
      if (key === 'fileSize') {
        return sort.order === 'ascending'
          ? (aVal as number) - (bVal as number)
          : (bVal as number) - (aVal as number)
      }
      if (key === 'createTime' || key === 'fileName') {
        aVal = String(aVal)
        bVal = String(bVal)
        return sort.order === 'ascending'
          ? (aVal as string).localeCompare(bVal as string)
          : (bVal as string).localeCompare(aVal as string)
      }
      return 0
    })
  }
}

// ==================== 文件上传 ====================
function handleFileChange(file: UploadFile) {
  if (file.raw) {
    pendingFiles.value.push({
      name: file.name,
      size: file.size || 0,
      raw: file.raw,
      status: 'pending',
    })
  }
}

function removePendingFile(index: number) {
  pendingFiles.value.splice(index, 1)
}

async function startUpload() {
  if (pendingFiles.value.length === 0) return
  uploading.value = true

  const toUpload = pendingFiles.value.filter((f) => f.status !== 'success')
  let successCount = 0
  let failCount = 0

  for (const file of toUpload) {
    file.status = 'uploading'
    try {
      await uploadFile(file.raw)
      file.status = 'success'
      successCount++
    } catch {
      file.status = 'error'
      failCount++
    }
  }

  uploading.value = false

  if (successCount > 0) {
    ElMessage.success(`成功上传 ${successCount} 个文件`)
    // 清除已上传成功的文件
    pendingFiles.value = pendingFiles.value.filter((f) => f.status !== 'success')
    // 刷新列表
    currentPage.value = 1
    fetchFileList()
  }
  if (failCount > 0) {
    ElMessage.error(`${failCount} 个文件上传失败`)
  }
}

// ==================== 文件下载 ====================
function downloadFile(row: SharedFile) {
  const url = getDownloadUrl(row.id)
  const a = document.createElement('a')
  a.href = url
  a.download = row.fileName
  a.style.display = 'none'
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
}

// ==================== 文件删除 ====================
async function confirmDelete(row: SharedFile) {
  try {
    await ElMessageBox.confirm(
      `确定要删除文件「${row.fileName}」吗？删除后将不可恢复。`,
      '删除确认',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )
    await deleteFile(row.id)
    ElMessage.success('文件已删除')
    fetchFileList()
  } catch {
    // 用户取消
  }
}

// ==================== 生命周期 ====================
onMounted(() => {
  fetchFileList()
})
</script>

<style scoped>
.file-share-container {
  max-width: 1100px;
  margin: 0 auto;
  padding: 4px 0;
}

/* ---- 页面标题 ---- */
.page-header {
  margin-bottom: 20px;
}

.page-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 20px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 6px 0;
}

.page-subtitle {
  font-size: 13px;
  color: #909399;
  margin: 0;
}

/* ---- 上传卡片 ---- */
.upload-card {
  margin-bottom: 20px;
}

.upload-area {
  width: 100%;
}

.upload-icon {
  color: #409eff;
  margin-bottom: 8px;
}

.upload-text {
  text-align: center;
}

.upload-title {
  font-size: 15px;
  color: #303133;
  margin: 0 0 4px 0;
  font-weight: 500;
}

.upload-hint {
  font-size: 12px;
  color: #c0c4cc;
  margin: 0;
}

/* ---- 待上传队列 ---- */
.pending-queue {
  margin-top: 16px;
  border-top: 1px solid #ebeef5;
  padding-top: 14px;
}

.queue-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.queue-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.queue-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 6px;
  margin-bottom: 6px;
  transition: background 0.2s;
}

.queue-item:hover {
  background: #ecf5ff;
}

.queue-item-info {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.queue-item-name {
  font-size: 13px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.queue-item-size {
  font-size: 12px;
  color: #909399;
  flex-shrink: 0;
}

.queue-item-actions {
  flex-shrink: 0;
  margin-left: 12px;
}

/* ---- 列表卡片 ---- */
.list-card {
  border-radius: 8px;
}

.list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.list-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

/* ---- 表格单元格 ---- */
.file-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.file-name-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
}

.file-size-text {
  font-size: 13px;
  color: #606266;
  font-variant-numeric: tabular-nums;
}

.time-text {
  font-size: 13px;
  color: #606266;
  font-variant-numeric: tabular-nums;
}

/* ---- 分页 ---- */
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

/* 上传组件样式微调 */
:deep(.el-upload-dragger) {
  border-radius: 8px;
  padding: 32px 20px;
}

:deep(.el-table) {
  font-size: 13px;
}

:deep(.el-table th.el-table__cell) {
  background-color: #f5f7fa;
  color: #303133;
  font-weight: 600;
}
</style>
