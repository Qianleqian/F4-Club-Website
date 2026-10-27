# 本次基础版验证记录

日期：2026-10-23。验证环境：用户 Windows 电脑、OpenJDK 26.0.2.1、Gradle Wrapper 9.4.1。本次使用临时预览数据，没有修改小组真实业务数据。

## 自动化结果

执行：`gradlew.bat test installDist`。

最终结果：BUILD SUCCESSFUL。ApplicationTest 1 项、CampusServiceTest 6 项，共 7 项，失败 0，错误 0。测试覆盖项目中的业务服务和 Ktor HTTP 路由；不是压力测试或真实用户验收。

打包产物可从 `build/install/campus-club/` 运行。HTML 测试报告由 Gradle 生成于 `build/reports/tests/test/index.html`，不包含在源代码压缩包内，重新运行测试即可生成。

## 浏览器检查

- 桌面首页、搜索框、活动卡片和登录框正常显示。
- 使用 student 演示账号完成实际网页登录、报名，并在我的报名中看到记录。
- 停止并重新启动服务后，首页仍显示已有的报名人数；旧 Session 失效并回到匿名状态。
- 在 360px 宽度设置下检查首页，文档内容宽度与可见宽度一致，没有横向溢出。
- 桌面与手机截图分别保存在本目录的 preview-desktop.jpg 和 preview-mobile.jpg。

四个角色的接口流程已经通过 HTTP 自动化测试。其他角色的全部页面操作、手机端完整业务流程、局域网设备互访、性能指标和真实用户验收仍需小组继续验证。

## 本次未部署

只进行本地开发和临时预览，没有开通云资源，没有发布公网网站，没有创建 GitHub 远程仓库。
