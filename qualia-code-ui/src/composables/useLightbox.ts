import { ref } from 'vue'

/** 图片灯箱全局状态：消息内图片点击全屏预览，点击任意处或 Esc 关闭 */
const currentImage = ref<string | null>(null)

export function useLightbox() {
  function open(src: string) {
    currentImage.value = src
  }
  function close() {
    currentImage.value = null
  }
  return { currentImage, open, close }
}
