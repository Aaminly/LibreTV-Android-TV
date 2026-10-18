# Phone Remote Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 手机搜索选片并控制电视真实播放。
**Architecture:** LAN HTTP 服务复用令牌安全边界，RemoteControl 管理前台会话和主线程播放器命令，手机原生网页使用真实 API。
**Tech Stack:** Kotlin Views / Media3 / Java ServerSocket / vanilla HTML CSS JS / ZXing.
**Spec:** docs/superpowers/specs/2026-10-19-phone-remote.md

## Global Constraints
- minSdk 23；不增加依赖、权限或后台播放。
- 1.3.0 code 4；保留签名与用户资料。
- 按用户直接实施授权原地完成，发布既有公开 origin/main；不发布此前未完成的搜索入口方案。

## Review Focus
- 过期令牌和跨站命令：协议测试拒绝。
- 切换影片后旧进度操作：媒体标识检查与设备测试。
- 页面隐藏/网络断开：停止轮询、禁用播放控制，恢复时读取真实状态。
- 搜索不阻塞播放器控制：独立 API 实例和有界工作线程。
- Main/Playback 过渡不关闭连接，真正离开应用关闭：设备测试。

### Task 1: LAN protocol
- [x] 替换单次输入测试为重复搜索、state/control、来源/令牌/参数、关闭监听测试；运行观察失败。
- [x] 修改 LanInputServer 为 JSON routes，与 `(String,JSONObject)->JSONObject` handler 接口连接。
- [x] JVM 全套通过。

### Task 2: TV session and player
- [x] 更新设备测试：二维码常驻，获取 URL，等待外部选片后检查真实播放、暂停、seek、恢复和退出。
- [x] 新建 RemoteControl.kt（前台生命周期、API 注册表、主线程命令）；MainActivity 侧栏 QR 与 PlaybackActivity 状态/命令接入，移除 PhoneInputDialog 及搜索按钮。
- [x] 真实设备检查 QR 和播放生命周期，不丢失已有收藏/记录。

### Task 3: Phone interface and release
- [x] phone-input.html 实现搜索结果、选集、播放条、状态同步；手机视口截图与真实操作验证。
- [x] 运行 build/unit/lint 与设备回归，修正问题；一次独立最终审阅。
- [x] README、截图、验证报告、签名 APK；同步原公开仓库，核对 SHA，清理被替代包。

执行记录：按用户最新指示停止实体电视测试/安装；改用本机模拟器。原地复用已授权工作区，未创建新 worktree。最终审阅的两项问题已修复；发布与清理结果在交付报告记录。
