import fs from 'node:fs'
import ts from 'typescript'
import { effectScope, ref, reactive, computed, watch, nextTick } from 'vue'

// Execute the actual setup/TS source with browser and API boundaries supplied by each test.
export function loadSource(filename, bindings, exports) {
  let source = fs.readFileSync(new URL(`../src/${filename}`, import.meta.url), 'utf8')
  if (filename.endsWith('.vue')) source = source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1]
  source = source.replace(/^import\b[\s\S]*?\bfrom\s+['"][^'"]+['"]\s*\r?\n/gm, '')
    .replace(/^export\s+default\s+\w+\s*;?\s*$/gm, '')
    .replace(/^export\s+(?=(?:async\s+)?(?:const|function|interface|type)\b)/gm, '')
  const javascript = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.None } }).outputText
  const scope = effectScope()
  const hooks = { mounted: [], unmount: [] }
  const supplied = { ref, reactive, computed, watch, defineProps: () => ({}),
    onMounted: fn => hooks.mounted.push(fn), onBeforeUnmount: fn => hooks.unmount.push(fn), onUnmounted: fn => hooks.unmount.push(fn), ...bindings }
  const result = scope.run(() => new Function(...Object.keys(supplied), `${javascript}\nreturn {${exports.join(',')}}`)(...Object.values(supplied)))
  return { ...result, hooks, dispose() { hooks.unmount.forEach(fn => fn()); scope.stop() } }
}

export function storage() {
  const data = new Map()
  return { getItem: key => data.get(key) ?? null, setItem: (key, value) => data.set(key, String(value)), removeItem: key => data.delete(key) }
}

export { reactive, nextTick }
