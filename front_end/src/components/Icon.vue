<script setup>
/**
 * 内联图标集。线条统一 1.6px、16×16 视窗、圆角端点——
 * 用 emoji 或者彩色图标包会立刻让界面变廉价，所以这里手写一套。
 */
const props = defineProps({
  name: { type: String, required: true },
  size: { type: [Number, String], default: 16 },
  stroke: { type: [Number, String], default: 1.6 },
})

const P = {
  gauge: 'M2.5 12.5a7 7 0 1 1 11 0M8 8.6l3-2.1',
  doc: 'M4 2.5h5l3 3v8H4zM9 2.5v3h3M5.8 8.5h4.4M5.8 11h3.2',
  docs: 'M5.5 2.5h4l2.5 2.5v6h-6.5zM5.5 11v2.5h6.5V6M7 5.5h2M7 7.8h1.5',
  folder: 'M2.5 4.5h4l1.2 1.6h5.8v7.4h-11z',
  users: 'M6 8a2.3 2.3 0 1 0 0-4.6A2.3 2.3 0 0 0 6 8zM2 13.2c0-2.1 1.8-3.4 4-3.4s4 1.3 4 3.4M11 4a2 2 0 0 1 0 3.9M12 9.9c1.4.4 2.3 1.4 2.3 3',
  gear: 'M8 10a2 2 0 1 0 0-4 2 2 0 0 0 0 4zM8 1.8l.9 1.4 1.6-.4.5 1.6 1.6.5-.4 1.6 1.4.9-1.4.9.4 1.6-1.6.5-.5 1.6-1.6-.4-.9 1.4-.9-1.4-1.6.4-.5-1.6-1.6-.5.4-1.6L1.4 8l1.4-.9-.4-1.6 1.6-.5.5-1.6 1.6.4z',
  plus: 'M8 3.4v9.2M3.4 8h9.2',
  search: 'M7.2 12a4.8 4.8 0 1 0 0-9.6 4.8 4.8 0 0 0 0 9.6zM10.8 10.8 14 14',
  chevronLeft: 'M9.8 3.6 5.4 8l4.4 4.4',
  chevronRight: 'M6.2 3.6 10.6 8l-4.4 4.4',
  chevronDown: 'M3.6 6.2 8 10.6l4.4-4.4',
  x: 'M3.8 3.8l8.4 8.4M12.2 3.8l-8.4 8.4',
  check: 'M3.4 8.4l3 3 6.2-6.8',
  more: 'M4 8h.01M8 8h.01M12 8h.01',
  trash: 'M3 4.6h10M6.4 4.6V3.2h3.2v1.4M4.4 4.6l.5 8.4h6.2l.5-8.4M6.6 7v3.6M9.4 7v3.6',
  edit: 'M10.6 2.8l2.6 2.6-7.6 7.6-3.2.6.6-3.2z',
  external: 'M9.4 2.8h3.8v3.8M13.2 2.8 7.6 8.4M11.6 9.4v3.8h-9v-9h3.8',
  alert: 'M8 2.6 14 13H2zM8 6.4v3.2M8 11.4h.01',
  info: 'M8 14A6 6 0 1 0 8 2a6 6 0 0 0 0 12zM8 7.4v3.4M8 5.3h.01',
  refresh: 'M13.4 8a5.4 5.4 0 1 1-1.6-3.8M13.6 2.6v3.2h-3.2',
  logout: 'M6 13.8H3.2V2.2H6M10 4.6 13.4 8 10 11.4M6.6 8h6.8',
  collapse: 'M2.6 2.6h10.8v10.8H2.6zM6.4 2.6v10.8',
  arrowLeft: 'M12.6 8H3.4M7 3.6 2.6 8 7 12.4',
  arrowUpRight: 'M4.6 11.4 11.4 4.6M6 4.6h5.4v5.4',
  clock: 'M8 14A6 6 0 1 0 8 2a6 6 0 0 0 0 12zM8 4.8V8l2.4 1.6',
  spark: 'M8 2.4l1.5 3.9 3.9 1.5-3.9 1.5L8 13.2l-1.5-3.9L2.6 7.8l3.9-1.5z',
  filter: 'M2.6 3.6h10.8l-4.2 4.6v4.6l-2.4 1.2V8.2z',
  mail: 'M2.4 3.4h11.2v9.2H2.4zM2.4 4.2 8 8.6l5.6-4.4',
  lock: 'M4.4 7.4h7.2v6.2H4.4zM6 7.4V5.2a2 2 0 0 1 4 0v2.2',
  user: 'M8 8.4a2.6 2.6 0 1 0 0-5.2 2.6 2.6 0 0 0 0 5.2zM3.2 13.8c0-2.4 2.1-3.9 4.8-3.9s4.8 1.5 4.8 3.9',
  book: 'M2.6 3.2h4.2c.7 0 1.2.6 1.2 1.2v8.4c0-.6-.5-1.2-1.2-1.2H2.6zM13.4 3.2H9.2c-.7 0-1.2.6-1.2 1.2v8.4c0-.6.5-1.2 1.2-1.2h4.2z',
  inbox: 'M2.4 9.2h3l.8 1.6h3.6l.8-1.6h3M2.4 9.2 4 3.2h8l1.6 6v3.6H2.4z',
  send: 'M13.8 2.2 2.4 6.8l4.4 1.6 1.6 4.4zM13.8 2.2 6.8 8.4',
  eye: 'M1.6 8S4 4 8 4s6.4 4 6.4 4-2.4 4-6.4 4-6.4-4-6.4-4zM8 9.8a1.8 1.8 0 1 0 0-3.6 1.8 1.8 0 0 0 0 3.6z',
  layers: 'M8 2.4 1.8 5.6 8 8.8l6.2-3.2zM1.8 8.8 8 12l6.2-3.2M1.8 11.4 8 14.6l6.2-3.2',
  pen: 'M8.6 3.2 12.8 7.4M3 13l.7-3.4 6.7-6.7 2.7 2.7-6.7 6.7z',
}
</script>

<template>
  <svg
    :width="props.size"
    :height="props.size"
    viewBox="0 0 16 16"
    fill="none"
    :stroke-width="props.stroke"
    stroke="currentColor"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    focusable="false"
  >
    <path :d="P[props.name] || ''" />
  </svg>
</template>