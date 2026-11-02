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

在设置界面完成之前，账号通过环境变量配置：

| 变量                 | 说明                                   |
| -------------------- | -------------------------------------- |
| `CECILIA_UIN`        | 登录的 QQ 号（签名服务在扫码前就需要） |
| `CECILIA_SIGN_URL`   | Lagrange V2 签名服务地址               |
| `CECILIA_SIGN_TOKEN` | 签名服务的 Token                       |
| `CECILIA_PROTOCOL`   | `Linux`（默认）或 `Mac`                |
| `CECILIA_MOCK`       | 设为 `1` 时使用模拟账号                |
| `CECILIA_HOME`       | 数据目录；设置后不影响已安装的应用     |

### 检查

```bash
pnpm typecheck
pnpm lint
pnpm build
```

## See Also

- [Yogurt](https://acidify.ntqqrev.org/yogurt/start) - 基于 Kotlin Multiplatform 的 Milky 协议端框架，原生支持 Windows / macOS / Linux
