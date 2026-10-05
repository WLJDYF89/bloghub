<script setup>
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import Icon from '@/components/Icon.vue'
import StatusPill from '@/components/StatusPill.vue'
import Pager from '@/components/Pager.vue'
import EmptyState from '@/components/EmptyState.vue'
import ErrorNotice from '@/components/ErrorNotice.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { articleApi, categoryApi } from '@/api'
import { useToast } from '@/composables/toast'
import { formatAgo } from '@/utils/format'

/**
 * 我的文章。不强制 status=1 —— 作者自己的草稿必须在列表里看得见。
 * 接口 GET /article/my 支持 keyword / status / categoryId / sortBy / sortOrder。
 */

const route = useRoute()
const toast = useToast()

// 从分类页的「N 篇」跳进来时会带 categoryId，直接作为初始筛选。
// 这里在 setup 期就读掉，不在 onMounted 里赋值 —— 否则会触发下面的筛选 watch，
// 进来一次发两个一模一样的请求。
const filters = reactive({
  keyword: '',
  status: '', // '' = 全部 · '0' = 草稿 · '1' = 已发布
  categoryId: route.query.categoryId ? String(route.query.categoryId) : '',
  sortBy: 'updateTime',
  sortOrder: 'desc',
})

const page = reactive({ pageNum: 1, pageSize: 10, total: 0, totalPages: 0 })
const rows = ref([])
const categories = ref([])
const loading = ref(true)
const error = ref(null)

const catName = computed(() => {
  const m = new Map()
  for (const c of categories.value) m.set(c.id, c.name)
  return m
})

const hasFilter = computed(
  () => Boolean(filters.keyword || filters.status !== '' || filters.categoryId !== ''),
)

async function load({ silent = false } = {}) {
  if (!silent) loading.value = true
  error.value = null
  try {
    const data = await articleApi.myPage({
      pageNum: page.pageNum,
      pageSize: page.pageSize,
      keyword: filters.keyword,
      status: filters.status,
      categoryId: filters.categoryId,
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

/** 分类只用来把 id 翻译成名字；拿不到就显示「未归类」，不拦列表 */
async function loadTaxonomy() {
  const cats = await categoryApi.list().catch(() => [])
  categories.value = cats ?? []
}

onMounted(() => {
  loadTaxonomy()
  load()
})

/* 关键词防抖：输入停顿 320ms 才发请求，翻页不重复触发 */
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
  () => [filters.status, filters.categoryId, filters.sortBy, filters.sortOrder],
  () => {
    page.pageNum = 1
    load()
  },
)

function resetFilters() {
  filters.keyword = ''
  filters.status = ''
  filters.categoryId = ''
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

/* ---------------- 行内操作 ---------------- */

const pending = ref(null)
const busyId = ref(null)
const confirm = reactive({ open: false, row: null, busy: false })

/** 只改 status：后端要求 title/content/status/categoryId 至少动一个，只传 status 是合法的 */
async function toggleStatus(row) {
  busyId.value = row.id
  try {
    await articleApi.update({ id: row.id, status: row.status === 1 ? 0 : 1 })
    toast.ok(row.status === 1 ? '已转为草稿' : '已发布')
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
    await articleApi.remove(confirm.row.id)
    toast.ok('已删除')
    confirm.open = false
    // 删掉当前页最后一条时往前退一页，避免停在空页上
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
          <h1 class="page__title">我的文章</h1>
          <p class="page__desc">草稿和已发布都在这里。点标题进编辑器。</p>
        </div>
        <div class="page__actions">
          <button
            class="btn btn--default btn--sm"
            type="button"
            :disabled="loading"
            @click="load()"
          >
            <Icon name="refresh" :size="13" />
            刷新
          </button>
          <RouterLink class="btn btn--primary btn--sm" :to="{ name: 'article-new' }">
            <Icon name="pen" :size="14" />
            写文章
          </RouterLink>
        </div>
      </div>

      <ErrorNotice v-if="error" :error="error" @retry="load()" />

      <section v-else class="panel">
        <div class="toolbar">
          <label class="search" style="flex: 1; min-width: 180px; max-width: 300px">
            <Icon name="search" :size="14" />
            <input v-model="filters.keyword" class="input" placeholder="搜标题或正文…" />
          </label>

          <div class="row gap-1">
            <button
              class="chip"
              :class="{ 'is-on': filters.status === '' }"
              type="button"
              @click="filters.status = ''"
            >
              全部
            </button>
            <button
              class="chip"
              :class="{ 'is-on': filters.status === '1' }"
              type="button"
              @click="filters.status = '1'"
            >
              已发布
            </button>
            <button
              class="chip"
              :class="{ 'is-on': filters.status === '0' }"
              type="button"
              @click="filters.status = '0'"
            >
              草稿
            </button>
          </div>

          <span class="grow" />

          <select v-model="filters.categoryId" class="select" style="width: 150px">
            <option value="">全部分类</option>
            <option v-for="c in categories" :key="c.id" :value="String(c.id)">{{ c.name }}</option>
          </select>

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
          :icon="hasFilter ? 'search' : 'pen'"
          :title="hasFilter ? '没有匹配的文章' : '还没有文章'"
          :desc="
            hasFilter ? '换个关键词或把筛选清掉试试。' : '标题和正文就够了，分类留空也能发。'
          "
        >
          <template #action>
            <button v-if="hasFilter" class="btn btn--default" type="button" @click="resetFilters">
              清除筛选
            </button>
            <RouterLink v-else class="btn btn--primary" :to="{ name: 'article-new' }">
              <Icon name="pen" :size="14" />
              写第一篇
            </RouterLink>
          </template>
        </EmptyState>

        <template v-else>
          <div class="table-wrap">
            <table class="table">
              <thead>
                <tr>
                  <th>标题</th>
                  <th style="width: 130px">分类</th>
                  <th style="width: 110px">状态</th>
                  <th style="width: 130px">更新时间</th>
                  <th style="width: 130px" />
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in rows" :key="row.id">
                  <td>
                    <RouterLink
                      class="table__title"
                      :to="{ name: 'article-edit', params: { id: row.id } }"
                    >
                      {{ row.title || '未命名草稿' }}
                    </RouterLink>
                    <div class="table__meta">
                      {{ row.createTime ? `创建于 ${formatAgo(row.createTime)}` : '' }}
                    </div>
                  </td>
                  <td>
                    <span v-if="row.categoryId" class="badge badge--plain">
                      {{ catName.get(row.categoryId) || '未知分类' }}
                    </span>
                    <span v-else class="c-3">未归类</span>
                  </td>
                  <td><StatusPill :status="row.status" /></td>
                  <td class="col-num c-3">{{ formatAgo(row.updateTime) }}</td>
                  <td>
                    <div class="table__actions">
                      <button
                        class="btn btn--ghost btn--sm"
                        type="button"
                        :disabled="busyId === row.id"
                        :title="row.status === 1 ? '转为草稿' : '发布'"
                        @click="toggleStatus(row)"
                      >
                        <Icon :name="row.status === 1 ? 'inbox' : 'send'" :size="13" />
                        {{ row.status === 1 ? '下架' : '发布' }}
                      </button>
                      <RouterLink
                        class="btn btn--ghost btn--sm btn--icon"
                        :to="{ name: 'article-edit', params: { id: row.id } }"
                        title="编辑"
                      >
                        <Icon name="edit" :size="14" />
                      </RouterLink>
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

      <ConfirmDialog
        :open="confirm.open"
        :title="`删除「${confirm.row?.title || '未命名草稿'}」？`"
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
.row-del:hover {
  color: var(--danger);
}

/* 触摸屏没有 hover，行内操作必须常驻 */
@media (max-width: 860px) {
  .table__actions {
    opacity: 1;
  }
}
</style>