import { onScopeDispose, ref, watch, type Ref } from 'vue'

/**
 * 高频源节流镜像：SSE chunk 高频到达时只按固定间隔向下游发射，
 * markdown 渲染（marked + hljs 全量重渲）挂下游 computed，避免每 chunk 重算
 */
export function useThrottledRef<T>(source: Ref<T>, intervalMs = 16): Readonly<Ref<T>> {
  const throttled = ref(source.value) as Ref<T>
  let timer: number | null = null
  let pending: T | undefined
  let hasPending = false

  const stop = watch(source, (val) => {
    pending = val
    hasPending = true
    if (timer === null) {
      timer = window.setTimeout(() => {
        timer = null
        if (hasPending) {
          throttled.value = pending as T
          hasPending = false
        }
      }, intervalMs)
    }
  })

  onScopeDispose(() => {
    stop()
    if (timer !== null) window.clearTimeout(timer)
  })

  return throttled
}
