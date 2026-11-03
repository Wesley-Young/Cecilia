# 开发约定

## 项目

- Cecilia 是基于 `@acidify/core`（Kotlin/JS）的桌面 IM 客户端，使用 PC 协议登录。
- 技术栈：Electron + electron-vite、pnpm v12、TypeScript 7（只做类型检查）、Biome；业务逻辑是 `@fraqjs/kernel` 插件，数据库用 `@fraqjs/plugin-kysely`（`node:sqlite` + Kysely）；界面用 React、TanStack Query 和 HeroUI v3（Tailwind v4），不引入其他 UI 框架。`@acidify/core`、`@fraqjs/*`、Kysely 和 Electron 锁定精确版本。
- 代码注释和标识符用英文，界面文案用中文。
- 只在开发者要求时修改 `README.md` 和 `AGENTS.md`。实现变了不需要改 `AGENTS.md`；觉得某条约定需要增改时，先问开发者。

## 进程与目录

- 主进程（`src/main/`）只做外壳：窗口、`app://` 协议、账号进程、safeStorage。`app.ts` 是唯一的组装入口，插件在 `plugins/` 中。
- 每个账号一个 utilityProcess（`src/account/`）。`app.ts` 是它的组装入口；`control.ts` 定义它与主进程之间的控制消息。
- `src/core/`：与 Electron 无关的业务插件，运行在账号进程的 `ChatContext` 中。每个子目录一个插件模块：`service.ts` 是服务类，`index.ts` 是插件定义。
- `src/renderer/`：沙箱中的页面，不使用 Electron 和 Node API。`views/` 只放页面，页面之间不互相导入；共享界面在 `components/`；`lib/` 只放数据和工具函数，不依赖界面。
- 依赖只能单向：renderer →（仅类型）account / main → core。离开当前目录时用 subpath imports（`#core/*`、`#main/*`、`#account/*`，写 `.ts` 后缀）；渲染进程内部用 `@/`，不写后缀。
- Cecilia 的文件放在 Electron 的 userData 下，不在 home 下建点目录。本地运行时把 `CECILIA_HOME` 设为另一个目录，就不影响已安装的应用。
- 日志经 kernel 的 logger 输出，账号进程的日志转发给主进程写出；日志文件只由持有单实例锁的进程打开。

## 插件

- `@fraqjs/kernel` 和 `@fraqjs/plugin-kysely` 的行为以 fraqjs/fraq 中对应版本的源码为准，`@acidify/core` 以 LagrangeDev/acidify 中对应版本的源码为准。
- 插件用 `#core/context.ts`（账号进程）或 `#main/context.ts`（主进程）的 `definePlugin` 定义。服务类带 `static token`，通过 `inject` / `provides` 声明依赖；kernel 按依赖排序，组装入口中的顺序无关紧要。
- 异步准备写在插件的 `apply` 中，启动后才开始的工作写在 `start` 中；资源释放写在服务的 `dispose()` 里，kernel 按 provide 的逆序调用。
- kernel 不支持热插拔：插件在启动前装好，Context 停止后不能重启。
- 不使用构造函数参数属性（`erasableSyntaxOnly`），字段在类中声明后在构造函数里赋值。
- 进程级的配置（数据目录、是否使用 mock）在启动时由主进程读取一次，经组装入口作为插件参数传入；插件不读 `process.env`。需要 Electron 能力的服务在 core 中定义为抽象类，由宿主实现（如 `Secrets`）。
- 账号如何登录（QQ 号、签名服务、AppInfo）是账号设置，存在账号数据库的 `cecilia_settings` 中，只通过 `AccountService.update` 修改：它在 QQ 号变化时先清空会话和本地消息，再让 `ChatClient.applySettings` 重建连接，不重启进程。
- 签名服务 Token 不写入日志；接口只返回是否已设置，不返回它的值。

## 数据边界

- `@acidify/core` 的 Kotlin 对象只出现在 `core/acidify/`，由 `mapper.ts` 转成 `core/model/` 中的普通数据。其他模块只用领域模型和 `ChatClient`。
- uin、seq、时间在领域模型中是 `number`；只有超过 2^53 的值（如 messageUid）才用 `string`。
- `ChatClient` 有两个实现：`acidify`（真实账号）和 `mock`（开发用），组装时二选一。
- 事件只在 `ChatEvents` 中声明：`client/*` 是服务器报告的原始变化，`contacts/*` 和 `store/*` 是插件处理后的结果，界面只跟随后者。
- 表结构在插件的 `apply` 中用 `kysely.schemas.register()` 注册，由 plugin-kysely 在 `start` 时迁移。改表结构时新增迁移，不改已有的迁移。
- 用到某个模块的表时，`import type {} from` 声明这些表的文件（`declare module '@fraqjs/plugin-kysely'`），否则界面推导出的类型会丢失。

## RPC

- 账号功能是 core router（`src/account/rpc/`），走主进程转交给窗口的 MessagePort；主进程自己的功能是 shell router（`src/main/shell-rpc.ts`），走 `app://`。
- preload 只负责转交 port，不暴露任何 API。不使用 `ipcMain`，新增能力一律写成 tRPC 过程；主动通知界面用 subscription。
- 路由只把校验后的输入交给服务；用户可见的错误抛 `AppError`。
- 渲染进程只通过 `@/lib/rpc` 访问后端；账号进程推送的变化统一在 `@/lib/cache.ts` 写入查询缓存。

## 渲染进程

- 服务端数据用 TanStack Query 管理，不自己写请求或状态 hooks。
- 按枚举取值的文案表用 `satisfies Record<..., string>` 覆盖全部取值。
- 需要定制 HeroUI 组件时在 `components/` 中组合，不复制或修改组件本身。

## 设计

开发者的指示优先于本节。

- 颜色只用 HeroUI 和 `styles.css` 中的语义变量，不写死色值（二维码需要的白底除外）。
- 只用系统字体（字体栈在 `styles.css` 中），组件不另设字体。URL、版本号等机器可读的值用 `font-mono`。
- 通用图标用 lucide。
- 每个界面最多一个主按钮，次要操作用 `ghost` 等次要样式。
- 不可恢复的操作要让用户明确确认，每个操作都有可见的结果。
- 跟随系统的深浅色。窗口在 macOS 上没有原生标题栏，各栏顶部用 `app-drag` 拖动窗口，其中可交互的元素加 `app-no-drag`。
- 界面做减法：标题、标签、按钮能说清的，不再另加说明。只有帮助用户做决定或从错误中恢复时，才加一句说明。

## UX 自查

界面质量靠设计和自查保证，不靠测试。界面改动用 `pnpm dev:mock` 从用户的视角把涉及的流程走一遍：

- 每一屏只有一个明确的主任务和一个主操作。
- 加载、空数据、出错、提交中、校验失败都有处理，状态切换时布局不跳动。
- 键盘可用：焦点顺序合理，对话框打开时聚焦第一个输入框，Enter 提交，Esc 关闭。
- 在最小窗口（760×520）和深色模式下不溢出、不错位；长名称、长消息会截断或换行。
- 把文案读一遍：能删的字删掉，同一个概念始终用同一个词。

## 验证

- 日常验证：`pnpm typecheck`、`pnpm lint`、`pnpm build`。
- 不写测试。
