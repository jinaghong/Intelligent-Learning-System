import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'

// ElementPlusResolver 默认从 element-plus/es 桶文件导入，会把全部组件的 props
// 拉进模块图（产物里出现一堆没人引用的组件 chunk）。这里改成按组件子路径导入。
function ElementPlusSubpathResolver() {
  return {
    type: 'component',
    resolve(name) {
      if (!name.startsWith('El')) return undefined
      const partial = name
        .slice(2)
        .replace(/([a-z0-9])([A-Z])/g, '$1-$2')
        .replace(/([A-Z])([A-Z][a-z])/g, '$1-$2')
        .toLowerCase()
      // 子组件源码都挂在父组件目录下（main/header 在 container，option 在 select……）
      const grouped = {
        main: 'container',
        header: 'container',
        aside: 'container',
        footer: 'container',
        'anchor-link': 'anchor',
        'avatar-group': 'avatar',
        'breadcrumb-item': 'breadcrumb',
        'button-group': 'button',
        'carousel-item': 'carousel',
        'checkbox-button': 'checkbox',
        'checkbox-group': 'checkbox',
        'collapse-item': 'collapse',
        'descriptions-item': 'descriptions',
        'dropdown-item': 'dropdown',
        'dropdown-menu': 'dropdown',
        'form-item': 'form',
        'menu-item': 'menu',
        'menu-item-group': 'menu',
        'sub-menu': 'menu',
        option: 'select',
        'option-group': 'select',
        'radio-button': 'radio',
        'radio-group': 'radio',
        'skeleton-item': 'skeleton',
        'splitter-panel': 'splitter',
        step: 'steps',
        'tab-pane': 'tabs',
        'table-column': 'table',
        'timeline-item': 'timeline',
        'tour-step': 'tour'
      }
      const folder = grouped[partial] || partial
      return {
        name,
        from: `element-plus/es/components/${folder}/index`,
        // 子组件有独立样式（如 el-breadcrumb-item.css），父目录样式并不包含它
        sideEffects: [
          ...new Set([
            `element-plus/es/components/${partial}/style/css`,
            `element-plus/es/components/${folder}/style/css`
          ])
        ]
      }
    }
  }
}

export default defineConfig({
  plugins: [
    vue(),
    Components({
      resolvers: [ElementPlusSubpathResolver()],
      dts: false
    })
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  preview: {
    port: 4173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    // 不手工按组件拆包：Element Plus 内部有大量循环引用（focus-trap / popper / dialog），
    // 强行拆成独立 chunk 会在生产环境打乱初始化顺序（实测 TypeError）。
    // 路由级动态 import 已经负责页面拆分，这里交给 Rollup 默认策略。
    chunkSizeWarningLimit: 1200
  }
})
