<div align="center">

<h1>Cecilia</h1>

Desktop IM client built on [@acidify/core](https://acidify.ntqqrev.org/core/nodejs) and Electron

[QQ 群](https://qm.qq.com/q/C04kPQzayk) | [Telegram](https://t.me/WeavingStar)

</div>

## 开发

需要 Node.js 24.13+ 和 pnpm 12。

```bash
pnpm install
pnpm dev:mock   # 使用模拟账号，不需要登录
pnpm dev        # 使用真实账号
```

在国内网络下，Electron 的二进制需要通过代理下载：

```bash
ELECTRON_GET_USE_PROXY=1 GLOBAL_AGENT_HTTPS_PROXY=http://127.0.0.1:7890 pnpm install
```

### 配置账号

首次启动时在登录界面填写 QQ 号、Lagrange V2 签名服务的地址和 Token，并选择协议版本（`@acidify/core` 内置的 AppInfo）。之后可以在登录界面的「账号设置」或主界面左上角的设置按钮中修改。设置保存在账号的数据库中；更换 QQ 号会清空本机的聊天记录和登录状态。

开发时还有两个环境变量：

| 变量           | 说明                               |
| -------------- | ---------------------------------- |
| `CECILIA_MOCK` | 设为 `1` 时使用模拟账号            |
| `CECILIA_HOME` | 数据目录；设置后不影响已安装的应用 |

### 检查

```bash
pnpm typecheck
pnpm lint
pnpm build
```

## See Also

- [Yogurt](https://acidify.ntqqrev.org/yogurt/start) - 基于 Kotlin Multiplatform 的 Milky 协议端框架，原生支持 Windows / macOS / Linux
