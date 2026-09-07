# food-life-agent-web

面向用户的美食生活前端应用，使用 Vue 3、TypeScript、Vite、Vue Router、Pinia、Axios、Element Plus。

前端通过 Gateway 联调后端，不直接绕过网关调用单个微服务。

## 本地运行

先在项目根目录启动基础设施和后端：

```powershell
cd ..
.\scripts\start-infra-all.ps1
.\scripts\start-local-services.ps1 -IncludeGateway -Restart
```

启动前端：

```powershell
npm install
npm run dev
```

访问：

```text
http://localhost:5173
```

## 代理规则

```text
/user-api      -> http://localhost:8080/api/user
/business-api  -> http://localhost:8080/api
/trade-api     -> http://localhost:8080/api/trade
```

## 构建

```powershell
npm run build
```
