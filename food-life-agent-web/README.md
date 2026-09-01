# FoodLife 美食拼团 Agent Web

面向用户的美食生活前端应用，使用 Vue 3、TypeScript、Vite、Vue Router、Pinia、Axios、Element Plus。

## 本地运行

先启动后端三服务：

```powershell
cd ..
.\scripts\start-local-services.ps1
```

启动前端：

```powershell
npm install
npm run dev
```

默认地址：

```text
http://localhost:5173
```

## 后端代理

```text
/user-api      -> http://localhost:8101/api/user
/business-api  -> http://localhost:8201/api
/trade-api     -> http://localhost:8301/api/trade
```

后续接入微服务网关后，可以把这些代理统一切到 gateway。

This template should help get you started developing with Vue 3 and TypeScript in Vite. The template uses Vue 3 `<script setup>` SFCs, check out the [script setup docs](https://v3.vuejs.org/api/sfc-script-setup.html#sfc-script-setup) to learn more.

Learn more about the recommended Project Setup and IDE Support in the [Vue Docs TypeScript Guide](https://vuejs.org/guide/typescript/overview.html#project-setup).
