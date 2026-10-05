<script setup>
import { computed, onMounted, ref } from 'vue'
import Icon from '@/components/Icon.vue'
import Pager from '@/components/Pager.vue'
import EmptyState from '@/components/EmptyState.vue'
import ErrorNotice from '@/components/ErrorNotice.vue'
import UserCell from '@/components/UserCell.vue'
import { adminApi } from '@/api'
import { formatDate } from '@/utils/format'

/**
 * 用户（管理员）—— 只读。
 *
 * GET /admin/user/list 全量返回、没有分页，所以搜索和翻页都在本地做。
 * 后端没有用户增删改、没有改角色、没有禁用，所以这里不放任何这类按钮：
 * 一个按下去必然报错的按钮，比没有按钮更糟。
 */

const users = ref([])
const loading = ref(true)
const error = ref(null)
const keyword = ref('')
const pageNum = ref(1)
const pageSize = 12

async function load() {
  loading.value = true
  error.value = null
  try {
    users.value = (await adminApi.users()) ?? []
  } catch (e) {
    error.value = e
    users.value = []
  } finally {
    loading.value = false
  }
}

onMounted(load)

const filtered = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  if (!k) return users.value
  return users.value.filter(
    (u) =>
      String(u.userName || '').toLowerCase().includes(k) ||
      String(u.nickname || '').toLowerCase().includes(k),
  )
})

const totalPages = computed(() => Math.ceil(filtered.value.length / pageSize))

const paged = computed(() => {
  const start = (pageNum.value - 1) * pageSize
  return filtered.value.slice(start, start + pageSize)
})

const adminCount = computed(() => users.value.filter((u) => u.roleId === 1).length)

function search() {
  pageNum.value = 1
}

function clearSearch() {
  keyword.value = ''
  search()
}

function goPage(n) {
  if (n < 1 || (totalPages.value && n > totalPages.value)) return
  pageNum.value = n
}
</script>

<template>
  <div class="page">
    <div class="page__inner">
      <div class="page__head">
        <div class="page__head-main">
          <h1 class="page__title">用户</h1>
          <p class="page__desc">
            全站 {{ users.length }} 人，其中 {{ adminCount }} 位管理员。搜索和翻页都在本地完成。
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

      <section v-else class="panel">
        <div class="toolbar">
          <label class="search" style="flex: 1; min-width: 180px; max-width: 300px">
            <Icon name="search" :size="14" />
            <input
              v-model="keyword"
              class="input"
              placeholder="搜用户名或昵称…"
              @input="search"
            />
          </label>
          <span class="grow" />
          <span class="panel__sub num">显示 {{ paged.length }} / {{ filtered.length }}</span>
        </div>

        <div class="banner" style="margin: 14px 16px 0">
          <Icon name="info" :size="15" />
          <span>
            这份名单是只读的。后端还没开放用户管理能力 ——
            改角色、禁用账号都做不到，所以这里没有对应的按钮。
          </span>
        </div>

        <div v-if="loading" class="panel__body stack gap-4">
          <div v-for="i in 6" :key="i" class="skeleton" style="height: 18px" />
        </div>

        <EmptyState
          v-else-if="!filtered.length"
          :icon="keyword ? 'search' : 'users'"
          :title="keyword ? '没有匹配的用户' : '还没有用户'"
          :desc="keyword ? '换个用户名或昵称试试。' : '第一个注册的人会出现在这里。'"
        >
          <template #action>
            <button v-if="keyword" class="btn btn--default" type="button" @click="clearSearch">
              清除搜索
            </button>
          </template>
        </EmptyState>

        <template v-else>
          <div class="table-wrap" style="margin-top: 14px">
            <table class="table">
              <thead>
                <tr>
                  <th>用户</th>
                  <th style="width: 120px">角色</th>
                  <th style="width: 110px">状态</th>
                  <th style="width: 140px">注册时间</th>
                  <th style="width: 90px" class="col-num">ID</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="u in paged" :key="u.userId">
                  <td>
                    <UserCell
                      :name="u.nickname || u.userName"
                      :sub="u.nickname ? u.userName : ''"
                      :admin="u.roleId === 1"
                    />
                  </td>
                  <td>
                    <span class="badge" :class="u.roleId === 1 ? 'badge--admin' : 'badge--role'">
                      {{ u.roleId === 1 ? '管理员' : '作者' }}
                    </span>
                  </td>
                  <td>
                    <span class="status" :class="u.status === 1 ? 'status--live' : 'status--draft'">
                      <i class="status__dot" />
                      {{ u.status === 1 ? '正常' : '已禁用' }}
                    </span>
                  </td>
                  <td class="num c-3">{{ formatDate(u.createTime) }}</td>
                  <td class="col-num t-mono c-3">{{ u.userId }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <Pager
            :page="pageNum"
            :page-size="pageSize"
            :total="filtered.length"
            :total-pages="totalPages"
            @go="goPage"
          />
        </template>
      </section>
    </div>
  </div>
</template>