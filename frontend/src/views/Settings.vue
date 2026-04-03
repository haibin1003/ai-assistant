<template>
  <div class="settings-container">
    <!-- 顶部导航 -->
    <header class="settings-header">
      <div class="header-left">
        <h1>AI 助手</h1>
      </div>
      <nav class="header-nav">
        <router-link to="/chat" class="nav-link">对话</router-link>
        <router-link to="/settings" class="nav-link active">配置</router-link>
      </nav>
      <div class="header-right">
        <div v-if="isLoggedIn" class="user-info">
          <span class="username">{{ username }}</span>
          <button @click="logout" class="btn-logout">退出</button>
        </div>
        <div v-else>
          <button @click="showLoginDialog = true" class="btn-login">登录系统</button>
        </div>
      </div>
    </header>

    <!-- 主要内容 -->
    <main class="settings-main">
      <!-- Tab 导航 -->
      <div class="settings-tabs">
        <button :class="['tab-btn', { active: activeTab === 'skills' }]" @click="activeTab = 'skills'; fetchPublishedSkills()">
          Skills 管理
          <span class="tab-count" v-if="publishedSkills.length">{{ publishedSkills.length }}</span>
        </button>
        <button :class="['tab-btn', { active: activeTab === 'systems' }]" @click="activeTab = 'systems'">
          系统接入
          <span class="tab-count" v-if="systems.length">{{ systems.length }}</span>
        </button>
        <button :class="['tab-btn', { active: activeTab === 'apikeys' }]" @click="activeTab = 'apikeys'; fetchApiKeys()">
          API Keys
          <span class="tab-count success" v-if="configuredApiKeysCount">{{ configuredApiKeysCount }}</span>
        </button>
      </div>

      <!-- Skills 管理 -->
      <div v-if="activeTab === 'skills'" class="tab-content">
        <div class="content-header">
          <div>
            <h2>已发布的 Skills</h2>
            <p class="desc">管理你的自定义技能扩展</p>
          </div>
          <button class="btn-primary" @click="openCreateSkill">
            <span>+</span> 创建 Skill
          </button>
        </div>

        <!-- Skills 列表 -->
        <div class="skills-grid" v-if="publishedSkills.length > 0">
          <div v-for="skill in publishedSkills" :key="skill.skillId" class="skill-card">
            <div class="skill-icon">S</div>
            <div class="skill-info">
              <h3>{{ skill.name }}</h3>
              <p>{{ skill.description }}</p>
              <div class="skill-meta">
                <span class="badge">v{{ skill.version }}</span>
                <span class="meta">{{ skill.skillId }}</span>
              </div>
            </div>
            <div class="skill-actions">
              <button @click="viewSkillDetail(skill)" class="btn-text">查看</button>
              <button @click="reloadSkill(skill.skillId)" class="btn-text">重载</button>
              <button @click="deletePublishedSkill(skill.skillId)" class="btn-text danger">删除</button>
            </div>
          </div>
        </div>

        <!-- 空状态 -->
        <div v-else class="empty-state">
          <div class="empty-icon">📦</div>
          <h3>暂无已发布的 Skills</h3>
          <p>点击"创建 Skill"开始构建你的第一个技能</p>
          <button class="btn-primary" @click="openCreateSkill">+ 创建第一个 Skill</button>
        </div>
      </div>

      <!-- 系统接入 -->
      <div v-if="activeTab === 'systems'" class="tab-content">
        <div class="content-header">
          <div>
            <h2>已接入系统</h2>
            <p class="desc">管理 MCP 服务连接</p>
          </div>
        </div>

        <div class="systems-grid" v-if="systems.length > 0">
          <div v-for="system in systems" :key="system.systemId" class="system-card">
            <div class="system-icon" :class="{ active: system.isActive }">
              {{ system.systemName.charAt(0) }}
            </div>
            <div class="system-info">
              <h3>{{ system.systemName }}</h3>
              <span :class="['status-badge', system.isActive ? 'active' : 'inactive']">
                {{ system.isActive ? '活跃' : '已禁用' }}
              </span>
              <p class="system-url">{{ system.mcpGatewayUrl }}</p>
            </div>
            <div class="system-tools">
              <div class="tools-header">
                <span>工具 ({{ system.toolCount || 0 }})</span>
                <button @click="showSystemTools(system)" class="btn-text">查看详情</button>
              </div>
              <div class="tools-list" v-if="expandedSystem === system.systemId">
                <div v-for="tool in systemTools[system.systemId]" :key="tool.name" class="tool-item">
                  <span class="tool-name">{{ tool.name }}</span>
                  <span class="tool-desc">{{ tool.description }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div v-else class="empty-state">
          <div class="empty-icon">🔌</div>
          <h3>暂未接入任何系统</h3>
          <p>通过 MCP Gateway 接入外部系统</p>
        </div>
      </div>

      <!-- API Keys -->
      <div v-if="activeTab === 'apikeys'" class="tab-content">
        <div class="content-header">
          <div>
            <h2>API Keys 配置</h2>
            <p class="desc">管理 LLM 和搜索服务的 API 密钥</p>
          </div>
        </div>

        <div class="apikeys-grid">
          <div v-for="key in apiKeys" :key="key.provider" class="api-key-card" :class="{ configured: key.configured }">
            <div class="api-key-header">
              <div class="provider-icon">{{ getProviderIcon(key.provider) }}</div>
              <div>
                <h3>{{ getProviderName(key.provider) }}</h3>
                <span :class="['status-badge', key.configured ? 'active' : 'inactive']">
                  {{ key.configured ? '已配置' : '未配置' }}
                </span>
              </div>
            </div>
            <div class="api-key-details" v-if="key.configured">
              <div class="detail-row">
                <span class="label">模型</span>
                <span class="value">{{ key.model || '-' }}</span>
              </div>
              <div class="detail-row">
                <span class="label">API 地址</span>
                <span class="value">{{ key.apiEndpoint || '默认' }}</span>
              </div>
            </div>
            <div class="api-key-actions">
              <button v-if="key.configured" @click="editApiKey(key)" class="btn-text">修改</button>
              <button v-if="key.configured" @click="deleteApiKey(key.provider)" class="btn-text danger">删除</button>
              <button v-if="!key.configured" @click="editApiKey(key)" class="btn-primary small">配置</button>
            </div>
          </div>
        </div>
      </div>
    </main>

    <!-- 登录对话框 -->
    <div v-if="showLoginDialog" class="dialog-overlay" @click.self="showLoginDialog = false">
      <div class="dialog">
        <h3>登录系统</h3>
        <div class="form-group">
          <label>选择系统</label>
          <select v-model="loginForm.systemId">
            <option value="osrm">OSRM</option>
          </select>
        </div>
        <div class="form-group">
          <label>用户名</label>
          <input v-model="loginForm.username" type="text" placeholder="请输入用户名" />
        </div>
        <div class="form-group">
          <label>密码</label>
          <input v-model="loginForm.password" type="password" placeholder="请输入密码" />
        </div>
        <div class="dialog-actions">
          <button @click="showLoginDialog = false" class="btn-secondary">取消</button>
          <button @click="login" class="btn-primary">登录</button>
        </div>
      </div>
    </div>

    <!-- Skill 创建对话框 - 双栏布局 -->
    <div v-if="showSkillForm" class="dialog-overlay" @click.self="closeSkillForm">
      <div class="dialog skill-form-dialog">
        <div class="dialog-header">
          <h3>创建 Skill</h3>
          <button class="btn-close" @click="closeSkillForm">×</button>
        </div>

        <div class="dialog-body skill-form-body">
          <!-- 左侧：表单区域 -->
          <div class="form-panel">
            <!-- 基本信息 -->
            <div class="form-section">
              <h4>基本信息</h4>
              <div class="form-group">
                <label>技能名称 <span class="required">*</span></label>
                <input v-model="skillForm.name" type="text" placeholder="例如：软件搜索助手" />
              </div>
              <div class="form-group">
                <label>技能描述 <span class="required">*</span></label>
                <textarea v-model="skillForm.description" placeholder="简短描述这个技能的功能" rows="2"></textarea>
              </div>
            </div>

            <!-- SKILL.md 内容 -->
            <div class="form-section">
              <h4>SKILL.md 内容 <span class="hint">(技能核心定义)</span></h4>
              <div class="form-hint">
                这是技能的核心文件，定义了如何与用户交互。下方有详细说明和示例。
              </div>
              <textarea
                v-model="skillForm.skillMdContent"
                class="code-editor"
                rows="15"
                placeholder="# SKILL.md

## 简介
简要描述这个技能

## 触发条件
- 关键词1
- 关键词2

## 系统提示词
你是一个...

## 所需工具
- tool1
- tool2"
              ></textarea>
              <button class="btn-link" @click="showSkillMdTemplate = true">查看 SKILL.md 完整模板示例</button>
            </div>

            <!-- 附加文件 -->
            <div class="form-section">
              <h4>附加文件 <span class="optional">(可选)</span></h4>
              <div class="form-hint">可以上传脚本、参考文档或资源文件供技能使用</div>
              <div class="file-groups">
                <div class="file-group">
                  <label class="file-label">📁 脚本 (scripts/)</label>
                  <div class="file-upload-wrapper">
                    <input type="file" multiple @change="handleFileSelect($event, 'scripts')" id="scripts-input" />
                    <label for="scripts-input" class="file-upload-btn">选择文件</label>
                  </div>
                  <div class="file-list">
                    <span v-for="(f, i) in skillForm.scripts" :key="i" class="file-tag">{{ f.name }}</span>
                    <span v-if="skillForm.scripts.length === 0" class="no-file">未选择文件</span>
                  </div>
                </div>
                <div class="file-group">
                  <label class="file-label">📚 参考文档 (references/)</label>
                  <div class="file-upload-wrapper">
                    <input type="file" multiple @change="handleFileSelect($event, 'references')" id="references-input" />
                    <label for="references-input" class="file-upload-btn">选择文件</label>
                  </div>
                  <div class="file-list">
                    <span v-for="(f, i) in skillForm.references" :key="i" class="file-tag">{{ f.name }}</span>
                    <span v-if="skillForm.references.length === 0" class="no-file">未选择文件</span>
                  </div>
                </div>
                <div class="file-group">
                  <label class="file-label">🎨 资源文件 (assets/)</label>
                  <div class="file-upload-wrapper">
                    <input type="file" multiple @change="handleFileSelect($event, 'assets')" id="assets-input" />
                    <label for="assets-input" class="file-upload-btn">选择文件</label>
                  </div>
                  <div class="file-list">
                    <span v-for="(f, i) in skillForm.assets" :key="i" class="file-tag">{{ f.name }}</span>
                    <span v-if="skillForm.assets.length === 0" class="no-file">未选择文件</span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 右侧：预览区域 -->
          <div class="preview-panel">
            <h4>目录结构预览</h4>
            <div class="directory-tree">
              <div class="tree-item folder">
                <span class="icon">📂</span>
                <span class="name">{{ skillForm.name || 'skill-name' }}</span>
              </div>
              <div class="tree-item file">
                <span class="icon">📄</span>
                <span class="name">SKILL.md</span>
              </div>
              <div class="tree-item folder" v-if="skillForm.scripts.length > 0">
                <span class="icon">📁</span>
                <span class="name">scripts/</span>
                <span class="count">({{ skillForm.scripts.length }})</span>
              </div>
              <div class="tree-item folder" v-if="skillForm.references.length > 0">
                <span class="icon">📁</span>
                <span class="name">references/</span>
                <span class="count">({{ skillForm.references.length }})</span>
              </div>
              <div class="tree-item folder" v-if="skillForm.assets.length > 0">
                <span class="icon">📁</span>
                <span class="name">assets/</span>
                <span class="count">({{ skillForm.assets.length }})</span>
              </div>
              <div v-if="!skillForm.name && skillForm.scripts.length === 0 && skillForm.references.length === 0 && skillForm.assets.length === 0" class="tree-empty">
                填写技能名称后，这里会显示目录结构
              </div>
            </div>

            <!-- SKILL.md 快速说明 -->
            <div class="skill-guide">
              <h4>SKILL.md 编写指南</h4>
              <div class="guide-items">
                <div class="guide-item">
                  <span class="guide-title">## 简介</span>
                  <span class="guide-desc">简短描述技能用途</span>
                </div>
                <div class="guide-item">
                  <span class="guide-title">## 触发条件</span>
                  <span class="guide-desc">用户说什么时触发此技能</span>
                </div>
                <div class="guide-item">
                  <span class="guide-title">## 系统提示词</span>
                  <span class="guide-desc">AI 扮演的角色和行为</span>
                </div>
                <div class="guide-item">
                  <span class="guide-title">## 所需工具</span>
                  <span class="guide-desc">技能需要调用的 MCP 工具</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="dialog-footer">
          <button @click="closeSkillForm" class="btn-secondary">取消</button>
          <button @click="saveSkill" class="btn-primary" :disabled="!canSaveSkill">创建并发布</button>
        </div>
      </div>
    </div>

    <!-- SKILL.md 模板 -->
    <div v-if="showSkillMdTemplate" class="dialog-overlay" @click.self="showSkillMdTemplate = false">
      <div class="dialog wide">
        <h3>SKILL.md 完整模板示例</h3>
        <pre class="code-preview"># 软件搜索助手

## 简介
帮助用户搜索 OSRM 系统中的软件包，提供软件信息和下载命令。

## 触发条件
当用户说以下内容时会触发此技能：
- 搜索软件
- 查找软件
- 帮我找xx软件
- 有哪些xx软件

## 系统提示词
你是一个专业的软件搜索助手。当用户想要搜索软件时：
1. 先使用 osrm_search_software 工具搜索相关软件
2. 如果用户询问具体软件详情，使用 osrm_get_software_detail 获取
3. 如果用户需要下载命令，使用 osrm_get_download_command 获取

## 所需工具
- osrm_search_software: 搜索软件包
- osrm_get_software_detail: 获取软件详情
- osrm_get_download_command: 获取下载命令

## 示例对话
用户：帮我搜索 Nginx
助手：让我帮你搜索 Nginx 相关的软件包...
(调用 osrm_search_software 工具)
助手：找到了以下 Nginx 相关软件：
1. Nginx (Docker) - 最新版本 1.25.4
2. Nginx Ingress Controller (Helm)
...</pre>
        <div class="dialog-actions">
          <button @click="showSkillMdTemplate = false" class="btn-primary">知道了</button>
        </div>
      </div>
    </div>

    <!-- Skill 详情 -->
    <div v-if="showSkillDetail" class="dialog-overlay" @click.self="closeSkillDetail">
      <div class="dialog very-wide">
        <h3>{{ currentSkill?.name }}</h3>
        <div class="skill-detail-info">
          <p><strong>描述：</strong>{{ currentSkill?.description }}</p>
          <p><strong>Skill ID：</strong><code>{{ currentSkill?.skillId }}</code></p>
          <p><strong>版本：</strong>{{ currentSkill?.version }}</p>
        </div>

        <!-- 文件目录结构 -->
        <div class="skill-files-section" v-if="currentSkillFiles">
          <h4>文件结构</h4>
          <div class="file-tree">
            <div class="tree-folder">
              <span class="folder-icon">📄</span> SKILL.md
              <button @click="viewFile('SKILL.md')" class="btn-link small">查看</button>
            </div>
            <div class="tree-folder" v-if="currentSkillFiles.scripts?.length">
              <span class="folder-icon">📁</span> scripts/
              <div class="tree-children">
                <div v-for="file in currentSkillFiles.scripts" :key="file.path" class="tree-file">
                  <span class="file-icon">📜</span> {{ file.name }}
                  <button @click="viewFile(file.path)" class="btn-link small">查看</button>
                </div>
              </div>
            </div>
            <div class="tree-folder" v-if="currentSkillFiles.references?.length">
              <span class="folder-icon">📁</span> references/
              <div class="tree-children">
                <div v-for="file in currentSkillFiles.references" :key="file.path" class="tree-file">
                  <span class="file-icon">📖</span> {{ file.name }}
                  <button @click="viewFile(file.path)" class="btn-link small">查看</button>
                </div>
              </div>
            </div>
            <div class="tree-folder" v-if="currentSkillFiles.assets?.length">
              <span class="folder-icon">📁</span> assets/
              <div class="tree-children">
                <div v-for="file in currentSkillFiles.assets" :key="file.path" class="tree-file">
                  <span class="file-icon">📎</span> {{ file.name }}
                  <button @click="viewFile(file.path)" class="btn-link small">查看</button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 文件内容查看 -->
        <div class="skill-md-content" v-if="currentSkill?.skillMdContent && !viewingFile">
          <h4>SKILL.md 内容</h4>
          <pre class="code-preview">{{ currentSkill.skillMdContent }}</pre>
        </div>

        <!-- 查看指定文件内容 -->
        <div class="skill-file-content" v-if="viewingFile">
          <h4>{{ viewingFileName }}</h4>
          <pre class="code-preview">{{ viewingFileContent }}</pre>
          <button @click="closeFileView" class="btn-secondary">返回目录</button>
        </div>

        <div class="dialog-actions">
          <button @click="closeSkillDetail" class="btn-secondary">关闭</button>
        </div>
      </div>
    </div>

    <!-- API Key 配置对话框 -->
    <div v-if="showApiKeyDialog" class="dialog-overlay" @click.self="showApiKeyDialog = false">
      <div class="dialog">
        <h3>配置 {{ getProviderName(apiKeyForm.provider) }} API Key</h3>
        <div class="form-group">
          <label>服务商类型</label>
          <select v-model="apiKeyForm.providerType">
            <option value="deepseek">DeepSeek</option>
            <option value="openai">OpenAI</option>
            <option value="claude">Claude</option>
          </select>
        </div>
        <div class="form-group">
          <label>API Key <span class="required">*</span></label>
          <input v-model="apiKeyForm.apiKey" type="password" placeholder="请输入 API Key" />
        </div>
        <div class="form-group">
          <label>API 地址（可选）</label>
          <input v-model="apiKeyForm.apiEndpoint" type="text" placeholder="留空使用默认地址" />
        </div>
        <div class="dialog-actions">
          <button @click="showApiKeyDialog = false" class="btn-secondary">取消</button>
          <button @click="saveApiKey" class="btn-primary" :disabled="!apiKeyForm.apiKey.trim()">保存</button>
        </div>
      </div>
    </div>

    <!-- 系统工具详情对话框 -->
    <div v-if="showToolsDialog" class="dialog-overlay" @click.self="showToolsDialog = false">
      <div class="dialog wide">
        <h3>{{ currentSystem?.systemName }} - 工具列表</h3>
        <div class="tools-detail-list">
          <div v-for="tool in currentSystemTools" :key="tool.name" class="tool-detail-item">
            <div class="tool-detail-name">{{ tool.name }}</div>
            <div class="tool-detail-desc">{{ tool.description }}</div>
            <div class="tool-detail-schema" v-if="tool.inputSchema && tool.inputSchema.properties">
              <span class="schema-label">参数：</span>
              <span v-for="key in Object.keys(tool.inputSchema.properties)" :key="key" class="schema-param">
                {{ key }}<span v-if="tool.inputSchema.required && tool.inputSchema.required.includes(key)" class="required">*</span>
              </span>
            </div>
          </div>
        </div>
        <div class="dialog-actions">
          <button @click="showToolsDialog = false" class="btn-primary">关闭</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import api from '../api'

const activeTab = ref('skills')
const loading = ref(false)
const showLoginDialog = ref(false)
const showSkillForm = ref(false)
const showSkillMdTemplate = ref(false)
const showSkillDetail = ref(false)
const showApiKeyDialog = ref(false)
const showToolsDialog = ref(false)
const isLoggedIn = ref(false)
const username = ref('')
const currentSkill = ref<any>(null)
const currentSkillFiles = ref<any>(null)
const viewingFile = ref(false)
const viewingFileName = ref('')
const viewingFileContent = ref('')
const expandedSystem = ref<string | null>(null)
const currentSystem = ref<any>(null)
const currentSystemTools = ref<any[]>([])

const systems = ref<any[]>([])
const publishedSkills = ref<any[]>([])
const apiKeys = ref<any[]>([])
const systemTools = ref<Record<string, any[]>>({})

const loginForm = ref({ systemId: 'osrm', username: '', password: '' })

const skillForm = ref({
  name: '', description: '', skillMdContent: '',
  triggerKeywords: [] as string[], requiredTools: [] as string[],
  scripts: [] as File[], references: [] as File[], assets: [] as File[]
})

const apiKeyForm = ref({ provider: '', providerType: 'deepseek', apiKey: '', apiEndpoint: '' })

const configuredApiKeysCount = computed(() => apiKeys.value.filter(k => k.configured).length)

const canSaveSkill = computed(() => skillForm.value.name.trim() && skillForm.value.description.trim() && skillForm.value.skillMdContent.trim())

function getProviderName(provider: string) {
  const names: Record<string, string> = { deepseek: 'DeepSeek', openai: 'OpenAI', claude: 'Claude', serper: 'Serper', tavily: 'Tavily' }
  return names[provider] || provider
}

function getProviderIcon(provider: string) {
  const icons: Record<string, string> = { deepseek: 'D', openai: 'O', claude: 'C', serper: 'S', tavily: 'T' }
  return icons[provider] || '?'
}

async function fetchApiKeys() {
  loading.value = true
  try { const res = await api.get('/config/api-key'); apiKeys.value = res.data.data || [] } catch (e) { console.error(e) }
  loading.value = false
}

function editApiKey(key: any) {
  apiKeyForm.value = { provider: key.provider, providerType: key.providerType || 'deepseek', apiKey: '', apiEndpoint: key.apiEndpoint || '' }
  showApiKeyDialog.value = true
}

async function saveApiKey() {
  try {
    await api.put(`/config/api-key/${apiKeyForm.value.provider}`, { providerType: apiKeyForm.value.providerType, apiKey: apiKeyForm.value.apiKey, apiEndpoint: apiKeyForm.value.apiEndpoint || null })
    alert('API Key 保存成功！')
    showApiKeyDialog.value = false
    fetchApiKeys()
  } catch (e: any) { alert('保存失败: ' + (e.response?.data?.message || e.message)) }
}

async function deleteApiKey(provider: string) {
  if (!confirm('确定要删除这个 API Key 吗？')) return
  try { await api.delete(`/config/api-key/${provider}`); alert('删除成功'); fetchApiKeys() } catch (e: any) { alert('删除失败') }
}

async function fetchPublishedSkills() {
  loading.value = true
  try { const res = await api.get('/skill-packages'); publishedSkills.value = res.data.data || [] } catch (e) { console.error(e) }
  loading.value = false
}

async function fetchSystems() {
  loading.value = true
  try { const res = await api.get('/systems'); systems.value = res.data.data || [] } catch (e) { console.error(e) }
  loading.value = false
}

async function showSystemTools(system: any) {
  if (expandedSystem.value === system.systemId) {
    expandedSystem.value = null
  } else {
    expandedSystem.value = system.systemId
    if (!systemTools.value[system.systemId]) {
      try {
        const res = await api.get(`/systems/${system.systemId}/tools`)
        systemTools.value[system.systemId] = res.data.data || []
      } catch (e) { console.error(e) }
    }
  }
}

async function login() {
  try {
    const sessionId = 'session-' + Date.now()
    const res = await api.post('/context/push', { sessionId, systemId: loginForm.value.systemId, user: { id: 1, username: loginForm.value.username, roles: ['USER'] }, credentials: { username: loginForm.value.username, password: loginForm.value.password } })
    if (res.data && res.data.code === 200) {
      sessionStorage.setItem('sessionId', sessionId)
      sessionStorage.setItem('username', loginForm.value.username)
      isLoggedIn.value = true
      username.value = loginForm.value.username
      showLoginDialog.value = false
      loginForm.value = { systemId: 'osrm', username: '', password: '' }
    } else { alert('登录失败') }
  } catch (e: any) { alert('登录失败: ' + (e.response?.data?.message || e.message)) }
}

function logout() { sessionStorage.removeItem('sessionId'); sessionStorage.removeItem('username'); isLoggedIn.value = false; username.value = '' }

function openCreateSkill() {
  skillForm.value = { name: '', description: '', skillMdContent: '', triggerKeywords: [], requiredTools: [], scripts: [], references: [], assets: [] }
  showSkillForm.value = true
}

function closeSkillForm() { showSkillForm.value = false }

function handleFileSelect(event: Event, type: 'scripts' | 'references' | 'assets') {
  const target = event.target as HTMLInputElement
  if (target.files) { skillForm.value[type] = Array.from(target.files) }
}

async function saveSkill() {
  try {
    const formData = new FormData()
    formData.append('name', skillForm.value.name)
    formData.append('description', skillForm.value.description)
    formData.append('skillMdContent', skillForm.value.skillMdContent)
    skillForm.value.triggerKeywords.forEach(k => formData.append('triggerKeywords', k))
    skillForm.value.requiredTools.forEach(t => formData.append('requiredTools', t))
    skillForm.value.scripts.forEach(f => formData.append('scripts', f))
    skillForm.value.references.forEach(f => formData.append('references', f))
    skillForm.value.assets.forEach(f => formData.append('assets', f))
    const res = await api.post('/skill-packages', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
    if (res.data && res.data.code === 200) { alert('Skill 创建成功！'); closeSkillForm(); fetchPublishedSkills() }
    else { alert('创建失败: ' + (res.data?.message || '未知错误')) }
  } catch (e: any) { alert('创建失败: ' + (e.response?.data?.message || e.message)) }
}

async function reloadSkill(skillId: string) {
  try { await api.post(`/skill-packages/${skillId}/reload`); alert('Skill 重载成功！') } catch (e: any) { alert('重载失败') }
}

async function deletePublishedSkill(skillId: string) {
  if (!confirm('确定要删除这个 Skill 吗？')) return
  try { await api.delete(`/skill-packages/${skillId}`); alert('删除成功'); fetchPublishedSkills() } catch (e: any) { alert('删除失败') }
}

function viewSkillDetail(skill: any) {
  currentSkill.value = skill
  currentSkillFiles.value = null
  viewingFile.value = false
  showSkillDetail.value = true
  // Fetch SKILL.md content
  api.get(`/skill-packages/${skill.skillId}/content`).then(res => {
    if (res.data && res.data.data) {
      currentSkill.value = { ...currentSkill.value, skillMdContent: res.data.data }
    }
  }).catch(() => {
    currentSkill.value = { ...currentSkill.value, skillMdContent: '无法加载内容' }
  })
  // Fetch file listing
  api.get(`/skill-packages/${skill.skillId}/files`).then(res => {
    if (res.data && res.data.data) {
      currentSkillFiles.value = res.data.data
    }
  }).catch(() => {
    currentSkillFiles.value = { scripts: [], references: [], assets: [] }
  })
}

function viewFile(filePath: string) {
  const skillId = currentSkill.value?.skillId
  if (!skillId) return
  viewingFileName.value = filePath.split('/').pop() || filePath
  // filePath is like "skills/skill-demo1234/scripts/analyze_deps.py"
  // We need to extract "scripts/analyze_deps.py"
  const parts = filePath.split('/')
  // Find the index of "skill-demo1234" and take everything after it
  const skillIndex = parts.findIndex(p => p.startsWith('skill-'))
  const apiPath = skillIndex >= 0 ? parts.slice(skillIndex + 1).join('/') : filePath
  api.get(`/skill-packages/${skillId}/file`, { params: { path: apiPath } }).then(res => {
    viewingFileContent.value = res.data?.data || '无法加载文件内容'
  }).catch(() => {
    viewingFileContent.value = '无法加载文件内容'
  })
  viewingFile.value = true
}

function closeFileView() {
  viewingFile.value = false
  viewingFileName.value = ''
  viewingFileContent.value = ''
}

function closeSkillDetail() {
  showSkillDetail.value = false
  currentSkill.value = null
  currentSkillFiles.value = null
  viewingFile.value = false
}

onMounted(() => {
  const sid = sessionStorage.getItem('sessionId')
  const user = sessionStorage.getItem('username')
  if (sid && user) { isLoggedIn.value = true; username.value = user }
  fetchPublishedSkills()
  fetchSystems()
})
</script>

<style scoped>
/* ===== 基础样式 - 简洁清新风格 ===== */
.settings-container {
  min-height: 100vh;
  background: #f8f9fa;
  color: #333;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
}

/* Header */
.settings-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 32px;
  background: #fff;
  border-bottom: 1px solid #e5e5e5;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
}

.settings-header h1 { margin: 0; font-size: 18px; font-weight: 600; color: #333; }

.header-nav { display: flex; gap: 24px; }

.nav-link { text-decoration: none; color: #666; font-size: 14px; transition: color 0.2s; }
.nav-link:hover { color: #333; }
.nav-link.active { color: #2563eb; font-weight: 500; }

.header-right { display: flex; align-items: center; gap: 12px; }
.username { color: #666; font-size: 14px; }

.btn-logout, .btn-login { padding: 8px 16px; border-radius: 6px; font-size: 14px; cursor: pointer; transition: all 0.2s; }
.btn-logout { border: 1px solid #ddd; background: #fff; color: #666; }
.btn-logout:hover { border-color: #2563eb; color: #2563eb; }
.btn-login { border: none; background: #2563eb; color: #fff; }
.btn-login:hover { background: #1d4ed8; }

/* Main */
.settings-main { max-width: 1000px; margin: 0 auto; padding: 32px; }

/* Tabs */
.settings-tabs { display: flex; gap: 8px; margin-bottom: 24px; }

.tab-btn {
  display: flex; align-items: center; gap: 8px;
  padding: 10px 20px; border: none; background: #fff;
  color: #666; font-size: 14px; cursor: pointer;
  border-radius: 8px; transition: all 0.2s;
  box-shadow: 0 1px 2px rgba(0,0,0,0.04);
}
.tab-btn:hover { color: #333; background: #f0f0f0; }
.tab-btn.active { background: #2563eb; color: #fff; }

.tab-count { background: #f0f0f0; padding: 2px 8px; border-radius: 10px; font-size: 12px; }
.tab-btn.active .tab-count { background: rgba(255,255,255,0.2); }
.tab-count.success { background: #10b981; color: #fff; }

/* Content */
.tab-content { background: #fff; border-radius: 12px; padding: 24px; box-shadow: 0 1px 3px rgba(0,0,0,0.04); }

.content-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 24px; }
.content-header h2 { margin: 0 0 4px; font-size: 18px; font-weight: 600; }
.content-header .desc { margin: 0; color: #888; font-size: 14px; }

.btn-primary {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 10px 20px; background: #2563eb; color: #fff;
  border: none; border-radius: 8px; font-size: 14px; cursor: pointer;
  transition: all 0.2s;
}
.btn-primary:hover { background: #1d4ed8; }
.btn-primary.small { padding: 8px 14px; font-size: 13px; }
.btn-primary:disabled { background: #ccc; cursor: not-allowed; }

.btn-secondary { padding: 10px 20px; background: #fff; color: #666; border: 1px solid #ddd; border-radius: 8px; font-size: 14px; cursor: pointer; }
.btn-secondary:hover { background: #f5f5f5; }

.btn-text { padding: 6px 12px; background: none; border: none; color: #666; font-size: 13px; cursor: pointer; }
.btn-text:hover { color: #2563eb; }
.btn-text.danger:hover { color: #ef4444; }

.btn-link { background: none; border: none; color: #2563eb; font-size: 13px; cursor: pointer; text-decoration: underline; }

/* Cards Grid */
.skills-grid, .systems-grid, .apikeys-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 16px; }

/* Skill Card */
.skill-card, .system-card, .api-key-card {
  display: flex; gap: 16px; padding: 16px; background: #fafafa; border-radius: 10px; transition: all 0.2s;
}
.skill-card:hover, .system-card:hover, .api-key-card:hover { background: #f5f5f5; }

.skill-icon, .system-icon, .provider-icon {
  width: 48px; height: 48px; border-radius: 10px; background: #e0e7ff; color: #2563eb;
  display: flex; align-items: center; justify-content: center; font-weight: 600; font-size: 18px; flex-shrink: 0;
}
.system-icon.active { background: #dcfce7; color: #10b981; }
.provider-icon { background: #f3f4f6; color: #666; }

.skill-info, .system-info, .api-key-header { flex: 1; min-width: 0; }
.skill-info h3, .system-info h3, .api-key-header h3 { margin: 0 0 4px; font-size: 15px; font-weight: 600; }
.skill-info p, .system-info p { margin: 0 0 8px; color: #666; font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.skill-meta { display: flex; gap: 8px; align-items: center; }
.badge { padding: 2px 8px; background: #e0e7ff; color: #2563eb; font-size: 11px; border-radius: 4px; }
.meta { color: #999; font-size: 12px; }

.status-badge { display: inline-block; padding: 2px 8px; font-size: 11px; border-radius: 4px; margin-left: 8px; }
.status-badge.active { background: #dcfce7; color: #10b981; }
.status-badge.inactive { background: #fee2e2; color: #ef4444; }

.system-url { color: #888; font-size: 12px; margin: 8px 0 0 !important; }

.skill-actions, .system-tools, .api-key-actions { display: flex; gap: 8px; align-items: flex-start; }

.tools-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; font-size: 13px; color: #666; }

.tools-list { margin-top: 8px; padding: 8px; background: #fafafa; border-radius: 6px; }
.tool-item { padding: 6px 0; border-bottom: 1px solid #eee; }
.tool-item:last-child { border-bottom: none; }
.tool-name { font-family: monospace; font-size: 12px; color: #2563eb; }
.tool-desc { display: block; font-size: 11px; color: #888; margin-top: 2px; }

/* API Key */
.api-key-card.configured { border: 1px solid #10b981; }
.api-key-details { margin: 12px 0; padding: 12px; background: #fafafa; border-radius: 6px; }
.detail-row { display: flex; justify-content: space-between; font-size: 13px; margin-bottom: 4px; }
.detail-row:last-child { margin-bottom: 0; }
.detail-row .label { color: #888; }
.detail-row .value { color: #333; }

/* Empty State */
.empty-state { text-align: center; padding: 48px 24px; }
.empty-icon { font-size: 48px; margin-bottom: 16px; }
.empty-state h3 { margin: 0 0 8px; color: #333; }
.empty-state p { margin: 0 0 24px; color: #888; }

/* Dialog */
.dialog-overlay {
  position: fixed; top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0,0,0,0.5); display: flex; align-items: center; justify-content: center;
  z-index: 1000; padding: 20px;
}

.dialog {
  background: #fff; border-radius: 12px; width: 100%; max-width: 420px;
  max-height: 95vh; display: flex; flex-direction: column; overflow: hidden;
}
.dialog.wide { max-width: 700px; }
.dialog.very-wide { max-width: 1000px; max-height: 85vh; overflow: hidden; }
.dialog.skill-form-dialog { max-width: 1100px; }

.dialog-header { display: flex; justify-content: space-between; align-items: center; padding: 16px 20px; border-bottom: 1px solid #eee; }
.dialog-header h3 { margin: 0; font-size: 16px; }
.btn-close { background: none; border: none; font-size: 20px; color: #999; cursor: pointer; }

.dialog-body { flex: 1; overflow-y: auto; padding: 20px; }
.dialog-footer { display: flex; justify-content: flex-end; gap: 12px; padding: 16px 20px; border-top: 1px solid #eee; }

.form-section { margin-bottom: 20px; }
.form-section h4 { margin: 0 0 12px; font-size: 14px; font-weight: 600; color: #333; }
.form-section .hint { font-weight: normal; color: #888; font-size: 12px; }
.form-section .optional { font-weight: normal; color: #888; font-size: 12px; }

.form-group { margin-bottom: 14px; }
.form-group label { display: block; margin-bottom: 6px; font-size: 13px; color: #555; }
.form-group input, .form-group select, .form-group textarea {
  width: 100%; padding: 10px 12px; border: 1px solid #ddd; border-radius: 6px;
  font-size: 14px; transition: border-color 0.2s; box-sizing: border-box;
}
.form-group input:focus, .form-group select:focus, .form-group textarea:focus { outline: none; border-color: #2563eb; }
.form-group textarea { resize: vertical; }

.required { color: #ef4444; }

.form-hint { font-size: 12px; color: #888; margin-bottom: 8px; }

.code-editor { font-family: 'Consolas', monospace; font-size: 13px; line-height: 1.6; background: #f8f9fa; min-height: 300px; height: 300px; resize: vertical; }

.file-groups { display: flex; flex-direction: column; gap: 16px; }
.file-group .file-label { display: block; font-size: 13px; color: #555; margin-bottom: 6px; }
.file-group input[type="file"] { display: none; }
.file-upload-wrapper { margin-bottom: 8px; }
.file-upload-btn {
  display: inline-block; padding: 8px 16px; background: #f5f5f5; border: 1px solid #ddd;
  border-radius: 6px; font-size: 13px; color: #555; cursor: pointer; transition: all 0.2s;
}
.file-upload-btn:hover { background: #e8e8e8; border-color: #ccc; }
.file-list { padding: 10px; border: 1px solid #eee; border-radius: 6px; min-height: 40px; display: flex; flex-wrap: wrap; gap: 6px; background: #fafafa; }
.no-file { color: #999; font-size: 12px; }
.file-tag { display: inline-flex; align-items: center; padding: 4px 8px; background: #e0e7ff; color: #2563eb; font-size: 12px; border-radius: 4px; }

/* Skill Form Dialog - 双栏布局 */
.skill-form-body { display: flex; gap: 24px; padding: 0 !important; }
.form-panel { flex: 1; padding: 24px; border-right: 1px solid #eee; overflow-y: auto; max-height: 80vh; }
.preview-panel { width: 280px; padding: 20px; background: #fafafa; max-height: 80vh; overflow-y: auto; }
.preview-panel h4 { margin: 0 0 12px; font-size: 13px; font-weight: 600; color: #666; }

.directory-tree { background: #fff; border-radius: 8px; padding: 12px; border: 1px solid #eee; }
.tree-item { display: flex; align-items: center; gap: 6px; padding: 6px 0; font-size: 13px; }
.tree-item .icon { font-size: 14px; }
.tree-item .name { color: #333; }
.tree-item .count { color: #888; font-size: 12px; }
.tree-item.folder { color: #333; }
.tree-item.file { color: #555; }
.tree-empty { text-align: center; color: #999; font-size: 12px; padding: 20px; }

.skill-guide { margin-top: 20px; }
.skill-guide h4 { margin: 0 0 12px; font-size: 13px; font-weight: 600; color: #666; }
.guide-items { display: flex; flex-direction: column; gap: 8px; }
.guide-item { display: flex; gap: 8px; font-size: 12px; }
.guide-title { color: #2563eb; font-family: monospace; }
.guide-desc { color: #888; }

.code-preview { background: #f8f9fa; padding: 16px; border-radius: 8px; font-size: 12px; line-height: 1.6; overflow-x: auto; white-space: pre-wrap; }

.skill-detail-info { padding: 12px; background: #fafafa; border-radius: 8px; display: flex; flex-wrap: wrap; gap: 16px; }
.skill-detail-info p { margin: 0; font-size: 13px; flex: 1 1 45%; }
.skill-detail-info code { background: #e0e7ff; padding: 2px 6px; border-radius: 4px; font-size: 11px; color: #2563eb; }

.skill-md-content { margin-top: 12px; flex: 1; overflow: hidden; display: flex; flex-direction: column; }
.skill-md-content h4 { margin: 0 0 8px; font-size: 13px; font-weight: 600; color: #333; }
.skill-md-content .code-preview { background: #f8f9fa; padding: 12px; border-radius: 8px; font-size: 11px; line-height: 1.5; overflow: auto; white-space: pre-wrap; flex: 1; max-height: 300px; }

.skill-files-section { margin-top: 12px; max-height: 200px; overflow-y: auto; }
.skill-files-section h4 { margin: 0 0 8px; font-size: 13px; font-weight: 600; color: #333; }
.file-tree { background: #f8f9fa; border-radius: 8px; padding: 10px; display: flex; flex-wrap: wrap; gap: 8px; }
.tree-folder { padding: 4px 8px; font-size: 12px; background: #fff; border-radius: 4px; border: 1px solid #e5e5e5; }
.tree-folder .folder-icon { margin-right: 4px; }
.tree-children { margin-left: 16px; padding: 4px 0; }
.tree-file { padding: 4px 0; font-size: 12px; color: #555; }
.tree-file .file-icon { margin-right: 4px; }
.skill-file-content { margin-top: 12px; flex: 1; overflow: hidden; display: flex; flex-direction: column; }
.skill-file-content h4 { margin: 0 0 8px; font-size: 13px; font-weight: 600; color: #333; }
.skill-file-content .code-preview { background: #f8f9fa; padding: 12px; border-radius: 8px; font-size: 11px; line-height: 1.5; overflow: auto; white-space: pre-wrap; flex: 1; max-height: 400px; }
.btn-link.small { padding: 2px 6px; font-size: 10px; }

.tools-detail-list { max-height: 400px; overflow-y: auto; }
.tool-detail-item { padding: 12px; border-bottom: 1px solid #eee; }
.tool-detail-item:last-child { border-bottom: none; }
.tool-detail-name { font-family: monospace; font-size: 13px; color: #2563eb; margin-bottom: 4px; }
.tool-detail-desc { font-size: 13px; color: #666; margin-bottom: 8px; }
.tool-detail-schema { font-size: 12px; }
.schema-label { color: #888; }
.schema-param { display: inline-block; background: #f0f0f0; padding: 2px 6px; border-radius: 4px; margin-right: 4px; color: #333; }
.schema-param .required { color: #ef4444; }

@media (max-width: 768px) {
  .settings-main { padding: 16px; }
  .skill-form-body { flex-direction: column; }
  .form-panel { border-right: none; border-bottom: 1px solid #eee; }
  .preview-panel { width: 100%; }
}
</style>