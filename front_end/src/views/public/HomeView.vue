<script setup>
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import Icon from '@/components/Icon.vue'
import Pager from '@/components/Pager.vue'
import EmptyState from '@/components/EmptyState.vue'
import ErrorNotice from '@/components/ErrorNotice.vue'
import { articleApi, categoryApi } from '@/api'
import { formatDate } from '@/utils/format'

/**
 * 公开首页。
 *
 * 后端公开列表只返回 id / title / author / create_time / update_time / status，
 * 既没有 content 也没有 category_id —— 所以做不了摘要；分类筛选靠 /category/public
 * 拿到分类 id，再让公开列表按 categoryId 过滤。
 * 版式是「标题主导的索引」：每篇一张轻玻璃卡片，靠排版节奏保持可扫读。
 */

const route = useRoute()
const router = useRouter()

const page = reactive({ pageNum: 1, pageSize: 5, total: 0, totalPages: 0 })
const rows = ref([])
const loading = ref(true)
const pending = ref(false)
const error = ref(null)
const keyword = ref('')

/* 侧栏：全部文章 / 文章分类 两个标签页。分类走公开接口，游客也能按分类浏览 */
const tab = ref('all') // 'all' | 'cat'
const categories = ref([])
const categoryId = ref(null)

const hasKeyword = computed(() => Boolean(keyword.value.trim()))

/** 侧栏状态（在哪个标签页 + 选了哪个分类）要能跟着人走：
    统一由这里生成地址栏 query，同时也带给文章详情页当返回目标。 */
function buildQuery() {
  const q = {}
  if (tab.value === 'cat') q.tab = 'cat'
  if (categoryId.value != null) q.category = String(categoryId.value)
  return q
}

/** 带着侧栏状态进文章详情，详情页的「返回」才能指回这里 */
const listQuery = computed(buildQuery)

const activeCategory = computed(
  () => categories.value.find((c) => c.id === categoryId.value) || null,
)

const countLabel = computed(() => {
  if (hasKeyword.value) return `找到 ${page.total} 篇`
  if (activeCategory.value) return `${activeCategory.value.name} · ${page.total} 篇`
  return `共 ${page.total} 篇`
})

const emptyIcon = computed(() =>
  hasKeyword.value ? 'search' : activeCategory.value ? 'folder' : 'book',
)

const emptyTitle = computed(() => {
  if (hasKeyword.value) return '没有找到相关的文章'
  if (activeCategory.value) return `「${activeCategory.value.name}」下还没有文章`
  return '还没有发布过文章'
})

const emptyDesc = computed(() => {
  if (hasKeyword.value) return '换个词试试，或者把搜索清掉。'
  if (activeCategory.value) return '换个分类看看，或者回到全部文章。'
  return '这里空着，说明还没有人按下发布。'
})

/**
 * silent = true 用于下钻（切分类、翻页、搜索）：保留当前列表并整体压暗，
 * 而不是换成骨架屏 —— 否则本机响应太快，会闪出「列表→骨架→列表」的两帧抖动。
 * 骨架屏只在首次加载和出错重试时出现。
 */
async function load({ silent = false } = {}) {
  if (silent) pending.value = true
  else loading.value = true
  error.value = null
  try {
    const params = {
      pageNum: page.pageNum,
      pageSize: page.pageSize,
      keyword: keyword.value.trim(),
    }
    if (categoryId.value != null) params.categoryId = categoryId.value
    const data = await articleApi.publicPage(params)
    rows.value = data?.records ?? []
    page.total = data?.total ?? 0
    page.totalPages = data?.totalPages ?? 0
    page.pageNum = data?.pageNum ?? page.pageNum
  } catch (e) {
    error.value = e
    rows.value = []
    page.total = 0
    page.totalPages = 0
  } finally {
    loading.value = false
    pending.value = false
  }
}

async function loadCategories() {
  try {
    categories.value = (await categoryApi.publicList()) ?? []
  } catch {
    // 分类拿不到就不显示可选列表，不该拖垮文章列表
    categories.value = []
  }
}

onMounted(() => {
  // 从文章详情返回时地址栏里带着 tab/category，恢复成当时点进去的样子
  const fromQuery = Number(route.query.category)
  if (Number.isFinite(fromQuery) && fromQuery > 0) categoryId.value = fromQuery
  if (route.query.tab === 'cat' || categoryId.value != null) tab.value = 'cat'
  load()
  loadCategories()
})

let timer = null
watch(keyword, () => {
  clearTimeout(timer)
  timer = setTimeout(() => {
    page.pageNum = 1
    load({ silent: true })
  }, 320)
})
onUnmounted(() => clearTimeout(timer))

// 地址栏里的侧栏状态变了（浏览器前进/后退）也跟着切
watch(
  () => route.query,
  (q) => {
    const parsed = Number(q.category)
    const id = Number.isFinite(parsed) && parsed > 0 ? parsed : null
    const nextTab = q.tab === 'cat' || id != null ? 'cat' : 'all'
    if (nextTab === tab.value && id === categoryId.value) return
    tab.value = nextTab
    categoryId.value = id
    page.pageNum = 1
    load({ silent: true })
  },
)

function goPage(n) {
  if (n < 1 || (page.totalPages && n > page.totalPages)) return
  page.pageNum = n
  load({ silent: true })
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function clearKeyword() {
  keyword.value = ''
}

/* ---------------- 侧栏交互 ---------------- */

function setTab(next) {
  if (tab.value === next) {
    // 已经在「文章分类」里且选了具体分类时，再点一次这个标签就取消选择、回到全部文章
    if (next === 'cat' && categoryId.value != null) {
      categoryId.value = null
      syncQuery()
      reloadFromTop()
    }
    return
  }
  const hadFilter = categoryId.value != null
  tab.value = next
  // 切回「全部文章」就把分类筛选清掉
  if (next === 'all') categoryId.value = null
  syncQuery()
  // 只有筛选条件真的变了才重新拉列表（纯换标签页不用重新请求）
  if (hadFilter !== (categoryId.value != null)) reloadFromTop()
}

/** 把侧栏状态写进地址栏（用 replace，不留一串历史垃圾），
    这样从文章详情退出时能带着它回到这里。 */
function syncQuery() {
  const next = buildQuery()
  if (next.tab === route.query.tab && next.category === route.query.category) return
  router.replace({ query: next })
}

function selectAll() {
  if (categoryId.value == null) return
  categoryId.value = null
  syncQuery()
  reloadFromTop()
}

function pickCategory(id) {
  if (categoryId.value === id) return
  categoryId.value = id
  syncQuery()
  reloadFromTop()
}

function reloadFromTop() {
  page.pageNum = 1
  load({ silent: true })
  window.scrollTo({ top: 0, behavior: 'smooth' })
}
</script>

<template>
  <div class="pubb">
    <div class="pubb__inner">
      <header class="mast">
        <h1 class="mast__title">BlogHub</h1>
        <p class="mast__desc">每一篇都是作者亲手写的。按时间排，不按热度。</p>
      </header>

      <div class="pubb__cols">
        <!-- ---------- 侧栏：全部文章 / 文章分类 ---------- -->
        <aside class="side">
          <div class="side__tabs">
            <button
              class="side__tab"
              :class="{ 'is-on': tab === 'all' }"
              type="button"
              @click="setTab('all')"
            >
              全部文章
            </button>
            <button
              class="side__tab"
              :class="{ 'is-on': tab === 'cat' }"
              type="button"
              @click="setTab('cat')"
            >
              文章分类
            </button>
          </div>

          <div v-if="tab === 'all'" class="side__body">
            <p class="side__hint">按发布时间排列，最新在前。</p>
          </div>

          <div v-else class="side__body">
            <ul v-if="categories.length" class="side__list">
              <li v-for="c in categories" :key="c.id">
                <button
                  class="side__item"
                  :class="{ 'is-on': categoryId === c.id }"
                  type="button"
                  @click="pickCategory(c.id)"
                >
                  {{ c.name }}
                </button>
              </li>
            </ul>
            <p v-else class="side__hint">还没有分类。</p>
          </div>
        </aside>

        <!-- ---------- 正文：文章索引 ---------- -->
        <div class="pubb__main">
          <div class="idx-head">
            <label class="search idx-search">
              <Icon name="search" :size="14" />
              <input v-model="keyword" class="input" placeholder="搜标题或正文…" />
            </label>
            <span class="grow" />
            <span v-if="!loading && !error" class="idx-count">{{ countLabel }}</span>
          </div>

          <ErrorNotice v-if="error" :error="error" @retry="load()" />

          <div v-else-if="loading" class="stack gap-5" style="padding: 20px 0">
            <div v-for="i in 6" :key="i" class="idx-skel">
              <div class="skeleton" style="width: 74px" />
              <div class="skeleton" style="height: 17px; width: 62%" />
            </div>
          </div>

          <EmptyState
            v-else-if="!rows.length"
            :icon="emptyIcon"
            :title="emptyTitle"
            :desc="emptyDesc"
          >
            <template #action>
              <button
                v-if="hasKeyword"
                class="btn btn--default"
                type="button"
                @click="clearKeyword"
              >
                清空搜索
              </button>
              <button
                v-else-if="activeCategory"
                class="btn btn--default"
                type="button"
                @click="selectAll"
              >
                回到全部文章
              </button>
              <RouterLink v-else class="btn btn--primary" :to="{ name: 'login' }">
                去写第一篇
              </RouterLink>
            </template>
          </EmptyState>

          <template v-else>
            <ol class="idx" :class="{ 'is-pending': pending }" :aria-busy="pending">
              <li v-for="a in rows" :key="a.id" class="idx__row">
                <RouterLink
                  class="idx__link"
                  :to="{ name: 'article', params: { id: a.id }, query: listQuery }"
                >
                  <time class="idx__date" :datetime="a.createTime">
                    {{ formatDate(a.createTime) }}
                  </time>
                  <span class="idx__body">
                    <span class="idx__title">{{ a.title || '无标题' }}</span>
                    <span class="idx__meta">{{ a.author }}</span>
                  </span>
                  <Icon class="idx__arrow" name="arrowUpRight" :size="15" />
                </RouterLink>
              </li>
            </ol>

            <div class="idx-foot" :class="{ 'is-pending': pending }">
              <Pager
                :page="page.pageNum"
                :page-size="page.pageSize"
                :total="page.total"
                :total-pages="page.totalPages"
                @go="goPage"
              />
            </div>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.pubb {
  padding: 0 24px 72px;
}

.pubb__inner {
  /* 跟顶部导航/页脚同宽（--content-max），内容区不再比外壳窄一截 */
  max-width: var(--content-max);
  margin: 0 auto;
}

/* 侧栏 + 正文两栏。侧栏只做导航，不抢版面的视觉重量 */
.pubb__cols {
  display: grid;
  grid-template-columns: 208px minmax(0, 1fr);
  gap: 32px;
  align-items: start;
}

.side {
  position: sticky;
  top: 88px;
  padding-top: 20px;
}

.side__tabs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 3px;
  padding: 3px;
  border: 1px solid var(--line);
  border-radius: var(--r);
  background: var(--surface-2);
}

.side__tab {
  padding: 7px 4px;
  border-radius: var(--r-sm);
  font-size: 13px;
  color: var(--text-2);
  transition:
    background-color var(--t-fast) var(--ease),
    color var(--t-fast) var(--ease);
}

.side__tab:hover {
  color: var(--text);
}

.side__tab.is-on {
  background: var(--surface);
  color: var(--text);
  font-weight: 500;
  box-shadow: var(--shadow-1);
}

.side__body {
  margin-top: 14px;
}

.side__list {
  display: grid;
  gap: 1px;
}

.side__item {
  display: block;
  width: 100%;
  padding: 8px 12px;
  border-radius: var(--r-sm);
  text-align: left;
  font-size: 14px;
  color: var(--text-2);
  transition:
    background-color var(--t-fast) var(--ease),
    color var(--t-fast) var(--ease);
}

.side__item:hover {
  background: var(--surface-2);
  color: var(--text);
}

.side__item.is-on {
  background: var(--accent-soft);
  color: var(--accent-ink);
  font-weight: 500;
}

.side__hint {
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-3);
}

/* 报头：这块是这个站唯一允许出现大字的地方，仍需克制 */
.mast {
  padding: 56px 0 30px;
  border-bottom: 1px solid var(--line);
}

.mast__title {
  font-size: clamp(30px, 5vw, 42px);
  font-weight: 600;
  letter-spacing: -0.04em;
  line-height: 1.05;
}

.mast__desc {
  margin-top: 12px;
  font-size: 17px;
  color: var(--text-2);
  max-width: 46ch;
}

.idx-head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px 0 12px;
}

.idx-search {
  width: min(320px, 100%);
}

.idx-count {
  font-size: 13px;
  color: var(--text-3);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.idx {
  display: grid;
  gap: 10px;
  padding: 2px 0 4px;
}

.idx__row {
  min-width: 0;
}

.idx__link {
  position: relative;
  isolation: isolate;
  overflow: hidden;
  display: grid;
  grid-template-columns: 104px minmax(0, 1fr) 20px;
  align-items: baseline;
  gap: 18px;
  padding: 14px 16px 14px 18px;
  border: 1px solid rgba(255, 255, 255, 0.76);
  border-radius: var(--r-lg);
  background-color: rgba(253, 252, 249, 0.62);
  background-image: linear-gradient(
    118deg,
    rgba(255, 255, 255, 0.34),
    rgba(255, 255, 255, 0) 34%,
    rgba(255, 255, 255, 0.16) 72%,
    rgba(255, 255, 255, 0)
  );
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.94),
    inset 0 -1px 0 rgba(28, 25, 18, 0.04),
    0 1px 1px rgba(28, 25, 18, 0.035),
    0 10px 24px -18px rgba(28, 25, 18, 0.28);
  -webkit-backdrop-filter: blur(14px) saturate(145%);
  backdrop-filter: blur(14px) saturate(145%);
  transition:
    transform var(--t) var(--ease),
    border-color var(--t) var(--ease),
    background-color var(--t) var(--ease),
    box-shadow var(--t) var(--ease);
}

.idx__link::before {
  content: '';
  position: absolute;
  inset: 0;
  z-index: 0;
  border-radius: inherit;
  background:
    radial-gradient(
      130% 160% at -8% -42%,
      rgba(255, 255, 255, 0.86),
      rgba(255, 255, 255, 0) 48%
    ),
    linear-gradient(
      100deg,
      rgba(255, 255, 255, 0.18),
      rgba(255, 255, 255, 0) 38% 68%,
      rgba(255, 255, 255, 0.12)
    );
  opacity: 0.72;
  pointer-events: none;
}

.idx__link > * {
  position: relative;
  z-index: 1;
}

@media (hover: hover) {
  .idx__link:hover {
    transform: translateY(-1px);
    border-color: rgba(255, 255, 255, 0.94);
    background-color: rgba(255, 253, 248, 0.78);
    box-shadow:
      inset 0 1px 0 rgba(255, 255, 255, 1),
      inset 0 -1px 0 rgba(28, 25, 18, 0.045),
      0 3px 8px rgba(28, 25, 18, 0.05),
      0 16px 32px -22px rgba(28, 25, 18, 0.34);
  }
}

.idx__date {
  font-family: var(--font-mono);
  font-size: 13px;
  color: var(--text-3);
  font-variant-numeric: tabular-nums;
}

.idx__body {
  min-width: 0;
}

.idx__title {
  display: block;
  font-size: 19px;
  font-weight: 500;
  letter-spacing: -0.02em;
  color: var(--text);
  line-height: 1.45;
}

.idx__link:hover .idx__title {
  color: var(--accent);
}

.idx__meta {
  display: block;
  margin-top: 5px;
  font-size: 13px;
  color: var(--text-3);
}

.idx__arrow {
  align-self: center;
  color: var(--text-4);
  opacity: 0;
  transition: opacity var(--t-fast) var(--ease);
}

.idx__link:hover .idx__arrow {
  opacity: 1;
  color: var(--accent);
}

.idx-skel {
  display: grid;
  grid-template-columns: 104px minmax(0, 1fr);
  align-items: center;
  gap: 16px;
  min-height: 78px;
  padding: 14px 18px;
  border: 1px solid rgba(255, 255, 255, 0.72);
  border-radius: var(--r-lg);
  background-color: rgba(253, 252, 249, 0.62);
  background-image: linear-gradient(
    118deg,
    rgba(255, 255, 255, 0.3),
    rgba(255, 255, 255, 0) 40%,
    rgba(255, 255, 255, 0.12)
  );
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.9),
    0 8px 20px -18px rgba(28, 25, 18, 0.24);
  -webkit-backdrop-filter: blur(12px) saturate(140%);
  backdrop-filter: blur(12px) saturate(140%);
}

.idx-foot {
  margin-top: 8px;
  border-top: 1px solid var(--line);
}

.idx-foot :deep(.pager) {
  border-top: none;
  padding: 12px 0 0;
  background: transparent;
}

/* 下钻加载时保留列表、整体压暗，避免骨架屏闪一下 */
.idx,
.idx-foot {
  transition: opacity var(--t-fast) var(--ease);
}

.idx.is-pending,
.idx-foot.is-pending {
  opacity: 0.5;
  pointer-events: none;
}

@media (max-width: 900px) {
  .pubb__cols {
    grid-template-columns: minmax(0, 1fr);
    gap: 16px;
  }

  .side {
    position: static;
    padding-top: 18px;
  }

  .side__tabs {
    max-width: 300px;
  }

  .side__list {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  .side__item {
    width: auto;
    padding: 6px 13px;
    border: 1px solid var(--line);
    border-radius: 999px;
  }

  .side__item:hover {
    background: transparent;
  }
}

@media (max-width: 620px) {
  .pubb {
    padding: 0 16px 56px;
  }

  .mast {
    padding: 36px 0 24px;
  }

  .idx {
    gap: 9px;
  }

  .idx__link {
    grid-template-columns: minmax(0, 1fr);
    gap: 6px;
    padding: 13px 15px 14px;
    -webkit-backdrop-filter: blur(9px) saturate(135%);
    backdrop-filter: blur(9px) saturate(135%);
  }

  .idx__date {
    order: 2;
  }

  .idx__arrow {
    display: none;
  }

  .idx-skel {
    grid-template-columns: 74px minmax(0, 1fr);
    min-height: 72px;
    padding: 13px 15px;
    -webkit-backdrop-filter: blur(8px) saturate(135%);
    backdrop-filter: blur(8px) saturate(135%);
  }
}

@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
  .idx__link,
  .idx-skel {
    background-color: var(--surface);
    background-image: none;
  }
}

@media (prefers-reduced-transparency: reduce) {
  .idx__link,
  .idx-skel {
    background-color: var(--surface);
    background-image: none;
    -webkit-backdrop-filter: none;
    backdrop-filter: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .idx__link {
    transition: none;
  }

  .idx__link:hover {
    transform: none;
  }
}
</style>