<template>
  <div class="capacity-monitor">
    <!-- 顶部操作栏 -->
    <div class="page-header">
      <h2 class="page-title">容量监控</h2>
      <el-button type="primary" @click="openAddMachineDialog">
        <el-icon><Plus /></el-icon> 添加机器
      </el-button>
    </div>

    <!-- 机器卡片网格 -->
    <div v-loading="loading" class="machine-grid">
      <template v-if="machines.length > 0">
        <MachineCard
          v-for="m in machines"
          :key="m.id"
          :machine="m"
          @open-detail="openScanDialog"
          @edit="openEditDialog"
          @changed="loadMachines"
        />
      </template>
      <el-empty v-else-if="!loading" description="暂无监控机器，点击右上角添加" />
    </div>

    <!-- 新增/编辑机器弹窗 -->
    <MachineFormDialog
      v-model="showFormDialog"
      :machine="editingMachine"
      @saved="loadMachines"
    />

    <!-- 全量扫描大弹窗 -->
    <MonitorScanDialog
      v-model="showScanDialog"
      :machine="scanTargetMachine"
      @closed="loadMachines"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { MonitorMachine } from '@/types/monitor'
import { getMachineList } from '@/api/monitor'
import MachineCard from '@/components/monitor/MachineCard.vue'
import MachineFormDialog from '@/components/monitor/MachineFormDialog.vue'
import MonitorScanDialog from '@/components/monitor/MonitorScanDialog.vue'

const loading = ref(false)
const machines = ref<MonitorMachine[]>([])

// 机器表单弹窗
const showFormDialog = ref(false)
const editingMachine = ref<MonitorMachine | null>(null)

// 扫描弹窗
const showScanDialog = ref(false)
const scanTargetMachine = ref<MonitorMachine | null>(null)

async function loadMachines() {
  loading.value = true
  try {
    machines.value = await getMachineList()
  } catch {
    machines.value = []
  } finally {
    loading.value = false
  }
}

function openAddMachineDialog() {
  editingMachine.value = null
  showFormDialog.value = true
}

function openScanDialog(machine: MonitorMachine) {
  scanTargetMachine.value = machine
  showScanDialog.value = true
}

function openEditDialog(machine: MonitorMachine) {
  editingMachine.value = machine
  showFormDialog.value = true
}

onMounted(() => {
  loadMachines()
})
</script>

<style scoped>
.capacity-monitor {
  padding: 20px;
  height: 100%;
  overflow-y: auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  margin: 0;
  color: #303133;
}

.machine-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 16px;
  min-height: 200px;
}
</style>
