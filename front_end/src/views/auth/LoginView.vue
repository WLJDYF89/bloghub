<script setup>
import { computed, reactive, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import Icon from '@/components/Icon.vue'
import BrandMark from '@/components/BrandMark.vue'
import { userApi } from '@/api'
import { useAuth } from '@/stores/auth'
import { useToast } from '@/composables/toast'

const route = useRoute()
const router = useRouter()
const auth = useAuth()
const toast = useToast()

const form = reactive({
  // 从「找回密码」回来时带上用户名，省得重打一遍
  userName: typeof route.query.userName === 'string' ? route.query.userName : '',
  password: '',
})
const submitting = ref(false)
const fieldError = reactive({ userName: '', password: '' })
const serverError = ref('')

const redirectTarget = computed(() => {
  const raw = route.query.redirect
  if (typeof raw === 'string' && raw.startsWith('/')) return raw
  return '/console'
})

function validate() {
  fieldError.userName = ''
  fieldError.password = ''
  serverError.value = ''

  if (!form.userName.trim()) {
    fieldError.userName = '用户名不能为空'
  }
  if (!form.password) {
    fieldError.password = '密码不能为空'
  }

  return !fieldError.userName && !fieldError.password
}

async function submit() {
  if (!validate()) return

  submitting.value = true
  try {
    await auth.login(form.userName.trim(), form.password)
    toast.ok('登录成功')
    await router.replace(redirectTarget.value)
  } catch (e) {
    serverError.value = e?.message || '登录失败'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="auth-shell">
    <div class="auth-card">
      <div class="auth-card__intro">
        <div class="auth-brand">
          <span class="auth-brand__mark"><BrandMark :size="28" /></span>
          <span>BlogHub</span>
        </div>
        <h1 class="auth-card__title">写作者入口</h1>
        <p class="auth-card__desc">登录后继续写文章、管理分类和查看全部内容。</p>
      </div>

      <form class="auth-form" @submit.prevent="submit">
        <div v-if="serverError" class="banner banner--error">
          <Icon name="alert" :size="15" />
          <span>{{ serverError }}</span>
        </div>

        <div class="field">
          <label class="field__label" for="login-user">用户名</label>
          <input
            id="login-user"
            v-model="form.userName"
            class="input"
            type="text"
            autocomplete="username"
            placeholder="输入用户名"
            :aria-invalid="Boolean(fieldError.userName)"
          />
          <p v-if="fieldError.userName" class="field__error">{{ fieldError.userName }}</p>
        </div>

        <div class="field">
          <label class="field__label" for="login-password">密码</label>
          <input
            id="login-password"
            v-model="form.password"
            class="input"
            type="password"
            autocomplete="current-password"
            placeholder="输入密码"
            :aria-invalid="Boolean(fieldError.password)"
          />
          <p v-if="fieldError.password" class="field__error">{{ fieldError.password }}</p>
          <RouterLink class="link link--sm field__link" :to="{ name: 'forgot' }">忘记密码？</RouterLink>
        </div>

        <button class="btn btn--primary btn--block" :disabled="submitting" type="submit">
          {{ submitting ? '登录中…' : '登录' }}
        </button>

        <div class="auth-form__row">
          <RouterLink class="link" :to="{ name: 'register' }">没有账号？去注册</RouterLink>
          <RouterLink class="link" :to="{ name: 'home' }">返回首页</RouterLink>
        </div>
      </form>
    </div>
  </div>
</template>

<style scoped>
.auth-shell {
  min-height: 100dvh;
  display: grid;
  place-items: center;
  padding: 32px 16px;
  background: radial-gradient(circle at top, rgba(196, 98, 31, 0.09), transparent 34%), var(--bg);
}

.auth-card {
  width: min(100%, 420px);
  background: rgba(253, 252, 249, 0.86);
  border: 1px solid var(--line);
  border-radius: var(--r-xl);
  box-shadow: var(--shadow-3);
  overflow: hidden;
}

.auth-card__intro {
  padding: 26px 26px 18px;
  border-bottom: 1px solid var(--line);
  background: linear-gradient(180deg, rgba(17, 14, 11, 0.02), transparent);
}

.auth-brand {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  letter-spacing: -0.02em;
}

/* 底色/圆角由 BrandMark 里的 mark.svg 自带，这里只定尺寸 */
.auth-brand__mark {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
}

.auth-card__title {
  margin-top: 18px;
  font-size: 30px;
  letter-spacing: -0.04em;
}

.auth-card__desc {
  margin-top: 8px;
  font-size: 15px;
  color: var(--text-2);
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 22px 26px 26px;
}

.auth-form__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: 13px;
}

/* 密码输入框下面的找回入口，靠右对齐 */
.field__link {
  align-self: flex-end;
}

.link {
  color: var(--text-2);
  transition: color var(--t-fast) var(--ease);
}

.link--sm {
  font-size: 13px;
}

.link:hover {
  color: var(--accent);
}
</style>