<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave, RouterLink, useRoute, useRouter } from 'vue-router'
import Icon from '@/components/Icon.vue'
import StatusPill from '@/components/StatusPill.vue'
import ErrorNotice from '@/components/ErrorNotice.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { articleApi, categoryApi } from '@/api'
import { streamAi } from '@/api/aiStream'
import { useAuth } from '@/stores/auth'
import { useToast } from '@/composables/toast'
import { countWords, formatDate } from '@/utils/format'

/**
 * 编辑器。路由 /console/articles/new 与 /console/articles/:id/edit 共用。
 *
 * 后端存的是纯文本，不承诺 markdown，所以这里不做任何格式解析 ——
 * 预览就是把同一段文字按阅读排版放出来，让作者看行宽和节奏。
 */

const route = useRoute()
const router = useRouter()
const auth = useAuth()
const toast = useToast()

const isNew = computed(() => route.name === 'article-new')
const id = computed(() => (isNew.value ? null : Number(route.params.id)))

const form = reactive({ title: '', content: '', status: 0, categoryId: '' })
const meta = ref(null)
const categories = ref([])

const loading = ref(true)
const error = ref(null)
const saving = ref(false)
const mode = ref('write')

/** 已落库的那份快照。用它判断有没有未保存的改动。 */
const saved = ref('')
const snapshot = () => JSON.stringify([form.title, form.content, form.status, form.categoryId])
const dirty = computed(() => !loading.value && !error.value && snapshot() !== saved.value)

const titleEl = ref(null)

async function load() {
  loading.value = true
  error.value = null
  try {
    const cats = await categoryApi.list().catch(() => [])
    categories.value = cats ?? []

    if (isNew.value) {
      form.title = ''
      form.content = ''
      form.status = 0
      form.categoryId = ''
      meta.value = null
    } else {
      const d = await articleApi.detail(id.value)
      form.title = d.title ?? ''
      form.content = d.content ?? ''
      form.status = d.status ?? 0
      form.categoryId = d.categoryId ? String(d.categoryId) : ''
      meta.value = d
    }
    saved.value = snapshot()
  } catch (e) {
    error.value = e
  } finally {
    loading.value = false
    await nextTick()
    autosizeTitle()
  }
}

onMounted(load)

function autosizeTitle() {
  const el = titleEl.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = `${el.scrollHeight}px`
}

/**
 * 存盘。status 必须显式传：后端 add 在 status 为 null 时默认 1（已发布），
 * 不传会把草稿直接发出去。
 */
async function save(status) {
  if (!form.title.trim()) {
    toast.error('标题不能为空')
    mode.value = 'write'
    titleEl.value?.focus()
    return
  }
  if (saving.value) return
  saving.value = true
  try {
    const payload = {
      title: form.title.trim(),
      content: form.content,
      status,
      // 0 是后端约定的「清空分类」哨兵值；null 才是「本次不改」
      categoryId: form.categoryId === '' ? 0 : Number(form.categoryId),
    }

    if (isNew.value) {
      const newId = await articleApi.add(payload)
      saved.value = snapshot()
      toast.ok(status === 1 ? '已发布' : '草稿已保存')
      // 发布后直接回列表；存草稿则留在编辑器继续写
      if (status === 1) {
        router.push({ name: 'my-articles' })
        return
      }
      await router.replace({ name: 'article-edit', params: { id: newId } })
      await load()
    } else {
      // 后端回传「本次是否真的有字段被改动」。前端会把整份表单原样发回去，
      // 所以「字都没改就点保存」是完全可能的 —— 那种情况下后端不写库、
      // 也不会顶掉更新时间，这里就别谎报「已更新」。
      const changed = await articleApi.update({ id: id.value, ...payload })

      form.status = status
      // 无论有没有改动，当前表单内容都已和库里一致（改了就已落库，
      // 没改则本来就一致），所以快照都要同步，避免留下假的「未保存改动」
      saved.value = snapshot()

      if (!changed) {
        toast.ok('内容没有变化，已跳过更新')
        // 没写库，更新时间不会变，不需要再去拉一次详情
        return
      }

      toast.ok(status === 1 ? '已更新' : '已存为草稿')
      if (status === 1) {
        router.push({ name: 'my-articles' })
        return
      }
      meta.value = { ...(meta.value || {}), ...payload, status, updateTime: null }
      // 更新时间由后端写，重新拉一次拿准确时间
      articleApi
        .detail(id.value)
        .then((d) => {
          meta.value = d
        })
        .catch(() => {})
    }
  } catch (e) {
    toast.error(e.message)
  } finally {
    saving.value = false
  }
}

/**
 * 取消：不保存直接退回「我的文章」。有未存改动时由 onBeforeRouteLeave 统一拦一次，
 * 这里不重复写确认逻辑。
 */
function cancel() {
  router.push({ name: 'my-articles' })
}

const confirm = reactive({ open: false, busy: false })

async function doDelete() {
  confirm.busy = true
  try {
    await articleApi.remove(id.value)
    saved.value = snapshot()
    toast.ok('已删除')
    router.replace({ name: 'my-articles' })
  } catch (e) {
    toast.error(e.message)
  } finally {
    confirm.busy = false
  }
}

/* Ctrl/Cmd + S 存盘：沿用当前状态，不偷偷改变发布状态 */
function onKey(e) {
  if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 's') {
    e.preventDefault()
    if (!loading.value && !error.value) save(form.status)
  }
}
onMounted(() => document.addEventListener('keydown', onKey))
onUnmounted(() => document.removeEventListener('keydown', onKey))

/* 有未存改动时拦一下路由跳转，不用原生 confirm 会静默丢稿 */
onBeforeRouteLeave(() => {
  if (!dirty.value) return true
  return window.confirm('这一篇还有没保存的改动，确定离开吗？')
})

const chars = computed(() => countWords(form.content))

/* ---------------- AI 写作助手 ---------------- */

const AI_ACTIONS = [
  { key: 'CONTINUE', label: '续写', ph: '想往哪个方向写？例如：补一段踩坑经历（可留空）' },
  { key: 'POLISH', label: '润色', ph: '有什么偏好吗？例如：更口语一些（可留空）' },
  { key: 'REWRITE', label: '改写', ph: '想改成什么样？例如：更正式（可留空）' },
  { key: 'TITLE', label: '起标题', ph: '有什么偏好吗？例如：带点悬念（可留空）' },
  { key: 'OUTLINE', label: '大纲', ph: '有什么要求？例如：分三部分展开（可留空）' },
  { key: 'TRANSLATE', label: '翻译', ph: '译成什么语言？例如：日语（可留空）' },
  { key: 'CUSTOM', label: '自定义', ph: '想怎么改？例如：把这段写得更口语一些（可留空）' },
]

const contentEl = ref(null)

const ai = reactive({
  open: false,
  action: '',
  instruction: '',
  running: false,
  /** 流式期间累积的预览文本。绝不能写进 form.content —— v-model 会让每个 token 重置光标。 */
  text: '',
  error: '',
})

/** 正文里选中的区间。润色 / 改写 / 翻译只处理它，为空则针对全文。 */
const sel = reactive({ start: 0, end: 0, text: '' })

function snapSel() {
  const el = contentEl.value
  if (!el) return
  sel.start = el.selectionStart ?? 0
  sel.end = el.selectionEnd ?? 0
  sel.text = (form.content || '').slice(sel.start, sel.end)
}

/** 回填时用 done 事件给的权威文本；流式期间它只是预览。 */
const aiReady = computed(() => !!ai.text && !ai.running)

/** 本次生成最终可回填的文本 */
let aiFinal = ''
/** 流式累积缓冲，由 rAF 合并后一次性写入 ai.text */
let aiPending = ''
let aiRaf = 0
let aiController = null

function pushDelta(piece) {
  aiPending += piece
  // 模型每秒能吐几十个片段，逐个渲染会抖；合并到一帧
  if (!aiRaf) aiRaf = requestAnimationFrame(commitAiText)
}

function commitAiText() {
  aiRaf = 0
  ai.text = aiPending
}

const categoryName = computed(() => {
  const hit = categories.value.find((c) => String(c.id) === String(form.categoryId))
  return hit ? hit.name : ''
})

const aiPlaceholder = computed(() => AI_ACTIONS.find((a) => a.key === ai.action)?.ph || '')

/**
 * 点功能按钮只是选中它并把「要求」输入框亮出来，不会立刻生成 ——
 * 作者可以补一句要求，也可以什么都不填，再点「生成」才真正发起。
 */
function selectAi(action) {
  if (ai.running) return
  ai.open = true
  if (action === ai.action) return
  ai.action = action
  ai.error = ''
  // 上一次的产出属于上一个动作，先清掉，免得作者误插错东西
  ai.text = ''
  aiPending = ''
  aiFinal = ''
}

async function runAi(action) {
  if (ai.running || !action) return

  ai.open = true
  ai.action = action
  ai.error = ''

  if (action !== 'CUSTOM' && !(form.content || '').trim()) {
    ai.error = '正文还是空的，先写点什么再让 AI 帮忙。'
    return
  }

  ai.running = true
  ai.text = ''
  aiPending = ''
  aiFinal = ''

  aiController = new AbortController()
  try {
    await streamAi(
      '/ai/chat',
      {
        action,
        instruction: ai.instruction.trim(),
        context: {
          title: form.title,
          content: form.content,
          selection: sel.text,
          selStart: sel.start,
          selEnd: sel.end,
          categoryName: categoryName.value,
        },
      },
      {
        signal: aiController.signal,
        onEvent(name, payload) {
          if (name === 'delta') {
            pushDelta(payload.v || '')
          } else if (name === 'done') {
            aiFinal = payload.text || aiPending
            aiPending = aiFinal
            if (aiRaf) cancelAnimationFrame(aiRaf)
            aiRaf = 0
            ai.text = aiFinal
          } else if (name === 'error') {
            ai.error = payload.msg || '生成失败，请重试'
          }
        },
      },
    )
  } catch (e) {
    if (e?.name === 'AbortError') {
      // 作者点了停止：已经生成的部分留下来，仍然可以插入
      if (aiRaf) cancelAnimationFrame(aiRaf)
      aiRaf = 0
      aiFinal = aiPending
      ai.text = aiPending
      if (!ai.text) ai.open = false
    } else {
      ai.error = e?.message || '生成失败，请重试'
    }
  } finally {
    aiController = null
    ai.running = false
  }
}

function stopAi() {
  aiController?.abort()
}

/** 插到光标处，不删任何东西 */
async function insertAi() {
  if (!aiFinal) return
  const c = form.content || ''
  const el = contentEl.value
  const at = el ? (el.selectionStart ?? c.length) : c.length
  form.content = c.slice(0, at) + aiFinal + c.slice(at)
  await nextTick()
  restoreCaret(at + aiFinal.length)
}

/** 用生成结果顶掉选中的那一段 */
async function replaceAiSelection() {
  if (!aiFinal || !sel.text) return
  const c = form.content || ''
  form.content = c.slice(0, sel.start) + aiFinal + c.slice(sel.end)
  await nextTick()
  restoreCaret(sel.start + aiFinal.length)
}

async function useAiAsTitle() {
  const line = (aiFinal || '').split('\n')[0].trim()
  if (!line) return
  form.title = line
  await nextTick()
  autosizeTitle()
}

async function restoreCaret(pos) {
  const el = contentEl.value
  if (!el) return
  el.focus()
  el.setSelectionRange(pos, pos)
  snapSel()
}

onUnmounted(() => {
  aiController?.abort()
  if (aiRaf) cancelAnimationFrame(aiRaf)
})
</script>

<template>
  <div class="page">
    <div class="page__inner">
      <div class="page__head">
        <div class="page__head-main">
          <h1 class="page__title">{{ isNew ? '新建文章' : '编辑文章' }}</h1>
          <p class="page__desc">
            <template v-if="meta">
              {{ meta.author }} · 创建于 {{ formatDate(meta.createTime) }}
              <template v-if="meta.updateTime"> · 最后改动 {{ meta.updateTime }}</template>
            </template>
            <template v-else>作者取自登录身份，不用手填。</template>
          </p>
        </div>
        <div class="page__actions">
          <button class="btn btn--ghost btn--sm" type="button" @click="cancel">
            <Icon name="arrowLeft" :size="13" />
            取消
          </button>
          <button
            v-if="!isNew"
            class="btn btn--danger btn--sm"
            type="button"
            @click="confirm.open = true"
          >
            <Icon name="trash" :size="13" />
            删除
          </button>
          <button
            class="btn btn--default btn--sm"
            type="button"
            :disabled="saving || loading || !!error"
            @click="save(0)"
          >
            {{ form.status === 1 ? '转为草稿' : '存草稿' }}
          </button>
          <button
            class="btn btn--primary btn--sm"
            type="button"
            :disabled="saving || loading || !!error"
            @click="save(1)"
          >
            <Icon name="check" :size="14" />
            {{ saving ? '保存中…' : form.status === 1 ? '保存修改' : '发布' }}
          </button>
        </div>
      </div>

      <ErrorNotice v-if="error" :error="error" @retry="load" />

      <div v-else-if="loading" class="panel">
        <div class="panel__body stack gap-5">
          <div class="skeleton" style="height: 26px; width: 46%" />
          <div class="skeleton" style="height: 15px" />
          <div class="skeleton" style="height: 15px; width: 88%" />
          <div class="skeleton" style="height: 15px; width: 72%" />
          <div class="skeleton" style="height: 15px; width: 90%" />
        </div>
      </div>

      <div v-else class="ed">
        <!-- ---------- 左：正文 ---------- -->
        <section class="panel">
          <div class="ed__title">
            <textarea
              ref="titleEl"
              v-model="form.title"
              class="ed__title-input"
              rows="1"
              placeholder="标题"
              aria-label="标题"
              @input="autosizeTitle"
            />
          </div>

          <div class="panel__head">
            <div class="seg" role="tablist">
              <button
                class="seg__b"
                :class="{ 'is-on': mode === 'write' }"
                type="button"
                role="tab"
                :aria-selected="mode === 'write'"
                @click="mode = 'write'"
              >
                写作
              </button>
              <button
                class="seg__b"
                :class="{ 'is-on': mode === 'preview' }"
                type="button"
                role="tab"
                :aria-selected="mode === 'preview'"
                @click="mode = 'preview'"
              >
                预览
              </button>
            </div>
            <button
              class="chip"
              :class="{ 'is-on': ai.open }"
              type="button"
              @click="ai.open = !ai.open"
            >
              <Icon name="spark" :size="13" />
              AI 写作
            </button>
            <span class="grow" />
            <span class="panel__sub num">{{ chars }} 字</span>
          </div>

          <div class="panel__body panel__body--flush">
            <textarea
              v-if="mode === 'write'"
              ref="contentEl"
              v-model="form.content"
              class="ed__body"
              placeholder="正文。换行会原样保留。"
              aria-label="正文"
              @select="snapSel"
              @keyup="snapSel"
              @mouseup="snapSel"
              @blur="snapSel"
            />
            <div v-else class="ed__preview">
              <div v-if="form.content" class="prose">{{ form.content }}</div>
              <p v-else class="c-3">还没有正文。</p>
            </div>

            <!-- AI 内联条：放在 textarea 之后而不是做成第三个 tab，
                 否则切换 tab 会卸载 textarea，选中区间就丢了 -->
            <section v-if="ai.open" class="ed__ai">
              <div class="ed__ai-head">
                <Icon name="spark" :size="14" />
                <strong class="ed__ai-title">AI 写作</strong>
                <span v-if="sel.text" class="panel__sub">已选中 {{ sel.text.length }} 字</span>
                <span v-else class="panel__sub">针对全文</span>
                <span class="grow" />
                <button
                  v-if="ai.running"
                  class="btn btn--ghost btn--sm"
                  type="button"
                  @click="stopAi"
                >
                  <Icon name="x" :size="12" />
                  停止
                </button>
                <button class="btn btn--ghost btn--sm" type="button" @click="ai.open = false">
                  收起
                </button>
              </div>

              <div class="ed__ai-actions">
                <button
                  v-for="a in AI_ACTIONS"
                  :key="a.key"
                  class="chip"
                  :class="{ 'is-on': ai.action === a.key }"
                  type="button"
                  :disabled="ai.running"
                  @click="selectAi(a.key)"
                >
                  {{ a.label }}
                </button>
              </div>

              <div v-if="ai.action" class="ed__ai-prompt">
                <input
                  v-model="ai.instruction"
                  class="input"
                  type="text"
                  :placeholder="aiPlaceholder"
                  aria-label="生成要求"
                  :disabled="ai.running"
                  @keyup.enter="runAi(ai.action)"
                />
                <button
                  class="btn btn--primary btn--sm"
                  type="button"
                  :disabled="ai.running"
                  @click="runAi(ai.action)"
                >
                  生成
                </button>
              </div>

              <div v-if="ai.error" class="banner banner--error">
                <Icon name="alert" :size="14" />
                <span>{{ ai.error }}</span>
                <button
                  v-if="ai.action"
                  class="btn btn--ghost btn--sm banner__action"
                  type="button"
                  @click="runAi(ai.action)"
                >
                  <Icon name="refresh" :size="12" />
                  重试
                </button>
              </div>

              <div v-if="ai.text" class="ed__ai-out prose">{{ ai.text }}</div>
              <p v-else-if="ai.running" class="ed__ai-wait">
                <i class="ed__ai-dot" />
                正在生成…
              </p>

              <div v-if="aiReady" class="ed__ai-foot">
                <button class="btn btn--default btn--sm" type="button" @click="insertAi">
                  <Icon name="plus" :size="13" />
                  插入到光标处
                </button>
                <button
                  class="btn btn--default btn--sm"
                  type="button"
                  :disabled="!sel.text"
                  @click="replaceAiSelection"
                >
                  <Icon name="pen" :size="13" />
                  替换选中
                </button>
                <button class="btn btn--ghost btn--sm" type="button" @click="useAiAsTitle">
                  用作标题
                </button>
              </div>
            </section>
          </div>
        </section>

        <!-- ---------- 右：属性 ---------- -->
        <aside class="ed__side stack gap-4">
          <section class="panel">
            <div class="panel__head">
              <h2 class="panel__title">状态</h2>
              <span class="grow" />
              <StatusPill :status="form.status" />
            </div>
            <div class="panel__body">
              <p class="t-xs c-3">
                {{ form.status === 1 ? '读者能在博客上看到这一篇。' : '只有你自己看得到，读者看不到。' }}
              </p>
              <p v-if="dirty" class="dirty">
                <i class="dirty__dot" />
                有未保存的改动
              </p>
            </div>
          </section>

          <section class="panel">
            <div class="panel__head">
              <h2 class="panel__title">分类</h2>
            </div>
            <div class="panel__body">
              <select v-model="form.categoryId" class="select" aria-label="分类">
                <option value="">未归类</option>
                <option v-for="c in categories" :key="c.id" :value="String(c.id)">{{ c.name }}</option>
              </select>
              <p class="t-xs c-3" style="margin-top: 8px">
                不选也能发。分类要在
                <RouterLink class="link" :to="{ name: 'categories' }">分类页</RouterLink>
                里先建。
              </p>
            </div>
          </section>

          <section v-if="meta" class="panel">
            <div class="panel__head">
              <h2 class="panel__title">记录</h2>
            </div>
            <div class="panel__body">
              <dl class="def-list">
                <dt>作者</dt>
                <dd>{{ meta.author || auth.displayName }}</dd>
                <dt>创建</dt>
                <dd class="num">{{ meta.createTime || '—' }}</dd>
                <dt>改动</dt>
                <dd class="num">{{ meta.updateTime || '—' }}</dd>
                <dt>文章 ID</dt>
                <dd class="t-mono">{{ meta.id }}</dd>
              </dl>
            </div>
          </section>
        </aside>
      </div>

      <ConfirmDialog
        :open="confirm.open"
        :title="`删除「${form.title || '未命名草稿'}」？`"
        body="删除后无法恢复。"
        confirm-text="删除"
        danger
        :busy="confirm.busy"
        @confirm="doDelete"
        @cancel="confirm.open = false"
      />
    </div>
  </div>
</template>

<style scoped>
.ed {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 296px;
  gap: 16px;
  align-items: start;
}

@media (max-width: 1000px) {
  .ed {
    grid-template-columns: minmax(0, 1fr);
  }
}

.ed__title {
  padding: 16px 20px 14px;
  border-bottom: 1px solid var(--line);
}

.ed__title-input {
  display: block;
  width: 100%;
  font-size: 24px;
  font-weight: 600;
  letter-spacing: -0.025em;
  line-height: 1.35;
  resize: none;
  overflow: hidden;
  min-height: 34px;
}

.ed__title-input::placeholder {
  color: var(--text-4);
  font-weight: 500;
}

.ed__body {
  display: block;
  width: 100%;
  min-height: 440px;
  padding: 18px 20px 24px;
  font-size: 17px;
  line-height: 1.9;
  resize: vertical;
}

.ed__body::placeholder {
  color: var(--text-4);
}

.ed__preview {
  padding: 20px;
  min-height: 440px;
}

/* AI 内联条 */
.ed__ai {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 20px 16px;
  border-top: 1px solid var(--line);
  background: var(--surface-2);
}

.ed__ai-head {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-3);
}

.ed__ai-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text);
}

.ed__ai-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.ed__ai-actions .chip:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.ed__ai-prompt {
  display: flex;
  gap: 8px;
}

.ed__ai-out {
  max-height: 320px;
  overflow: auto;
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-radius: var(--r-sm);
  background: var(--surface);
  font-size: 15px;
  line-height: 1.8;
  color: var(--text-2);
}

.ed__ai-wait {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--text-3);
}

.ed__ai-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--accent);
  flex: none;
  animation: ed-pulse 1.1s var(--ease) infinite;
}

@keyframes ed-pulse {
  0%,
  100% {
    opacity: 0.25;
  }
  50% {
    opacity: 1;
  }
}

.ed__ai-foot {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.ed__side {
  position: sticky;
  top: calc(var(--nav-h) + 20px);
}

@media (max-width: 1000px) {
  .ed__side {
    position: static;
  }
}

.link {
  color: var(--accent);
}

.link:hover {
  text-decoration: underline;
  text-underline-offset: 3px;
}

.dirty {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--line-2);
  font-size: 13px;
  color: var(--accent-ink);
}

.dirty__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--accent);
  flex: none;
}

/* 分段切换：写作 / 预览 */
.seg {
  display: inline-flex;
  padding: 2px;
  gap: 2px;
  border-radius: var(--r-sm);
  background: var(--surface-2);
  border: 1px solid var(--line);
}

.seg__b {
  height: 24px;
  padding: 0 11px;
  border-radius: var(--r-xs);
  font-size: 13px;
  font-weight: 500;
  color: var(--text-3);
  transition:
    background-color var(--t-fast) var(--ease),
    color var(--t-fast) var(--ease);
}

.seg__b:hover {
  color: var(--text);
}

.seg__b.is-on {
  background: var(--surface);
  color: var(--text);
  box-shadow: var(--shadow-1);
}
</style>