# 开发日志-056-Nacos免Docker本地服务端

## 1. 本次目标

本次补齐免 Docker 的 Nacos Server 本地运行方式。

前面已经完成：

```text
1. 三个微服务客户端接入 Nacos discovery/config 依赖
2. 三个微服务新增 bootstrap.yml
3. 新增 Nacos 模式启动脚本 start-local-services-nacos.ps1
```

但本机没有 Docker，也没有 Nacos Server，因此还不能真正启动注册中心。

本次新增本地压缩包方式：

```text
下载 nacos-server zip
解压到 tools/nacos-server/nacos
使用 startup.cmd -m standalone 启动
```

## 2. 新增脚本

新增：

```text
scripts/install-nacos-server.ps1
scripts/start-nacos-server.ps1
scripts/stop-nacos-server.ps1
scripts/check-nacos.ps1
```

## 3. 安装 Nacos Server

默认版本：

```text
Nacos 2.2.3
```

默认安装目录：

```text
tools/nacos-server/nacos
```

执行：

```powershell
.\scripts\install-nacos-server.ps1
```

如果网络能正常访问 GitHub Releases，脚本会自动下载：

```text
https://github.com/alibaba/nacos/releases/download/2.2.3/nacos-server-2.2.3.zip
```

## 4. 国内网络较慢时的手动安装方式

如果自动下载太慢，可以手动下载：

```text
nacos-server-2.2.3.zip
```

然后执行：

```powershell
.\scripts\install-nacos-server.ps1 -LocalZip "D:\downloads\nacos-server-2.2.3.zip"
```

脚本会把本地 zip 复制到：

```text
.cache/nacos/nacos-server-2.2.3.zip
```

并解压到：

```text
tools/nacos-server/nacos
```

## 5. 启动 Nacos

执行：

```powershell
.\scripts\start-nacos-server.ps1
```

脚本内部等价于：

```powershell
startup.cmd -m standalone
```

启动成功后控制台地址：

```text
http://127.0.0.1:8848/nacos
```

默认账号密码：

```text
nacos / nacos
```

## 6. 检查 Nacos

执行：

```powershell
.\scripts\check-nacos.ps1
```

检查内容：

```text
1. 8848 端口是否监听
2. /nacos 控制台是否可访问
3. 输出 Nacos 控制台地址
```

## 7. 停止 Nacos

执行：

```powershell
.\scripts\stop-nacos-server.ps1
```

优先调用：

```powershell
shutdown.cmd
```

如果 8848 端口仍然监听，则按端口找到进程并停止。

## 8. 启动微服务注册到 Nacos

Nacos 启动后，再执行：

```powershell
.\scripts\start-local-services-nacos.ps1 -Rebuild -Restart
```

这个脚本会打开：

```text
NACOS_DISCOVERY_ENABLED=true
NACOS_CONFIG_ENABLED=true
```

三个服务会尝试注册到：

```text
127.0.0.1:8848
```

## 9. 本次实测情况

本机环境：

```text
Java 11 可用
Docker 不可用
8848 未监听
项目内原本没有 Nacos Server
```

实际尝试从 GitHub Releases 下载 Nacos 2.2.3，但网络吞吐过慢：

```text
下载速度约几十 KB/s
完整包约 142MB
预计需要约 1 小时
```

因此没有继续等待完整下载。本次已提供可运行脚本和手动 zip 安装入口。

## 10. 面试点对应

本次可以讲：

```text
1. Nacos Server 和 Nacos Client 的区别
2. 为什么客户端配置好了不代表注册中心已经运行
3. standalone 模式适合本地开发
4. 生产环境 Nacos 应该集群部署
5. 8848、9848、9849 端口的作用
6. 为什么 tools/ 和 .cache/ 不应该提交到 Git
```

## 11. 下一步

拿到完整 Nacos zip 后，建议验证：

```text
1. start-nacos-server.ps1 启动 Nacos
2. check-nacos.ps1 检查控制台
3. start-local-services-nacos.ps1 启动三个微服务
4. 在 Nacos 控制台查看服务实例
5. 继续验证 Feign 服务名调用
```
