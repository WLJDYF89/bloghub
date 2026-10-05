<script setup>
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import Icon from '@/components/Icon.vue'
import StatusPill from '@/components/StatusPill.vue'
import Pager from '@/components/Pager.vue'
import EmptyState from '@/components/EmptyState.vue'
import ErrorNotice from '@/components/ErrorNotice.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { adminApi } from '@/api'
import { useToast } from '@/composables/toast'
import { formatAgo } from '@/utils/format'

/**
 * 全站文章（管理员）。
 *
 * GET /admin/article/list 不限制 status（草稿也看得到），也支持按 author 精确过滤，
 * 但**不支持按 status 过滤** —— 所以这里没有状态筛选，只有关键词 + 作者 + 排序。
 * 返回的是完整 Article（含 content），所以能直接在弹层里读全文，不用再请求一次。
 */

const route = useRoute()
const toast = useToast()

const filters = reactive({
  // 概览页的 triage 列表会带 ?q=标题 跳进来，直接当初始搜索词
  keyword: String(route.query.q || ''),
  author: '',
  sortBy: 'updateTime',
  sortOrder: 'desc',
})

const page = reactive({ pageNum: 1, pageSize: 10, total: 0, totalPages: 0 })
const rows = ref([])
const authors = ref([])
const loading = ref(true)
const error = ref(null)

const hasFilter = computed(() => Boolean(filters.keyword || filters.author))

async function load({ silent = false } = {}) {
  if (!silent) loading.value = true
  error.value = null
  try {
    const data = await adminApi.articlePage({
      pageNum: page.pageNum,
      pageSize: page.pageSize,
      keyword: filters.keyword,
      author: filters.author,
      sortBy: filters.sortBy,
      sortOrder: filters.sortOrder,
    })
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
  }
}

/** 作者筛选项来自用户列表；拿不到就退化成只有「全部作者」，不拦列表 */
async function loadAuthors() {
  try {
    authors.value = (await adminApi.users())?.map((u) => u.userName) ?? []
  } catch {
    authors.value = []
  }
}

onMounted(() => {
  loadAuthors()
  load()
})

let timer = null
watch(
  () => filters.keyword,
  () => {
    clearTimeout(timer)
    timer = setTimeout(() => {
      page.pageNum = 1
      load()
    }, 320)
  },
)
onUnmounted(() => clearTimeout(timer))

watch(
  () => [filters.author, filters.sortBy, filters.sortOrder],
  () => {
    page.pageNum = 1
    load()
  },
)

function resetFilters() {
  filters.keyword = ''
  filters.author = ''
  filters.sortBy = 'updateTime'
  filters.sortOrder = 'desc'
  page.pageNum = 1
  load()
}

function goPage(n) {
  if (n < 1 || (page.totalPages && n > page.totalPages)) return
  page.pageNum = n
  load()
}

/* ---------------- 全文弹层 ---------------- */

const reader = ref(null)

/* ---------------- 行内操作 ---------------- */

const busyId = ref(null)
const confirm = reactive({ open: false, row: null, busy: false })

async function toggleStatus(row) {
  busyId.value = row.id
  try {
    await adminApi.updateArticle({ id: row.id, status: row.status === 1 ? 0 : 1 })
    toast.ok(row.status === 1 ? '已下架为草稿' : '已发布')
    await load({ silent: true })
  } catch (e) {
    toast.error(e.message)
  } finally {
    busyId.value = null
  }
}

async function doDelete() {
  confirm.busy = true
  try {
    await adminApi.removeArticle(confirm.row.id)
    toast.ok('已删除')
    confirm.open = false
    if (rows.value.length === 1 && page.pageNum > 1) page.pageNum -= 1
    await load({ silent: true })
  } catch (e) {
    toast.error(e.message)
  } finally {
    confirm.busy = false
  }
}
</script>

<template>
  <div class="page">
    <div class="page__inner">
      <div class="page__head">
        <div class="page__head-main">
          <h1 class="page__title">全站文章</h1>
          <p class="page__desc">
            所有人的文章都在这里，草稿也看得到。可以改状态、删内容、读全文。
          </p>
        </div>
        <div class="page__actions">
          <button class="btn btn--default btn--sm" type="button" :disabled="loading" @click="load()">
            <Icon name="refresh" :size="13" />
            刷新
          </button>
        </div>
      </div>

      <ErrorNotice v-if="error" :error="error" @retry="load()" />

      <section v-else class="panel">
        <div class="toolbar">
          <label class="search" style="flex: 1; min-width: 180px; max-width: 320px">
            <Icon name="search" :size="14" />
            <input v-model="filters.keyword" class="input" placeholder="搜标题或正文…" />
          </label>

          <select v-model="filters.author" class="select" style="width: 168px" aria-label="作者">
            <option value="">全部作者</option>
            <option v-for="a in authors" :key="a" :value="a">{{ a }}</option>
            <!-- 作者列表没取到时，至少让人知道筛选项为什么是空的 -->
            <option v-if="!authors.length" value="" disabled>作者列表未加载</option>
          </select>

          <span class="grow" />

          <select v-model="filters.sortBy" class="select" style="width: 128px">
            <option value="updateTime">按更新时间</option>
            <option value="createTime">按创建时间</option>
          </select>
          <select v-model="filters.sortOrder" class="select" style="width: 100px">
            <option value="desc">新→旧</option>
            <option value="asc">旧→新</option>
          </select>
          <button v-if="hasFilter" class="btn btn--quiet btn--sm" type="button" @click="resetFilters">
            <Icon name="x" :size="13" />
            清除
          </button>
        </div>

        <div v-if="loading" class="panel__body stack gap-4">
          <div v-for="i in 6" :key="i" class="skeleton" style="height: 18px" />
        </div>

        <EmptyState
          v-else-if="!rows.length"
          :icon="hasFilter ? 'search' : 'layers'"
          :title="hasFilter ? '没有匹配的文章' : '全站还没有文章'"
          :desc="hasFilter ? '换个关键词或作者试试。' : '等第一位作者发出第一篇。'"
        >
          <template #action>
            <button v-if="hasFilter" class="btn btn--default" type="button" @click="resetFilters">
              清除筛选
            </button>
          </template>
        </EmptyState>

        <template v-else>
          <div class="table-wrap">
            <table class="table">
              <thead>
                <tr>
                  <th>标题</th>
                  <th style="width: 130px">作者</th>
                  <th style="width: 105px">状态</th>
                  <th style="width: 125px">更新</th>
                  <th style="width: 190px" />
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in rows" :key="row.id">
                  <td>
                    <button class="table__title" type="button" @click="reader = row">
                      {{ row.title || '未命名草稿' }}
                    </button>
                    <div class="table__meta">
                      {{ row.content ? `约 ${row.content.length} 字符` : '正文为空' }}
                    </div>
                  </td>
                  <td class="c-2">{{ row.author }}</td>
                  <td><StatusPill :status="row.status" /></td>
                  <td class="col-num c-3">{{ formatAgo(row.updateTime) }}</td>
                  <td>
                    <div class="table__actions">
                      <button class="btn btn--ghost btn--sm" type="button" @click="reader = row">
                        <Icon name="eye" :size="13" />
                        读全文
                      </button>
                      <button
                        class="btn btn--ghost btn--sm"
                        type="button"
                        :disabled="busyId === row.id"
                        @click="toggleStatus(row)"
                      >
                        {{ row.status === 1 ? '下架' : '发布' }}
                      </button>
                      <button
                        class="btn btn--ghost btn--sm btn--icon row-del"
                        type="button"
                        title="删除"
                        @click="((confirm.row = row), (confirm.open = true))"
                      >
                        <Icon name="trash" :size="14" />
                      </button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <Pager
            :page="page.pageNum"
            :page-size="page.pageSize"
            :total="page.total"
            :total-pages="page.totalPages"
            @go="goPage"
          />
        </template>
      </section>

      <!-- 全文只读。列表接口已经带了 content，不必再打一次详情 -->
      <Teleport to="body">
        <div v-if="reader" class="overlay" @click.self="reader = null">
          <div class="reader" role="dialog" aria-modal="true">
            <header class="reader__head">
              <div style="min-width: 0">
                <h2 class="reader__title">{{ reader.title || '未命名草稿' }}</h2>
                <p class="reader__meta">
                  {{ reader.author }} · <StatusPill :status="reader.status" size="sm" /> ·
                  {{ reader.updateTime }}
                </p>
              </div>
              <span class="grow" />
              <button
                class="btn btn--ghost btn--sm btn--icon"
                type="button"
                aria-label="关闭"
                @click="reader = null"
              >
                <Icon name="x" :size="15" />
              </button>
            </header>
            <div class="reader__body">
              <div v-if="reader.content" class="prose">{{ reader.content }}</div>
              <p v-else class="c-3">这篇还没有正文。</p>
            </div>
            <footer class="reader__foot">
              <span class="t-xs c-3">
                管理员视图只读。要改内容请让作者自己改，或直接在列表里下架。
              </span>
              <span class="grow" />
              <RouterLink
                v-if="reader.status === 1"
                class="btn btn--default btn--sm"
                :to="{ name: 'article', params: { id: reader.id } }"
                target="_blank"
              >
                在博客上打开
                <Icon name="arrowUpRight" :size="13" />
              </RouterLink>
            </footer>
          </div>
        </div>
      </Teleport>

      <ConfirmDialog
        :open="confirm.open"
        :title="`删除「${confirm.row?.title || '未命名草稿'}」？`"
        :body="`作者是 ${confirm.row?.author || '未知'}。删除后无法恢复。`"
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
.row-del:hover {
  color: var(--danger);
}

/* 标题列是个按钮（点了开全文），外观保持和链接一致 */
button.table__title {
  text-align: left;
  width: 100%;
}

@media (max-width: 860px) {
  .table__actions {
    opacity: 1;
  }
}

.reader {
  width: 100%;
  max-width: 720px;
  max-height: 84dvh;
  display: flex;
  flex-direction: column;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  box-shadow: var(--shadow-3);
  overflow: hidden;
}

.reader__head {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px 18px 14px;
  border-bottom: 1px solid var(--line);
}

.reader__title {
  font-size: 19px;
  font-weight: 600;
  letter-spacing: -0.02em;
}

.reader__meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  font-size: 13px;
  color: var(--text-3);
}

.reader__body {
  padding: 20px 22px 26px;
  overflow-y: auto;
}

.reader__foot {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 18px;
  border-top: 1px solid var(--line);
  background: var(--surface-2);
}
</style>