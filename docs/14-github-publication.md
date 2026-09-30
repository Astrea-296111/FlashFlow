# GitHub 发布验收

2026-09-30，公开仓库 Astrea-296111/FlashFlow。完整 347 文件通过 GitHub API 上传，上传时 tree SHA 与本地交付 tree SHA 完全相同：6545b00f92329d395ce4cfa5aa35cb7ceb9a4eca。原始分阶段开发历史保留在 Release 的 FlashFlow-history.bundle，远端 main 使用导入提交及后续文档提交。

远端 CI #1 和 #2 均 success；#2 对应 5fc77a971c70e2bc0f7c85eaf53e0103c84c9d7c，运行 ID 36724535673。验证步骤包含 spotless、Maven verify（单元与 Testcontainers 集成测试）、package、docker compose config、docker build。完整 Compose 业务链路和公网服务部署仍未验证。

此前本地网络隔离失败记录保留，远端 CI 已提供可运行环境中的重新验收。CI 动态状态以 GitHub Actions 为准。
