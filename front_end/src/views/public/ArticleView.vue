<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import Icon from '@/components/Icon.vue'
import EmptyState from '@/components/EmptyState.vue'
import ErrorNotice from '@/components/ErrorNotice.vue'
import { articleApi } from '@/api'
import { useAuth } from '@/stores/auth'

/**
 * 公开阅读页。
 *
 * GET /article/{id}：草稿只有作者本人能打开，别人拿到的是「文章不存在」——
 * 所以这里不需要额外判断权限，把后端的判定如实呈现就行。
 * 分类名称表游客拿不到（/category/list 需要登录），所以不渲染这个字段。
 */

const route = useRoute()
const auth = useAuth()

const article = ref(null)
const loading = ref(true)
const error = ref(null)

const id = computed(() => Number(route.params.id))

/** 后端只在「作者本人打开自己的草稿」这一种情况下会返回 status=0 */
const isOwnDraft = computed(() => article.value && article.value.status !== 1)

/** 从哪儿进来的就回哪儿：把首页侧栏的状态（标签页 + 选中的分类）原样带回去 */
const fromCategoryTab = computed(
  () => route.query.tab === 'cat' || Boolean(route.query.category),
)

const backQuery = computed(() => {
  const q = {}
  if (route.query.tab === 'cat') q.tab = 'cat'
  if (route.query.category) q.category = route.query.category
  return q
})

async function load() {
  loading.value = true
  error.value = null
  article.value = null
  try {
    article.value = await articleApi.detail(id.value)
  } catch (e) {
    error.value = e
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(id, load)

/** 拿不到文章时，区分「不存在」和「后端出问题」——前者不该给重试按钮 */
const notFound = computed(
  () => error.value?.kind === 'api' && /不存在/.test(error.value.message || ''),
)
</script>

<template>
  <div class="pubb">
    <div class="pubb__inner">
      <RouterLink class="back" :to="{ name: 'home', query: backQuery }">
        <Icon name="arrowLeft" :size="13" />
        {{ fromCategoryTab ? '返回分类' : '全部文章' }}
      </RouterLink>

      <div v-if="loading" class="stack gap-5" style="padding: 40px 0">
        <div class="skeleton" style="height: 30px; width: 72%" />
        <div class="skeleton" style="width: 220px" />
        <div class="skeleton" style="height: 15px; margin-top: 20px" />
        <div class="skeleton" style="height: 15px; width: 92%" />
        <div class="skeleton" style="height: 15px; width: 84%" />
      </div>

      <div v-else-if="notFound" style="padding-top: 24px">
        <EmptyState
          icon="book"
          title="这篇文章不存在"
          desc="它可能被删掉了，也可能是别人的草稿 —— 草稿只有作者本人看得到。"
        >
          <template #action>
            <RouterLink class="btn btn--primary" :to="{ name: 'home', query: backQuery }">
              回到文章列表
            </RouterLink>
          </template>
        </EmptyState>
      </div>

      <ErrorNotice v-else-if="error" :error="error" @retry="load" />

      <article v-else class="art">
        <header class="art__head">
          <h1 class="art__title">{{ article.title || '无标题' }}</h1>
          <p class="art__meta">
            <span>{{ article.author }}</span>
            <span class="art__dot">·</span>
            <span class="num">{{ article.updateTime || article.createTime }}</span>
          </p>
        </header>

        <div v-if="isOwnDraft" class="banner banner--ok art__draft">
          <Icon name="info" :size="15" />
          <span class="grow">
            这一篇还是草稿，只有你自己看得到，别人打开会显示「文章不存在」。
          </span>
          <RouterLink
            v-if="auth.isLoggedIn"
            class="btn btn--default btn--sm banner__action"
            :to="{ name: 'article-edit', params: { id: article.id } }"
          >
            继续编辑
          </RouterLink>
        </div>

        <div v-if="article.content" class="prose art__body">{{ article.content }}</div>
        <p v-else class="c-3 art__body">这篇还没有正文。</p>

        <footer class="art__foot">
          <span class="c-3">写于 {{ article.createTime }}</span>
          <span class="grow" />
          <RouterLink class="btn btn--ghost btn--sm" :to="{ name: 'home', query: backQuery }">
            更多文章
            <Icon name="chevronRight" :size="13" />
          </RouterLink>
        </footer>
      </article>
    </div>
  </div>
</template>

<style scoped>
.pubb {
  padding: 0 24px 80px;
}

.pubb__inner {
  /* 阅读列适当加宽，正文行长仍保持在可读范围内 */
  max-width: 1050px;
  margin: 0 auto;
}

.back {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-top: 26px;
  font-size: 15px;
  color: var(--text-3);
  transition: color var(--t-fast) var(--ease);
}

.back:hover {
  color: var(--accent);
}

.art__head {
  padding: 26px 0 20px;
}

.art__title {
  font-size: clamp(24px, 4.4vw, 33px);
  font-weight: 600;
  letter-spacing: -0.035em;
  line-height: 1.22;
}

.art__meta {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-top: 14px;
  font-size: 15px;
  color: var(--text-3);
}

.art__dot {
  color: var(--text-4);
}

.art__draft {
  margin-bottom: 22px;
}

.art__body {
  padding-top: 4px;
  border-top: 1px solid var(--line);
  padding-top: 26px;
}

.art__foot {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 44px;
  padding-top: 16px;
  border-top: 1px solid var(--line);
  font-size: 13px;
}

@media (max-width: 620px) {
  .pubb {
    padding: 0 18px 60px;
  }
}
</style>