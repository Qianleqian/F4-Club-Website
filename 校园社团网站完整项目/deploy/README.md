# 服务器部署准备

状态：Linux 运行包已准备，尚未连接或部署到远程服务器。当前尚未提供服务器地址、操作系统和 SSH 用户，以下采用 Ubuntu + systemd + Nginx 的布局作为待适配模板，不是已经执行的部署记录。

## 运行包

`campus-club-0.1.0.tar` 由 Gradle distTar 生成，包含 Linux 启动脚本和所有 Java 依赖。服务器只需要兼容的 JDK 17 或以上版本，不需要安装 Kotlin 或 Gradle，也不需要在云端下载构建依赖。

它没有打包本机业务数据、演示预览数据或 JDK。Linux 和 Windows 路径大小写不同，请保持包内目录结构。

## 拟部署布局

```text
浏览器 → Nginx 80/443 → Ktor 127.0.0.1:8080 → 本地 JSON

/opt/campus-club/              程序与依赖
/var/lib/campus-club/          独立的持久化数据目录
/etc/systemd/system/campus-club.service
```

使用独立 campus 系统账号运行程序，授予其数据目录写入权限。更新程序时保留数据目录。安装配置前检查服务器上是否已有站点或同名服务，避免覆盖。

## 得到服务器信息后的步骤

1. 通过 SSH 确认系统、已有 Java、可用内存、端口及站点，核对首次连接的主机指纹。
2. 上传运行包，安装或确认 JDK，解压到程序目录。
3. 创建运行账号和数据目录，安装并检查 systemd 服务。
4. 仅在服务器本机测试 `/api/health`，然后测试登录、审核、报名及数据持久化。
5. 处理公开的演示账号和密码后，再接入公网入口；不把公开的 demo1234 当作实际账号密码。
6. 将 Nginx 示例中的占位符替换为真实 IP 或域名，并按已有站点情况选择配置位置。
7. 配置云防火墙中实际需要的网站端口；Ktor 的 8080 保持仅本机监听。
8. 有域名和证书时配置 HTTPS；中国内地网站公开提供服务前按阿里云要求办理备案。
9. 从其他网络检查访问，验证成功后交付实际可访问地址。

本模板不会自行修改防火墙、覆盖默认站点、安装软件或开放公网端口。远程部署时按目标服务器的实际情况执行。

## 需要提供的信息

- 已购买还是尚未购买。
- 公网 IP、服务器系统和 SSH 用户名。
- 已有 SSH 密钥或控制台连接方式；不要将密码或私钥粘贴到聊天。
- 是否已有域名，服务器所在地域，以及服务器上是否已有其他网站。

## 官方参考

- [Gradle Application 插件与运行包](https://docs.gradle.org/current/userguide/application_plugin.html)
- [阿里云轻量应用服务器防火墙](https://help.aliyun.com/zh/simple-application-server/user-guide/manage-the-firewall-of-a-server)
- [阿里云服务器使用及备案说明](https://www.alibabacloud.com/help/en/simple-application-server/product-overview/usage-notes)
