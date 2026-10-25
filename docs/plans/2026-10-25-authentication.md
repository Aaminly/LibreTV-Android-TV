# 网页访问密码兼容实现与验收计划

目标：有密码站点先验证才能使用；缓存会话，下次打开不重复输入；继续支持作者明确开放的免密码部署。

采用用户确认的密码弹框流程，复用现有电视 UI、HttpURLConnection 和 Media3。仅缓存服务器签发的会话，不保存密码。候选验证成功后一次提交地址与会话，原有观看记录和收藏继续按服务地址隔离。

- [x] 复现 API 缺失 Cookie、重定向及缺少 PASSWORD 的失败。
- [x] SessionCookies/LibreTvApi/WatchStore：候选隔离、持久化、到期、Secure、完整 origin 和路径范围。
- [x] AccessGate/ServiceAccess/Main：启动与换站验证、强制弹框、失效锁定、后台与旧任务取消。
- [x] Playback/Images/RemoteControl/LanInputServer：认证覆盖实际媒体、封面及手机链路，手机不接收上游会话。
- [x] 当前最终代码运行 JVM、Lint、手机同步、模拟器和真实官方协议检查；Astra 审核证据及边界。
- [x] 签名 APK 与安装包一致性校验。

源码交付及既有 `origin/main` 推送由收尾发布记录记载；GitHub Release 新发布需具体产物审批。

重点回归：已有历史不能绕过、失败密码和取消验证、重启会话恢复、会话撤销与第三方资源 401 的区别、旧站到期不能打断新站、跨端口重定向不得携带 Cookie、开放/未配置密码状态必须区分。
