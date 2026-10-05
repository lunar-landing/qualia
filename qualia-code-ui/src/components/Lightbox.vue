<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'
import { useLightbox } from '@/composables/useLightbox'

const { currentImage, close } = useLightbox()

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && currentImage.value) close()
}

onMounted(() => document.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => document.removeEventListener('keydown', onKeydown))
</script>

<template>
  <Teleport to="body">
    <div v-show="currentImage" class="lightbox open" @click="close">
      <img :src="currentImage ?? ''" alt="" />
    </div>
  </Teleport>
</template>
