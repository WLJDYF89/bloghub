<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import Icon from '@/components/Icon.vue'
import StatusPill from '@/components/StatusPill.vue'
import Pager from '@/components/Pager.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import EmptyState from '@/components/EmptyState.vue'
import ErrorNotice from '@/components/ErrorNotice.vue'
import { categoryApi } from '@/api'
import { useToast } from '@/composables/toast'
import { useAuth } from '@/stores/auth'
import { formatAgo } from '@/utils/format'

/**
 * 分类。后端 /category/list 返回的 articleCount 只算当前登录用户名下的文章 ——
 * 所以这个数字对每个作者都是自己的，不是全站。
 */

const toast = useToast()
const auth = useAuth()

const rows = ref([])
const loading = ref(true)
const error = ref(null)

const creating = reactive({ open: false, name: '', sortOrder: '', busy: false })
const editing = ref(null) // { id, name, sortOrder }
const confirm = reactive({ open: false, row: null, busy: false })

const totalArticles = computed(() => rows.value.reduce((s, c) => s + (c.articleCount || 0), 0))

/* 普通作者只看到「我名下有文章」的分类：一篇都没有的不占位置，
   只有当某篇文章选了它，它才会出现。管理员要管理全部分类，所以看全量。 */
const visibleRows = computed(() =>
  auth.isAdmin ? rows.value : rows.value.filter((c) => (c.articleCount || 0) > 0),
)

async function load() {
  loading.value = true
  error.value = null
  try {
    rows.value = (await categoryApi.list()) ?? []
  } catch (e) {
    error.value = e
    rows.value = []
  } finally {
    loading.value = false
  }
}

onMounted(load)

/* ---------------- 分类详情：这个分类下我的文章 ----------------
 * 不跳路由、不换标签页 —— 就在本页把列表换成详情，靠左上角的返回按钮切回。
 * 用内部状态而不是子路由，是为了让侧栏的「分类」始终保持高亮。 */

const detail = ref(null) // { id, name }

const artRows = ref([])
const artLoading = ref(false)
const artError = ref(null)
const artPage = reactive({ pageNum: 1, pageSize: 10, total: 0, totalPages: 0 })

async function openDetail(row) {
  detail.value = { id: row.id, name: row.name }
  artPage.pageNum = 1
  await loadArticles()
}

function closeDetail() {
  detail.value = null
  artRows.value = []
  artError.value = null
}

async function loadArticles() {
  if (!detail.value) return
  artLoading.value = true
  artError.value = null
  try {
    const data = await categoryApi.articles(detail.value.id, {
      pageNum: artPage.pageNum,
      pageSize: artPage.pageSize,
    })
    artRows.value = data?.records ?? []
    artPage.total = data?.total ?? 0
    artPage.totalPages = data?.totalPages ?? 0
    artPage.pageNum = data?.pageNum ?? artPage.pageNum
  } catch (e) {
    artError.value = e
    artRows.value = []
    artPage.total = 0
    artPage.totalPages = 0
  } finally {
    artLoading.value = false
  }
}

function goArtPage(n) {
  if (n < 1 || (artPage.totalPages && n > artPage.totalPages)) return
  artPage.pageNum = n
  loadArticles()
}

const nameInput = ref(null)

async function focusName() {
  creating.open = true
  await nextTick()
  nameInput.value?.focus()
}

async function create() {
  const name = creating.name.trim()
  if (!name) {
    toast.error('分类名不能为空')
    return
  }
  creating.busy = true
  try {
    await categoryApi.add({ name, sortOrder: creating.sortOrder === '' ? 0 : Number(creating.sortOrder) })
    toast.ok('分类已建立')
    creating.name = ''
    creating.sortOrder = ''
    creating.open = false
    await load()
  } catch (e) {
    toast.error(e.message)
  } finally {
    creating.busy = false
  }
}

function startEdit(row) {
  editing.value = {
    id: row.id,
    name: row.name,
    sortOrder: String(row.sortOrder ?? 0),
  }
}

async function saveEdit() {
  const e = editing.value
  if (!e) return
  const name = e.name.trim()
  if (!name) {
    toast.error('分类名不能为空')
    return
  }
  try {
    // 后端 update 要求 name 非空，sortOrder 为空则落 0 —— 两个都带上
    await categoryApi.update({ id: e.id, name, sortOrder: e.sortOrder === '' ? 0 : Number(e.sortOrder) })
    toast.ok('已保存')
    editing.value = null
    await load()
  } catch (err) {
    toast.error(err.message)
  }
}

async function doDelete() {
  confirm.busy = true
  try {
    await categoryApi.remove(confirm.row.id)
    toast.ok('分类已删除')
    confirm.open = false
    await load()
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
      <!-- ---------- 分类详情：这个分类下我的文章 ---------- -->
      <template v-if="detail">
        <div class="page__head">
          <div class="page__head-main">
            <button class="btn btn--ghost btn--sm back" type="button" @click="closeDetail">
              <Icon name="arrowLeft" :size="13" />
              返回分类
            </button>
            <h1 class="page__title">{{ detail.name }}</h1>
            <p class="page__desc">这个分类下你名下的文章，按更新时间倒序。</p>
          </div>
          <div class="page__actions">
            <button
              class="btn btn--default btn--sm"
              type="button"
              :disabled="artLoading"
              @click="loadArticles"
            >
              <Icon name="refresh" :size="13" />
              刷新
            </button>
          </div>
        </div>

        <section class="panel">
          <div class="panel__head">
            <h2 class="panel__title">文章</h2>
            <span class="grow" />
            <span class="panel__sub num">{{ artPage.total }} 篇</span>
          </div>

          <ErrorNotice v-if="artError" :error="artError" @retry="loadArticles" />

          <div v-else-if="artLoading" class="panel__body stack gap-4">
            <div v-for="i in 5" :key="i" class="skeleton" style="height: 18px" />
          </div>

          <EmptyState
            v-else-if="!artRows.length"
            icon="docs"
            title="这个分类下还没有文章"
            desc="在编辑器里把文章归到这个分类，它就会出现在这里。"
          >
            <template #action>
              <RouterLink class="btn btn--default" :to="{ name: 'my-articles' }">
                去我的文章
              </RouterLink>
            </template>
          </EmptyState>

          <template v-else>
            <div class="table-wrap">
              <table class="table">
                <thead>
                  <tr>
                    <th>标题</th>
                    <th style="width: 110px">状态</th>
                    <th style="width: 130px">更新时间</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="row in artRows" :key="row.id">
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
                    <td><StatusPill :status="row.status" /></td>
                    <td class="col-num c-3">{{ formatAgo(row.updateTime) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>

            <Pager
              :page="artPage.pageNum"
              :page-size="artPage.pageSize"
              :total="artPage.total"
              :total-pages="artPage.totalPages"
              @go="goArtPage"
            />
          </template>
        </section>
      </template>

      <!-- ---------- 分类列表 ---------- -->
      <template v-else>
        <div class="page__head">
          <div class="page__head-main">
            <h1 class="page__title">分类</h1>
            <p class="page__desc">
              {{
                auth.isAdmin
                  ? '篇数只算你名下的文章。删掉分类不会删文章，那些文章会变成「未归类」。'
                  : '只列出你名下有文章的分类，篇数也只算你名下的。删掉分类不会删文章，那些文章会变成「未归类」。'
              }}
            </p>
          </div>
          <div class="page__actions">
            <button class="btn btn--default btn--sm" type="button" :disabled="loading" @click="load">
              <Icon name="refresh" :size="13" />
              刷新
            </button>
            <button
              v-if="auth.isAdmin"
              class="btn btn--primary btn--sm"
              type="button"
              @click="focusName"
            >
              <Icon name="plus" :size="14" />
              新建分类
            </button>
          </div>
        </div>

        <ErrorNotice v-if="error" :error="error" @retry="load" />

        <template v-else>
        <section v-if="creating.open" class="panel" style="margin-bottom: 16px">
          <div class="panel__head">
            <h2 class="panel__title">新建分类</h2>
            <span class="panel__sub">排序号越小越靠前，留空按 0</span>
            <span class="grow" />
            <button class="btn btn--quiet btn--sm" type="button" @click="creating.open = false">
              收起
            </button>
          </div>
          <form class="panel__body row gap-2 wrap" @submit.prevent="create">
            <input
              ref="nameInput"
              v-model="creating.name"
              class="input"
              style="flex: 1; min-width: 200px"
              placeholder="分类名，例如「工程手记」"
              maxlength="30"
            />
            <input
              v-model="creating.sortOrder"
              class="input"
              style="width: 110px"
              type="number"
              placeholder="排序号"
              min="0"
            />
            <button class="btn btn--primary" type="submit" :disabled="creating.busy">
              {{ creating.busy ? '建立中…' : '建立' }}
            </button>
          </form>
        </section>

        <section class="panel">
          <div class="panel__head">
            <h2 class="panel__title">{{ auth.isAdmin ? '全部分类' : '我的分类' }}</h2>
            <span class="grow" />
            <span class="panel__sub">
              {{ visibleRows.length }} 个 · 共 {{ totalArticles }} 篇
            </span>
          </div>

          <div v-if="loading" class="panel__body stack gap-4">
            <div v-for="i in 5" :key="i" class="skeleton" style="height: 18px" />
          </div>

          <EmptyState
            v-else-if="!visibleRows.length"
            icon="folder"
            :title="rows.length ? '还没有可显示的分类' : '还没有分类'"
            :desc="
              rows.length
                ? '你名下的文章都还没归类 —— 给文章选一个分类，它就会出现在这里。'
                : auth.isAdmin
                  ? '分类是可选的。先建一个，之后在编辑器里就能把文章归进去。'
                  : '分类由管理员统一维护，你给文章选好分类后，它就会出现在这里。'
            "
          >
            <template #action>
              <button v-if="auth.isAdmin" class="btn btn--primary" type="button" @click="focusName">
                <Icon name="plus" :size="14" />
                新建分类
              </button>
            </template>
          </EmptyState>

          <div v-else class="table-wrap">
            <table class="table table--always-actions">
              <thead>
                <tr>
                  <th>名称</th>
                  <th style="width: 110px">排序号</th>
                  <th style="width: 120px">我的文章</th>
                  <th style="width: 150px" />
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in visibleRows" :key="row.id">
                  <!-- 改名：整行切成输入，Enter 保存 / Esc 取消 -->
                  <template v-if="editing && editing.id === row.id">
                    <td>
                      <input
                        v-model="editing.name"
                        class="input"
                        maxlength="30"
                        aria-label="分类名"
                        @keydown.enter.prevent="saveEdit"
                        @keydown.esc="editing = null"
                      />
                    </td>
                    <td>
                      <input
                        v-model="editing.sortOrder"
                        class="input"
                        type="number"
                        min="0"
                        aria-label="排序号"
                        @keydown.enter.prevent="saveEdit"
                        @keydown.esc="editing = null"
                      />
                    </td>
                    <td class="col-num c-3">{{ row.articleCount ?? 0 }}</td>
                    <td>
                      <div class="table__actions">
                        <button class="btn btn--default btn--sm" type="button" @click="editing = null">
                          取消
                        </button>
                        <button class="btn btn--primary btn--sm" type="button" @click="saveEdit">
                          保存
                        </button>
                      </div>
                    </td>
                  </template>

                  <template v-else>
                    <td><span class="table__title" style="max-width: none">{{ row.name }}</span></td>
                    <td class="num c-3">{{ row.sortOrder ?? 0 }}</td>
                    <td>
                      <button
                        v-if="row.articleCount"
                        class="count"
                        type="button"
                        @click="openDetail(row)"
                      >
                        {{ row.articleCount }} 篇
                        <Icon name="chevronRight" :size="12" />
                      </button>
                      <span v-else class="c-3">暂无</span>
                    </td>
                    <td>
                      <div class="table__actions">
                        <button
                          v-if="auth.isAdmin"
                          class="btn btn--ghost btn--sm"
                          type="button"
                          @click="startEdit(row)"
                        >
                          <Icon name="edit" :size="13" />
                          改名
                        </button>
                        <button
                          v-if="auth.isAdmin"
                          class="btn btn--ghost btn--sm btn--icon row-del"
                          type="button"
                          title="删除"
                          @click="((confirm.row = row), (confirm.open = true))"
                        >
                          <Icon name="trash" :size="14" />
                        </button>
                      </div>
                    </td>
                  </template>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
        </template>
      </template>

      <ConfirmDialog
        :open="confirm.open"
        :title="`删除分类「${confirm.row?.name || ''}」？`"
        :body="
          confirm.row?.articleCount
            ? `该分类下 ${confirm.row.articleCount} 篇文章会变成「未归类」，文章本身不会被删。`
            : '这个分类下还没有文章，删除不会影响任何内容。'
        "
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
/* 返回按钮在页面左上角，单独占一行 */
.back {
  margin-bottom: 8px;
}

.count {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 0;
  border: 0;
  background: none;
  cursor: pointer;
  font-size: 15px;
  color: var(--text-2);
  font-variant-numeric: tabular-nums;
}

.count:hover {
  color: var(--accent);
}

.row-del:hover {
  color: var(--danger);
}
</style>