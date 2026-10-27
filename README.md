# 集会 Campus Clubs

校园社团活动发布与报名网站，Kotlin/Ktor 本地开发基础版 v0.1。

## 四人协作

代码位于仓库根目录。`main` 为基础版，四位成员分别使用 `feature/student`、`feature/club`、`feature/teacher`、`feature/admin`。每条分支均含完整项目。详细分工、运行和 PR 流程见 [四人协作说明](docs/07-team-collaboration.md)。

## 当前可以演示的流程

社团账号提交活动，老师通过审核后活动公开，学生报名并查看记录，社团查看报名名单。管理员可以创建账号、分配角色、关联社团并启用或禁用账号。所有业务由 Kotlin 后端处理，刷新网页或重启服务器后业务数据仍保留。

这是用于继续开发的基础版本。它尚未完成原始项目说明中的全部功能，差异详见 [需求清单](docs/02-requirements.md)。

## 启动

需要 JDK 17 或更高版本及首次下载依赖所需的网络。项目带 Gradle Wrapper，无需自行安装 Gradle。

Windows：双击 `start-local.bat`。该脚本优先使用 JAVA_HOME，否则尝试查找用户 `.jdks` 中的 JDK。

也可以在 IntelliJ IDEA 中打开本文件所在文件夹，选择可用的 JDK 作为 Gradle JVM，同步项目后运行 `ApplicationKt`。

PowerShell 命令：

```powershell
# 若尚未配置 JAVA_HOME，将下一行改为实际 JDK 目录
$env:JAVA_HOME = 'C:\Users\Lenovo\.jdks\openjdk-26.0.2.1'
.\gradlew.bat run
```

浏览器访问 **http://127.0.0.1:8080**。保持启动窗口运行；Ctrl+C 停止服务器。若端口占用，先设置 `$env:PORT='8081'`，再启动并访问相应端口。

## 演示账号

| 角色 | 账号 | 密码 |
|---|---|---|
| 学生 | student | demo1234 |
| 社团工作人员 | club | demo1234 |
| 审核老师 | teacher | demo1234 |
| 管理员 | admin | demo1234 |

首次启动自动建立这四个账号、三个社团和一场已发布的示例活动。示例活动日期按首次启动时间生成。登录框提供填入演示账号的按钮。

## 项目结构

```text
src/main/kotlin/edu/campus/
  Application.kt               服务器入口与公共配置
  model/Models.kt              用户、活动、报名及请求数据模型
  repository/LocalRepository.kt 本地 JSON 读取、事务和文件保存
  security/Passwords.kt        密码哈希
  service/CampusService.kt     业务校验和权限规则
  routes/                      按四个角色拆分的接口
src/main/resources/static/
  index.html                   统一页面外壳与登录框
  app.js                       四个角色的页面和接口调用
  styles.css                   电脑和手机自适应样式
src/test/kotlin/edu/campus/     业务规则与 HTTP 流程测试
docs/                         规划、需求、架构、调查及协作模板
data/state.json               首次运行生成，不进入 Git
```

## 数据和技术边界

- 技术版本固定在 `build.gradle.kts` 与 `gradle/wrapper/gradle-wrapper.properties`。Kotlin 2.3.21、Ktor 3.4.3、Gradle 9.4.1，输出 Java 17 字节码。
- 网页使用 HTML/CSS/JavaScript，核心业务使用 Kotlin。没有 Node.js、前端构建工具或云服务依赖。
- 原 spec 允许本地文件。这里选 JSON 保存多行简介和关联记录，通过 Kotlin Serialization 读写；若课程后续限定 CSV，只需替换 repository 层。
- 一个账号在 v0.1 中只有一个角色。社团人员只能访问所属社团的数据。多角色账号留到后续讨论。
- 报名检查和写入在同一把锁内完成，防止单个服务进程内重复报名和超额。当前文件存储仅支持一个服务进程。
- 密码使用带随机盐的 PBKDF2 哈希；接口不返回密码哈希。登录 Cookie 有签名并设置 HttpOnly 和 SameSite。
- 服务器重启会更新本地 Session 签名密钥，所有账号需要重新登录，业务数据不丢失。
- 时间字段统一按北京时间理解，页面不做浏览器时区换算。

## 测试与打包

```powershell
.\gradlew.bat test
.\gradlew.bat installDist
.\gradlew.bat distZip
```

`installDist` 输出在 `build/install/campus-club/`，包含启动脚本与运行依赖。进入该目录后可使用 `bin/campus-club.bat` 启动。`distZip` 生成可分发运行包，和交付的源代码 ZIP 用途不同。

## 局域网演示

默认只监听本机。如果需要同一 Wi-Fi 下的其他设备访问，启动前设置：

```powershell
$env:HOST='0.0.0.0'
.\gradlew.bat run
```

其他设备通过 `http://服务器电脑局域网IP:8080` 访问，前提是网络允许设备互访且防火墙允许该端口。当前演示账号公开，不要直接把基础版暴露到公网。

## 接下来从哪里开始

1. 阅读 [总体规划](docs/01-plan.md)，确认四人分工与相对迭代安排。
2. 使用 [需求调查和每周记录模板](docs/04-research-and-portfolio.md) 收集真实反馈。
3. 对照 [需求与验收清单](docs/02-requirements.md) 选择下一项功能。
4. 阅读 [架构与接口约定](docs/03-architecture.md) 后再修改接口与数据结构。
5. 将代码推到你们自己的 GitHub 仓库，Wiki 中整理这些文档及会议记录。仓库已建立，四人协作方式见 [协作说明](docs/07-team-collaboration.md)。
