<script setup>
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import Icon from '@/components/Icon.vue'
import StatusPill from '@/components/StatusPill.vue'
import EmptyState from '@/components/EmptyState.vue'
import ErrorNotice from '@/components/ErrorNotice.vue'
import { adminApi, articleApi, categoryApi } from '@/api'
import { useAuth } from '@/stores/auth'
import { daysSince, formatAgo } from '@/utils/format'

/**
 * 概览。
 *
 * 两种角色共用同一版式，数据源不同：
 * - 作者走 GET /article/overview（后端按当前登录用户聚合，数字都是现成的）
 * - 管理员没有对应统计接口（countAllArticle 不支持 status 过滤），
 *   所以不装作有一屏仪表盘，只用三个真实调用：全站 total + 用户全量 + 分类全量。
 */

const auth = useAuth()

const loading = ref(true)
const error = ref(null)
const stats = ref(null)
const adminStats = ref(null)

async function loadAdmin() {
  const [fresh, stale] = await Promise.all([
    adminApi.articlePage({ pageNum: 1, pageSize: 6, sortBy: 'updateTime', sortOrder: 'desc' }),
    adminApi.articlePage({ pageNum: 1, pageSize: 6, sortBy: 'updateTime', sortOrder: 'asc' }),
  ])
  // 作者数 / 分类数是补充信息，拿不到就少两句话，不该炸掉整页
  const [users, cats] = await Promise.allSettled([adminApi.users(), categoryApi.list()])
  adminStats.value = {
    total: fresh?.total ?? 0,
    recent: fresh?.records ?? [],
    stale: stale?.records ?? [],
    userCount: users.status === 'fulfilled' ? users.value.length : null,
    catCount: cats.status === 'fulfilled' ? cats.value.length : null,
  }
}

async function load() {
  loading.value = true
  error.value = null
  try {
    if (auth.isAdmin) await loadAdmin()
    else stats.value = await articleApi.overview()
  } catch (e) {
    error.value = e
  } finally {
    loading.value = false
  }
}

onMounted(load)

/* ---------------- 结论条 ---------------- */

/** 零值整条不出现：不写「0 篇草稿」这种没有信息量的短语 */
const clauses = computed(() => {
  if (auth.isAdmin) {
    const s = adminStats.value
    if (!s) return []
    const out = [{ num: s.total, post: ' 篇文章' }]
    if (s.userCount) out.push({ num: s.userCount, post: ' 位作者' })
    if (s.catCount) out.push({ num: s.catCount, post: ' 个分类' })
    const oldest = s.stale[0]
    if (oldest) {
      const d = daysSince(oldest.updateTime)
      if (d > 0) out.push({ pre: '最久没动的一篇 ', num: d, post: ' 天没动过', accent: true })
    }
    return out
  }

  const o = stats.value
  if (!o) return []
  const out = []
  if (o.publishedCount > 0) out.push({ num: o.publishedCount, post: ' 篇已发布' })
  if (o.draftCount > 0) out.push({ num: o.draftCount, post: ' 篇草稿' })
  if (o.weekNewCount > 0) out.push({ pre: '本周新增 ', num: o.weekNewCount, post: ' 篇' })
  if (o.longestDraftDays > 0) {
    out.push({ pre: '最久一篇躺了 ', num: o.longestDraftDays, post: ' 天', accent: true })
  }
  return out
})

/* ---------------- 指标卡 ---------------- */
/* 已发布 / 草稿是主对象的两种状态，即使是 0 也说明问题，恒显示；
   本周新增 / 最久停滞 / 作者数 / 分类数 属于补充，为 0 就不占位置。 */

const kpis = computed(() => {
  if (auth.isAdmin) {
    const s = adminStats.value
    if (!s) return []
    const out = [{ label: '全站文章', value: s.total }]
    if (s.userCount) out.push({ label: '作者', value: s.userCount })
    if (s.catCount) out.push({ label: '分类', value: s.catCount })
    return out
  }
  const o = stats.value
  if (!o) return []
  const out = [
    { label: '已发布', value: o.publishedCount },
    { label: '草稿', value: o.draftCount },
  ]
  if (o.weekNewCount > 0) out.push({ label: '本周新增', value: o.weekNewCount })
  if (o.longestDraftDays > 0) {
    out.push({ label: '最久停滞', value: o.longestDraftDays, unit: '天' })
  }
  return out
})

/** 卡片少的时候别让它们被拉成一整条，宽度按卡数收口 */
const kpiWidth = computed(() => ({ maxWidth: `${kpis.value.length * 232}px` }))

const coldStart = computed(() => {
  if (auth.isAdmin) return (adminStats.value?.total ?? 0) === 0
  const o = stats.value
  return !o || (o.publishedCount === 0 && o.draftCount === 0)
})

const staleDrafts = computed(() => stats.value?.staleDrafts ?? [])
const recent = computed(() => stats.value?.recent ?? [])
</script>

<template>
  <div class="page">
    <div class="page__inner">
      <div class="page__head">
        <div class="page__head-main">
          <h1 class="page__title">概览</h1>
          <p class="page__desc">
            {{
              auth.isAdmin
                ? '全站规模，以及最久没人碰过的那几篇。'
                : '该写什么、该发什么，都摆在这一屏里。'
            }}
          </p>
        </div>
        <div class="page__actions">
          <button class="btn btn--default btn--sm" type="button" :disabled="loading" @click="load">
            <Icon name="refresh" :size="13" />
            刷新
          </button>
        </div>
      </div>

      <ErrorNotice v-if="error" :error="error" @retry="load" />

      <template v-else-if="loading">
        <div class="skeleton" style="width: min(340px, 62%); height: 20px" />
        <div class="grid-kpi" style="margin-top: 22px">
          <div v-for="i in 4" :key="i" class="kpi">
            <div class="skeleton" style="width: 46px" />
            <div class="skeleton" style="width: 68px; height: 24px; margin-top: 11px" />
          </div>
        </div>
        <div class="grid-2" style="margin-top: 20px">
          <div v-for="i in 2" :key="i" class="panel">
            <div class="panel__head"><div class="skeleton" style="width: 84px" /></div>
            <div class="panel__body stack gap-4">
              <div v-for="j in 3" :key="j" class="skeleton" style="height: 15px" />
            </div>
          </div>
        </div>
      </template>

      <div v-else-if="coldStart" class="panel">
        <EmptyState
          v-if="auth.isAdmin"
          icon="layers"
          title="全站还没有内容"
          desc="第一篇被别人写出来之前，这里不会有数字。可以先看看站里已经有哪些人。"
        >
          <template #action>
            <RouterLink class="btn btn--default" :to="{ name: 'users' }">查看用户</RouterLink>
          </template>
        </EmptyState>
        <EmptyState
          v-else
          icon="pen"
          title="还没有写下第一篇"
          desc="标题和正文就够了，分类留空也能发。"
        >
          <template #action>
            <RouterLink class="btn btn--primary" :to="{ name: 'article-new' }">
              <Icon name="pen" :size="14" />
              写下第一篇
            </RouterLink>
          </template>
        </EmptyState>
      </div>

      <template v-else>
        <!-- 结论条：一行能读懂的句子，不是标题 -->
        <div class="verdict-row">
          <p class="verdict">
            <template v-for="(c, i) in clauses" :key="i">
              <span v-if="i" class="verdict__sep">·</span>
              <span v-if="c.accent" class="verdict__act">{{ c.pre }}{{ c.num }}{{ c.post }}</span>
              <span v-else>{{ c.pre }}<b>{{ c.num }}</b>{{ c.post }}</span>
            </template>
          </p>
        </div>

        <div class="grid-kpi" :style="kpiWidth">
          <div v-for="k in kpis" :key="k.label" class="kpi">
            <div class="kpi__label">{{ k.label }}</div>
            <div class="kpi__value">
              {{ k.value }}<span v-if="k.unit" class="kpi__unit">{{ k.unit }}</span>
            </div>
          </div>
        </div>

        <!-- ------------- 作者视角 ------------- -->
        <div v-if="!auth.isAdmin" class="grid-2" style="margin-top: 20px">
          <section class="panel">
            <div class="panel__head">
              <h2 class="panel__title">需要处理</h2>
              <span class="panel__sub">躺得最久的草稿</span>
              <span class="grow" />
              <span v-if="staleDrafts.length" class="badge">{{ staleDrafts.length }}</span>
            </div>
            <div v-if="staleDrafts.length" class="panel__body panel__body--flush">
              <ul class="feed">
                <li v-for="a in staleDrafts" :key="a.id">
                  <RouterLink
                    class="feed__item"
                    :to="{ name: 'article-edit', params: { id: a.id } }"
                  >
                    <span class="feed__main">
                      <span class="feed__title">{{ a.title || '未命名草稿' }}</span>
                      <span class="feed__meta">
                        {{ a.daysSinceUpdate ?? daysSince(a.updateTime) }} 天没动 ·
                        {{ formatAgo(a.updateTime) }}
                      </span>
                    </span>
                    <span class="feed__cta">
                      继续写
                      <Icon name="chevronRight" :size="13" />
                    </span>
                  </RouterLink>
                </li>
              </ul>
            </div>
            <EmptyState
              v-else
              icon="check"
              title="没有躺着的草稿"
              desc="手上的东西都在动，挺好。"
            />
          </section>

          <section class="panel">
            <div class="panel__head">
              <h2 class="panel__title">最近编辑</h2>
              <span class="panel__sub">按更新时间</span>
              <span class="grow" />
              <RouterLink class="btn btn--quiet btn--sm" :to="{ name: 'my-articles' }">
                全部文章
                <Icon name="chevronRight" :size="13" />
              </RouterLink>
            </div>
            <div v-if="recent.length" class="panel__body panel__body--flush">
              <ul class="feed">
                <li v-for="a in recent" :key="a.id">
                  <RouterLink
                    class="feed__item"
                    :to="{ name: 'article-edit', params: { id: a.id } }"
                  >
                    <span class="feed__main">
                      <span class="feed__title">{{ a.title || '未命名草稿' }}</span>
                      <span class="feed__meta feed__meta--row">
                        <StatusPill :status="a.status" size="sm" />
                        <span>·</span>
                        <span>{{ formatAgo(a.updateTime) }}</span>
                      </span>
                    </span>
                  </RouterLink>
                </li>
              </ul>
            </div>
            <EmptyState
              v-else
              icon="inbox"
              title="还没有可显示的记录"
              desc="写一篇之后，这里会按时间排开。"
            />
          </section>
        </div>

        <!-- ------------- 管理员视角 ------------- -->
        <div v-else class="grid-2" style="margin-top: 20px">
          <section class="panel">
            <div class="panel__head">
              <h2 class="panel__title">最久没更新</h2>
              <span class="panel__sub">全站排序</span>
              <span class="grow" />
              <RouterLink class="btn btn--quiet btn--sm" :to="{ name: 'all-articles' }">
                全站文章
                <Icon name="chevronRight" :size="13" />
              </RouterLink>
            </div>
            <div v-if="adminStats.stale.length" class="panel__body panel__body--flush">
              <ul class="feed">
                <li v-for="a in adminStats.stale" :key="a.id">
                  <RouterLink
                    class="feed__item"
                    :to="{ name: 'all-articles', query: { q: a.title } }"
                  >
                    <span class="feed__main">
                      <span class="feed__title">{{ a.title || '未命名草稿' }}</span>
                      <span class="feed__meta feed__meta--row">
                        <span>{{ a.author }}</span>
                        <span>·</span>
                        <StatusPill :status="a.status" size="sm" />
                        <span>·</span>
                        <span>{{ daysSince(a.updateTime) }} 天没动</span>
                      </span>
                    </span>
                  </RouterLink>
                </li>
              </ul>
            </div>
            <EmptyState v-else icon="inbox" title="全站还没有文章" desc="等第一位作者动手。" />
          </section>

          <section class="panel">
            <div class="panel__head">
              <h2 class="panel__title">最近更新</h2>
              <span class="panel__sub">全站排序</span>
              <span class="grow" />
              <RouterLink class="btn btn--quiet btn--sm" :to="{ name: 'all-articles' }">
                全站文章
                <Icon name="chevronRight" :size="13" />
              </RouterLink>
            </div>
            <div v-if="adminStats.recent.length" class="panel__body panel__body--flush">
              <ul class="feed">
                <li v-for="a in adminStats.recent" :key="a.id">
                  <RouterLink
                    class="feed__item"
                    :to="{ name: 'all-articles', query: { q: a.title } }"
                  >
                    <span class="feed__main">
                      <span class="feed__title">{{ a.title || '未命名草稿' }}</span>
                      <span class="feed__meta feed__meta--row">
                        <span>{{ a.author }}</span>
                        <span>·</span>
                        <StatusPill :status="a.status" size="sm" />
                        <span>·</span>
                        <span>{{ formatAgo(a.updateTime) }}</span>
                      </span>
                    </span>
                  </RouterLink>
                </li>
              </ul>
            </div>
            <EmptyState v-else icon="inbox" title="全站还没有文章" desc="等第一位作者动手。" />
          </section>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.feed {
  display: flex;
  flex-direction: column;
}

.feed li + li {
  border-top: 1px solid var(--line-2);
}

.feed__item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 11px 16px;
  min-height: 52px;
  transition: background-color var(--t-fast) var(--ease);
}

.feed__item:hover {
  background: var(--surface-2);
}

.feed__main {
  min-width: 0;
  flex: 1;
  display: block;
}

.feed__title {
  display: block;
  font-size: 15px;
  font-weight: 500;
  color: var(--text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.feed__item:hover .feed__title {
  color: var(--accent);
}

.feed__meta {
  display: block;
  margin-top: 3px;
  font-size: 12.5px;
  color: var(--text-3);
}

.feed__meta--row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.feed__cta {
  flex: none;
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 13px;
  color: var(--text-3);
  transition: color var(--t-fast) var(--ease);
}

.feed__item:hover .feed__cta {
  color: var(--accent);
}
</style>