<script setup>
/**
 * 品牌图标：黑色圆角方块 + 白色字母 B + 右上角橙色圆点。
 *
 * 它与浏览器标签页用的是同一份资源 `public/mark.svg`，
 * 所以侧栏 / 顶部导航 / 登录页看到的图标和 favicon 永远一致 ——
 * 以前这里是 `Icon name="book"`（一套手写的线条书图标），
 * 和标签页的 mark.svg 是两个完全不同的图形。
 *
 * 不要再在它外面套一个深色圆角底（以前的 .rail__mark 之类就是），
 * mark.svg 自带底色与圆角，套两层会变成双重底色。
 */
defineProps({
  /** 边长（px）。容器已定尺寸时可以用 CSS 覆盖。 */
  size: { type: [Number, String], default: 28 },
  /**
   * 是否纯装饰。默认 true：
   * 图标旁边始终有可见的 "BlogHub" 文字，读屏再念一遍品牌名是重复信息，
   * 所以默认 aria-hidden。真要独立使用（旁边没有文字）时传 :decorative="false"。
   */
  decorative: { type: Boolean, default: true },
})
</script>

<template>
  <img
    class="brand-mark"
    src="/mark.svg"
    :width="size"
    :height="size"
    :alt="decorative ? '' : 'BlogHub'"
    :aria-hidden="decorative ? 'true' : null"
    draggable="false"
  />
</template>

<style scoped>
.brand-mark {
  display: block;
  flex: none;
  /* width/height 属性给了内联尺寸，这里允许被容器 CSS 覆盖 */
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  /* 圆角要跟着尺寸走：mark.svg 内部圆角是 7/32 = 21.875%。
     之前写死成 --r-sm(5px)，小尺寸下图片自身的圆角比元素大，
     阴影就会在圆角外露出方形拐角（120px 下肉眼可见）。 */
  border-radius: 21.875%;
  user-select: none;
  -webkit-user-drag: none;
}
</style>
