<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/Icon.vue'
import ErrorNotice from '@/components/ErrorNotice.vue'
import { userApi } from '@/api'
import { useAuth } from '@/stores/auth'
import { useToast } from '@/composables/toast'

/**
 * 设置。只做两件真能做成的事：看自己的账号信息、改密码。
 * 昵称改不了 —— 后端没有更新用户资料的接口，所以这里不给输入框。
 */

const auth = useAuth()
const router = useRouter()
const toast = useToast()

const loading = ref(true)
const error = ref(null)
const user = ref(null)

const form = reactive({ password: '', confirm: '' })
const saving = ref(false)
const fieldError = reactive({ password: '', confirm: '' })

const roleLabel = computed(() => (user.value?.roleId === 1 ? '管理员' : '作者'))

async function load() {
  loading.value = true
  error.value = null
  try {
    user.value = await userApi.info()
  } catch (e) {
    error.value = e
  } finally {
    loading.value = false
  }
}

onMounted(load)

function validate() {
  fieldError.password = ''
  fieldError.confirm = ''
  if (!form.password) {
    fieldError.password = '新密码不能为空'
  } else if (form.password.length < 6) {
    fieldError.password = '密码长度不能少于 6 位'
  }
  if (form.confirm !== form.password) {
    fieldError.confirm = '两次输入不一致'
  }
  return !fieldError.password && !fieldError.confirm
}

async function save() {
  if (!validate()) return
  saving.value = true
  try {
    await userApi.resetPassword(user.value.userName, form.password)
    toast.ok('密码已更新')
    form.password = ''
    form.confirm = ''
  } catch (e) {
    toast.error(e.message)
  } finally {
    saving.value = false
  }
}

async function doLogout() {
  await auth.logout()
  router.push({ name: 'home' })
}
</script>

<template>
  <div class="page page--narrow">
    <div class="page__inner">
      <div class="page__head">
        <div class="page__head-main">
          <h1 class="page__title">设置</h1>
          <p class="page__desc">当前登录账号的信息，以及改密码。</p>
        </div>
      </div>

      <ErrorNotice v-if="error" :error="error" @retry="load" />

      <div v-else class="stack gap-4">
        <section class="panel">
          <div class="panel__head">
            <h2 class="panel__title">账号</h2>
            <span class="grow" />
            <span
              class="badge"
              :class="user?.roleId === 1 ? 'badge--admin' : 'badge--role'"
            >
              {{ roleLabel }}
            </span>
          </div>

          <div v-if="loading" class="panel__body stack gap-4">
            <div v-for="i in 5" :key="i" class="skeleton" style="height: 16px" />
          </div>

          <div v-else class="panel__body">
            <dl class="def-list">
              <dt>用户名</dt>
              <dd>{{ user?.userName }}</dd>
              <dt>昵称</dt>
              <dd>
                {{ user?.nickname || '—' }}
                <span class="t-xs c-3" style="display: block; margin-top: 2px">
                  昵称暂时改不了。
                </span>
              </dd>
              <dt>角色</dt>
              <dd>{{ roleLabel }}</dd>
              <dt>注册</dt>
              <dd class="num">{{ user?.createTime || '—' }}</dd>
              <dt>最近更新</dt>
              <dd class="num">{{ user?.updateTime || '—' }}</dd>
              <dt>用户 ID</dt>
              <dd class="t-mono">{{ user?.userId }}</dd>
            </dl>
          </div>

          <div class="panel__foot row gap-2">
            <span class="t-xs c-3">
              退出后当前登录凭证会立刻失效，需要重新登录。
            </span>
            <span class="grow" />
            <button class="btn btn--default btn--sm" type="button" @click="doLogout">
              <Icon name="logout" :size="13" />
              退出登录
            </button>
          </div>
        </section>

        <section class="panel">
          <div class="panel__head">
            <h2 class="panel__title">改密码</h2>
            <span class="grow" />
            <Icon name="lock" :size="15" style="color: var(--text-4)" />
          </div>

          <form class="panel__body stack gap-4" @submit.prevent="save">
            <div class="field">
              <label class="field__label" for="np">新密码</label>
              <input
                id="np"
                v-model="form.password"
                class="input"
                type="password"
                autocomplete="new-password"
                placeholder="至少 6 位"
                :aria-invalid="Boolean(fieldError.password)"
              />
              <p v-if="fieldError.password" class="field__error">{{ fieldError.password }}</p>
            </div>

            <div class="field">
              <label class="field__label" for="np2">再输一次</label>
              <input
                id="np2"
                v-model="form.confirm"
                class="input"
                type="password"
                autocomplete="new-password"
                placeholder="两次要一致"
                :aria-invalid="Boolean(fieldError.confirm)"
              />
              <p v-if="fieldError.confirm" class="field__error">{{ fieldError.confirm }}</p>
            </div>

            <div class="banner">
              <Icon name="info" :size="15" />
              <span>
                后端没有独立的改密接口，这里走的是找回密码那一支 ——
                不需要填当前密码。改完立刻生效，当前登录不会掉线。
              </span>
            </div>

            <div class="row gap-2">
              <button class="btn btn--primary" type="submit" :disabled="saving">
                {{ saving ? '提交中…' : '更新密码' }}
              </button>
              <span class="grow" />
            </div>
          </form>
        </section>
      </div>
    </div>
  </div>
</template>