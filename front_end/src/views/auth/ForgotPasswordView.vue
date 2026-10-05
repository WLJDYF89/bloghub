<script setup>
import { reactive, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import Icon from '@/components/Icon.vue'
import BrandMark from '@/components/BrandMark.vue'
import { userApi } from '@/api'
import { useToast } from '@/composables/toast'

const route = useRoute()
const router = useRouter()
const toast = useToast()

// 从登录页带过来的用户名直接填好，少打一次字
const form = reactive({
  userName: typeof route.query.userName === 'string' ? route.query.userName : '',
  password: '',
  confirm: '',
})
const submitting = ref(false)
const fieldError = reactive({ userName: '', password: '', confirm: '' })
const serverError = ref('')

function validate() {
  fieldError.userName = ''
  fieldError.password = ''
  fieldError.confirm = ''
  serverError.value = ''

  if (!form.userName.trim()) {
    fieldError.userName = '用户名不能为空'
  }

  if (!form.password) {
    fieldError.password = '新密码不能为空'
  } else if (form.password.length < 6) {
    fieldError.password = '密码至少 6 位'
  }

  if (!form.confirm) {
    fieldError.confirm = '请确认新密码'
  } else if (form.confirm !== form.password) {
    fieldError.confirm = '两次输入不一致'
  }

  return !fieldError.userName && !fieldError.password && !fieldError.confirm
}

async function submit() {
  if (!validate()) return

  const userName = form.userName.trim()
  submitting.value = true
  try {
    await userApi.resetPassword(userName, form.password)
    toast.ok('密码已重置，请用新密码登录')
    await router.replace({ name: 'login', query: { userName } })
  } catch (e) {
    serverError.value = e?.message || '重置密码失败'
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
        <h1 class="auth-card__title">找回密码</h1>
        <p class="auth-card__desc">输入用户名并设置一个新密码，重置后即可直接登录。</p>
      </div>

      <form class="auth-form" @submit.prevent="submit">
        <div v-if="serverError" class="banner banner--error">
          <Icon name="alert" :size="15" />
          <span>{{ serverError }}</span>
        </div>

        <div class="field">
          <label class="field__label" for="forgot-user">用户名</label>
          <input
            id="forgot-user"
            v-model="form.userName"
            class="input"
            type="text"
            autocomplete="username"
            placeholder="注册时使用的用户名"
            :aria-invalid="Boolean(fieldError.userName)"
          />
          <p v-if="fieldError.userName" class="field__error">{{ fieldError.userName }}</p>
        </div>

        <div class="field">
          <label class="field__label" for="forgot-password">新密码</label>
          <input
            id="forgot-password"
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
          <label class="field__label" for="forgot-confirm">确认新密码</label>
          <input
            id="forgot-confirm"
            v-model="form.confirm"
            class="input"
            type="password"
            autocomplete="new-password"
            placeholder="再次输入新密码"
            :aria-invalid="Boolean(fieldError.confirm)"
          />
          <p v-if="fieldError.confirm" class="field__error">{{ fieldError.confirm }}</p>
        </div>

        <button class="btn btn--primary btn--block" :disabled="submitting" type="submit">
          {{ submitting ? '重置中…' : '重置密码' }}
        </button>

        <div class="auth-form__row">
          <RouterLink class="link" :to="{ name: 'login' }">想起密码了？去登录</RouterLink>
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

.link {
  color: var(--text-2);
  transition: color var(--t-fast) var(--ease);
}

.link:hover {
  color: var(--accent);
}
</style>