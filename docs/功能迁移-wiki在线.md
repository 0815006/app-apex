# Wiki 在线文档 — 迁移复刻指南

> 从 Apex 项目中提取「Wiki 在线文档」功能的完整迁移文档。
>
> **源项目技术栈**: Vue 3 + Element Plus + TypeScript (前端) / Spring Boot + MyBatis-Plus + Flyway (后端)

---

## 目录

- [一、功能概览](#一功能概览)
- [二、文件清单](#二文件清单)
- [三、数据库](#三数据库)
- [四、后端实现](#四后端实现)
- [五、前端实现](#五前端实现)
- [六、依赖与配置](#六依赖与配置)
- [七、迁移步骤](#七迁移步骤)
- [附录](#附录)

---

## 一、功能概览

- **树形目录结构**：文件夹 + Markdown 文档，无限层级嵌套，单表存储
- **Markdown 编辑器**：基于 `md-editor-v3`，分屏编辑/预览
- **双链 `[[title]]` 跳转**：自动识别 `[[文档标题]]` 语法，点击跳转，未找到则提示新建
- **拖拽排序**：el-tree 原生拖拽，支持同级排序和移入文件夹
- **导入/导出**：导入 `.md` 文件，导出文档为 Markdown 下载
- **面包屑导航**：完整路径导航，支持点击跳转至任意层级
- **可拖拽分隔线**：左侧目录树宽度 200~600px 可调，持久化到 localStorage
- **搜索过滤**：实时过滤目录树节点
- **右键菜单**：新建文档/文件夹、导入 .md、重命名、导出、删除

---

## 二、文件清单

### 前端 (5 个源文件)

```
web-apex-vue/src/
├── views/
│   └── WikiManager.vue              # 主页面（~1270行，三栏布局）
├── components/wiki/
│   └── FolderDocList.vue            # 文件夹内容卡片列表子组件
├── api/
│   └── wiki.ts                      # API 接口封装（8 个端点）
├── types/
│   └── wiki.ts                      # WikiNodeVO / WikiDocument 类型定义
└── utils/
    └── request.ts                   # Axios 实例（拦截器、统一错误处理）
```

### 后端 (10 个源文件)

```
java-apex-server/src/main/java/com/apex/
├── controller/
│   └── WikiController.java          # REST 控制器，8 个端点
├── service/
│   └── WikiService.java             # 核心业务逻辑
├── entity/
│   └── WikiDocument.java            # MyBatis-Plus 实体类
├── mapper/
│   └── WikiDocumentMapper.java      # BaseMapper 接口
├── model/
│   ├── WikiNodeVO.java              # 树节点 VO（record，含递归 children）
│   ├── MoveNodeDTO.java             # 移动节点 DTO
│   └── SortOrderDTO.java            # 批量排序 DTO
├── common/
│   ├── Result.java                  # 统一响应封装 {code, message, data}
│   ├── BusinessException.java       # 业务异常（可带错误码）
│   └── GlobalExceptionHandler.java  # 全局异常处理器

java-apex-server/src/main/resources/db/migration/
└── V1__create_wiki_document.sql     # Flyway 建表 DDL
```

> **注意**: `EmpContext` 和 `currentUser.ts` 是源项目的员工号追踪机制，如果你的项目有自己的认证体系（如 JWT），直接替换即可，不影响 Wiki 核心逻辑。

---

## 三、数据库

### DDL

```sql
CREATE TABLE `wiki_document` (
    `id`          VARCHAR(32)  NOT NULL COMMENT '唯一主键（雪花ID）',
    `title`       VARCHAR(255) NOT NULL COMMENT '文档或文件夹标题',
    `content`     LONGTEXT     COMMENT 'Markdown原始内容（文件夹此项为空）',
    `type`        TINYINT      DEFAULT 2 COMMENT '节点类型: 1-文件夹, 2-文档',
    `parent_id`   VARCHAR(32)  DEFAULT '0' COMMENT '父级ID，0表示根目录',
    `sort_order`  INT          DEFAULT 0 COMMENT '同层级排序权重，数字越小越靠前',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_title` (`title`) COMMENT '标题唯一，确保双链能精准定位'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Wiki文档主表';
```

### 关键设计

| 设计点 | 说明 |
|--------|------|
| **单表树形** | 文件夹和文档存同一张表，`type` 区分 (1=文件夹, 2=文档)，`parent_id` 构建层级 |
| **雪花ID** | MyBatis-Plus `IdType.ASSIGN_ID` 自动生成，避免自增冲突 |
| **标题唯一** | `uk_title` 唯一索引，保证 `[[双链]]` 精准定位 |
| **整数间隔排序** | `sort_order` 用 0, 10, 20... 间隔法，插入取中位数，移动后 `reorderSiblings()` 归一化 |
| **级联删除** | 递归收集子孙节点 ID → `deleteBatchIds` 批量删除 |

---

## 四、后端实现

### 4.1 Entity: WikiDocument.java

```java
@Data
@TableName("wiki_document")
public class WikiDocument {
    @TableId(type = IdType.ASSIGN_ID)   // 雪花ID
    private String id;
    private String title;               // 唯一
    private String content;             // LONGTEXT，文件夹为 null
    private Integer type;               // 1=文件夹, 2=文档
    private String parentId;            // "0" = 根目录
    private Integer sortOrder;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

### 4.2 Mapper: WikiDocumentMapper.java

```java
@Mapper
public interface WikiDocumentMapper extends BaseMapper<WikiDocument> {
    // 无需自定义 SQL，全部使用 MyBatis-Plus 内置方法
}
```

### 4.3 Controller: 8 个 REST 端点

Base path: `/api/wiki`

| 方法 | 路径 | 说明 | 请求体/参数 |
|------|------|------|------------|
| GET | `/tree` | 获取完整目录树 | — |
| GET | `/{id}` | 获取文档详情 | — |
| GET | `/by-title` | 按标题查文档（双链跳转） | `?title=xxx` |
| POST | `/save` | 创建或更新文档 | `WikiDocument` JSON |
| DELETE | `/{id}` | 级联删除节点及其子节点 | — |
| PUT | `/{id}/move` | 移动节点 | `{newParentId, newSortOrder}` |
| PUT | `/sort-batch` | 批量更新排序 | `{items: [{id, sortOrder}]}` |
| GET | `/{folderId}/children` | 获取文件夹直接子节点 | — |

### 4.4 Service 核心方法

**`buildWikiTree()`** — 构建目录树
```
查询全表 → 按 sort_order 排序 → 过滤 parentId="0" 的根节点 → 递归 convertToVO()
```

**`saveOrUpdate(doc)`** — 创建/更新
```
标题唯一性校验（查重，排除自身）→ insertOrUpdate → 重新查询返回完整数据
```

**`deleteById(id)`** — 级联删除
```
递归 collectChildrenIds() → 收集所有子孙节点 → deleteBatchIds()
```

**`moveNode(id, newParentId, newSortOrder)`** — 移动节点
```
1. 校验节点存在
2. 防止循环引用（目标不能是自身或子孙）
3. 校验目标父节点存在且为文件夹
4. 若跨父节点 → reorderSiblings(旧父节点)
5. 更新 parentId + sortOrder
6. reorderSiblings(新父节点)
```

**`reorderSiblings(parentId)`** — 排序归一化
```
取同父节点下所有子节点 → 按 sort_order 排序 → 重新设为 0, 10, 20, 30...
```

### 4.5 支撑类（适配到目标项目）

| 类 | 作用 | 迁移建议 |
|----|------|---------|
| `Result<T>` | `{code: 200, message: "success", data: T}` | 用目标项目已有的统一响应类 |
| `BusinessException` | `RuntimeException` + code/message | 用目标项目已有的异常类 |
| `GlobalExceptionHandler` | `@RestControllerAdvice` 统一异常处理 | 用目标项目已有的 |

**WikiService 中对这几个类的依赖**：
- `throw new BusinessException(404, "文档不存在")` — 用于 `getById`、`getByTitle`
- `throw new BusinessException("标题已存在")` — 用于 `saveOrUpdate` 唯一性校验
- `throw new BusinessException("不能移动到自身或子孙节点下")` — 用于 `moveNode`
- `Result.success(data)` / `Result.success()` — Controller 层返回值

如果你目标项目用不同的响应格式，只需修改 Controller 返回值的包装方式即可。

---

## 五、前端实现

### 5.1 页面布局

```
┌──────────────────┬─────────────────────────────────┐
│  左侧边栏 (可拖拽) │  右侧内容区                       │
│  默认 320px       │                                  │
│                   │  面包屑: 工作区 / 文件夹 / 文档     │
│  [导入] [搜索框]   │  ─────────────────────────────── │
│                   │                                  │
│  📁 目录树        │  ┌─ 文件夹视图（点击文件夹时）───── │
│  (el-tree)       │  │ FolderDocList 卡片列表         │
│    📁 文件夹      │  └──────────────────────────────── │
│      📄 文档1     │                                  │
│      📄 文档2     │  ┌─ 文档预览（点击文档时）───────── │
│    📁 文件夹2     │  │ h1 标题                        │
│                   │  │ MdPreview 渲染内容             │
│  ──────────────── │  └──────────────────────────────── │
│  [新建根文档]      │                                  │
│  [新建根文件夹]    │  ┌─ 编辑模式 ────────────────────── │
│  [编辑当前文档]    │  │ 标题 input + MdEditor 编辑器    │
│                   │  └──────────────────────────────── │
├──────────────────┤                                  │
│  可拖拽分隔线      │                                  │
└──────────────────┴─────────────────────────────────┘
```

### 5.2 TypeScript 类型 ([types/wiki.ts](web-apex-vue/src/types/wiki.ts))

```ts
export interface WikiNodeVO {
  id: string
  title: string
  type: number          // 1=文件夹, 2=文档
  parentId: string
  updateTime?: string
  children?: WikiNodeVO[] | null
}

export interface WikiDocument {
  id?: string
  title: string
  content: string
  type: number
  parentId: string
  sortOrder?: number
  createTime?: string
  updateTime?: string
}
```

### 5.3 API 封装 ([api/wiki.ts](web-apex-vue/src/api/wiki.ts))

```ts
import request from '@/utils/request'
import type { WikiNodeVO, WikiDocument } from '@/types/wiki'

export function getWikiTree(): Promise<{ code: number; message: string; data: WikiNodeVO[] }> {
  return request.get('/wiki/tree')
}

export function getDocDetail(id: string): Promise<{ code: number; message: string; data: WikiDocument }> {
  return request.get(`/wiki/${id}`)
}

export function getDocByTitle(title: string): Promise<{ code: number; message: string; data: WikiDocument }> {
  return request.get('/wiki/by-title', { params: { title } })
}

export function saveDoc(doc: {
  id?: string; title: string; content?: string; type: number; parentId?: string; sortOrder?: number
}): Promise<{ code: number; message: string; data: WikiDocument }> {
  return request.post('/wiki/save', doc)
}

export function deleteDoc(id: string): Promise<{ code: number; message: string; data: null }> {
  return request.delete(`/wiki/${id}`)
}

export function moveNode(
  id: string, newParentId: string, newSortOrder: number
): Promise<{ code: number; message: string; data: null }> {
  return request.put(`/wiki/${id}/move`, { newParentId, newSortOrder })
}

export function batchUpdateSortOrder(
  items: { id: string; sortOrder: number }[]
): Promise<{ code: number; message: string; data: null }> {
  return request.put('/wiki/sort-batch', { items })
}

export function getFolderChildren(
  folderId: string
): Promise<{ code: number; message: string; data: WikiNodeVO[] }> {
  return request.get(`/wiki/${folderId}/children`)
}
```

### 5.4 WikiManager.vue 核心状态与逻辑

```ts
// 核心响应式状态
const wikiTreeData = ref<WikiNodeVO[]>([])        // 完整树数据
const currentDoc = ref<WikiDocument>({...})         // 当前选中文档
const currentFolder = ref<WikiNodeVO | null>(null)  // 当前选中文件夹
const folderChildren = ref<WikiNodeVO[]>([])        // 文件夹直系子节点
const isEditing = ref(false)                        // 编辑/预览模式
const filterText = ref('')                          // 搜索文本
const sidebarWidth = ref(320)                       // 左侧栏宽度
```

#### 关键功能点

**1. 双链 `[[title]]` 跳转**

内容渲染时将 `[[标题]]` 替换为可点击链接，通过事件委托捕获点击：

```ts
// 计算属性：正则替换 [[...]] 为 <a data-wiki-title="...">
const processedContent = computed(() => {
  const regex = /\[\[(.*?)\]\]/g
  return currentDoc.value.content.replace(regex, (_, title) => {
    return `<a href="javascript:void(0);" data-wiki-title="${title}"
             class="wiki-internal-double-link">🔗 ${title}</a>`
  })
})

// 事件委托：捕获点击 → 调用 getDocByTitle API
const handleContentAreaClick = async (e: MouseEvent) => {
  const targetLink = e.target.closest('.wiki-internal-double-link')
  if (!targetLink) return
  const title = targetLink.getAttribute('data-wiki-title')
  // API 返回 404 时弹窗：是否新建该文档
}
```

**2. 拖拽排序**

利用 el-tree 的 `draggable` + `allowDrop` + `@node-drop`：

```ts
// 只允许文件夹接收 "inner" 拖放
const allowDrop = (_draggingNode, dropNode, type) => {
  if (type === 'inner') return dropNode.data.type === 1
  return true
}

// 拖放完成后计算新 parentId 和 sortOrder
const handleNodeDrop = async (draggingNode, dropNode, dropType) => {
  // 计算 newParentId 和 newSortOrder → moveNode API → 刷新树
}
```

**3. Markdown 导入/导出**

```ts
// 导入：FileReader 读取 .md → saveDoc API
const handleMdFileChange = (e: Event) => {
  const file = e.target.files?.[0]
  const reader = new FileReader()
  reader.onload = async (event) => {
    const content = event.target?.result as string
    const title = file.name.replace(/\.md$/i, '')
    await saveDoc({ title, content, type: 2, parentId })
    await loadTree()
  }
  reader.readAsText(file)
}

// 导出：getDocDetail → Blob → <a download>
const handleExport = async (data: WikiNodeVO) => {
  const res = await getDocDetail(data.id)
  const blob = new Blob([res.data.content], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  // 创建 <a> 标签触发浏览器下载
}
```

**4. 可拖拽分隔线**

```ts
const sidebarWidth = ref(loadSidebarWidth())  // 从 localStorage 加载

// mousedown → 注册 mousemove/mouseup
// mousemove → sidebarWidth = clamp(200, delta, 600)
// mouseup → 保存到 localStorage('apex_wiki_sidebar_width')
```

**5. 面包屑导航**

从 `wikiTreeData` 中递归查找从根到当前节点的完整路径：

```ts
const breadcrumbPath = computed(() => {
  const targetId = currentDoc.value.id || currentFolder.value?.id
  if (!targetId) return []
  // 在树中递归查找路径
  const path: { id: string; title: string }[] = []
  findPath(wikiTreeData.value, []) // 深度优先搜索
  return path
})
```

### 5.5 FolderDocList.vue 子组件

纯展示组件，接收 Props，通过 Emits 与父组件通信：

```ts
defineProps<{
  folderTitle: string      // 文件夹标题
  children: WikiNodeVO[]    // 子节点列表
  loading: boolean          // 加载状态
}>()

defineEmits<{
  selectNode: [item: WikiNodeVO]  // 点击节点 → 父组件处理跳转
  createDoc: []                   // 新建文档
  importMarkdown: []              // 导入 Markdown
}>()
```

以卡片网格 (`grid grid-cols-1 gap-3`) 渲染子节点，区分文件夹 (📁 + 琥珀色) 和文档 (📄 + 靛蓝色) 样式。

### 5.6 Axios 实例 ([utils/request.ts](web-apex-vue/src/utils/request.ts))

```ts
const request = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

// 请求拦截器：注入认证信息
request.interceptors.request.use((config) => {
  const empNo = getCurrentEmpNo()
  if (empNo) config.headers['X-Emp-No'] = empNo
  return config
})

// 响应拦截器：统一错误处理
// 放行 code === 200（成功）和 code === 404（Wiki 双链 404 由页面自行处理）
// 其他错误码 → ElMessage.error + reject
```

> **迁移要点**: 把 `baseURL` 和请求拦截器中的认证逻辑替换为目标项目的。

---

## 六、依赖与配置

### 6.1 前端依赖

```json
{
  "vue": "^3.x",
  "vue-router": "^4.x",
  "element-plus": "^2.x",
  "@element-plus/icons-vue": "^2.x",
  "md-editor-v3": "^4.x",
  "axios": "^1.x"
}
```

| 包 | 用途 |
|----|------|
| `element-plus` | `el-tree`、`el-button`、`el-dialog`、`el-input`、`el-dropdown`、`el-tag`、`el-empty`、`el-message`、`el-message-box`、`el-tooltip` |
| `@element-plus/icons-vue` | `Search`, `Refresh`, `Upload`, `Folder`, `Document`, `MoreFilled`, `DocumentAdd`, `FolderAdd`, `Edit`, `Delete`, `EditPen`, `CaretTop`, `CaretBottom`, `Download` |
| `md-editor-v3` | `MdEditor`（编辑器）、`MdPreview`（预览器），需引入样式 `import 'md-editor-v3/lib/style.css'` |

### 6.2 后端依赖

```xml
<!-- Spring Boot 3 + MyBatis-Plus -->
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
</dependency>
<!-- Flyway -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-mysql</artifactId>
</dependency>
<!-- Lombok -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
</dependency>
```

### 6.3 application.yml 关键配置

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
mybatis-plus:
  global-config:
    db-config:
      id-type: assign_id        # 雪花ID
```

---

## 七、迁移步骤

### Step 1 — 建库

执行 `V1__create_wiki_document.sql`，调整 Flyway 版本号以适应目标项目。

### Step 2 — 复制后端

1. **直接复制的文件**（改包名即可）：
   - `WikiDocument.java`（Entity）
   - `WikiDocumentMapper.java`（Mapper）
   - `WikiNodeVO.java`、`MoveNodeDTO.java`、`SortOrderDTO.java`（VO/DTO）

2. **需要适配的文件**：
   - `WikiService.java` — 将 `BusinessException` 替换为目标项目的异常类；将 `EmpContext.getEmpNo()` 日志替换为目标项目的用户获取方式
   - `WikiController.java` — 将 `Result.success()` 替换为目标项目的响应包装；将 `EmpContext.getEmpNo()` 替换

3. **用目标项目已有的**：
   - `Result` 统一响应类
   - `BusinessException` / 全局异常处理
   - 认证拦截器（替换 `EmpContext` + `EmpContextConfig`）

### Step 3 — 复制前端

1. **直接复制的文件**：
   - `types/wiki.ts`
   - `api/wiki.ts`（改 `request` 导入路径）
   - `components/wiki/FolderDocList.vue`

2. **需要适配的组件**：
   - `WikiManager.vue` — 调整可能涉及的认证相关逻辑（源项目中通过 `request.ts` 拦截器统一处理，WikiManager 本身不直接依赖认证）

3. **需要整合的**：
   - 将 `request.ts` 的拦截器逻辑合并到目标项目的 Axios 实例
   - 注册 Vue Router 路由：
     ```ts
     { path: 'wiki', name: 'Wiki', component: () => import('@/views/WikiManager.vue') }
     ```

### Step 4 — 安装依赖 & 启动

```bash
# 前端
npm install md-editor-v3

# 后端 — 确保 Flyway 迁移自动执行，然后启动
mvn spring-boot:run

# 前端
npm run dev
```

### Step 5 — 验证

- [ ] 新建根文件夹 → 树刷新
- [ ] 文件夹下新建文档 → 编辑 Markdown → 保存 → 内容持久化
- [ ] 拖拽文档到另一个文件夹 → 层级和排序正确
- [ ] 文档中写入 `[[另一文档标题]]` → 点击跳转
- [ ] `[[不存在的文档]]` → 弹窗提示新建
- [ ] 导入 `.md` 文件 → 解析并存入
- [ ] 导出文档为 `.md` → 下载文件内容完整
- [ ] 拖拽分隔线调整左侧栏宽度 → 刷新后宽度保持
- [ ] 搜索框输入关键字 → 树节点过滤

---

## 附录

### A. 完整文件依赖图

```
WikiManager.vue
├── el-tree (Element Plus)         ← 左侧目录树
├── MdPreview / MdEditor           ← md-editor-v3
├── FolderDocList.vue              ← 文件夹内容卡片
├── api/wiki.ts                    ← API 封装层
│   └── utils/request.ts           ← Axios 实例 (baseURL: /api)
├── types/wiki.ts                  ← TS 类型定义
└── localStorage                   ← 侧边栏宽度持久化

WikiController.java                ← /api/wiki/*
├── WikiService.java
│   └── WikiDocumentMapper.java    ← MyBatis-Plus BaseMapper
│       └── wiki_document 表
├── WikiDocument.java (Entity)
├── WikiNodeVO.java (VO)
├── MoveNodeDTO.java (DTO)
└── SortOrderDTO.java (DTO)
```

### B. 适配清单

| 需要替换的内容 | 说明 |
|--------------|------|
| `EmpContext.getEmpNo()` | Controller/Service 日志中的用户标识，替换为目标项目的用户获取方式 |
| `BusinessException` | 替换为目标项目的业务异常类 |
| `Result<T>` | 替换为目标项目的统一响应类 |
| `request.ts` 中的 `baseURL` | 调整为目标项目的 API 前缀 |
| `request.ts` 拦截器认证逻辑 | 替换为目标项目的 Token/Cookie 注入方式 |
| `currentUser.ts` | 如果目标项目有现成的用户状态管理，Wiki 不直接依赖它（仅通过 request 拦截器间接使用） |
