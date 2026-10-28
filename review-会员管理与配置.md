# 审查：会员管理与配置.patch

### 摘要

这份 patch 里，`gatesHolding` 补上 `store_id` 是该合的修复：停用失败回写和恢复会员都会用这个对象算闸机到期时间，缺门店时会空指针。会员编辑里「没选卡就关掉弹窗」能绕开后端报错，但底部「保存」不会保存姓名和手机号，关掉后容易当成已经保存。小程序地址改成 `http://59.110.11.215` 只改了 `http.ts`，人脸预览仍走本机；这个 IP 也只在开发者工具模拟器里能请求，手机预览仍然会被微信拦下。

### 发现的问题

**[WARNING] 正确性：底部「保存」在未选卡时直接关掉弹窗**

- **位置：** `code/admin/src/views/MembersView.vue` 的 `saveGrant`
- **问题：** 姓名、手机号各自有「保存姓名」「保存手机号」。底部「保存」只提交新增的会员卡和课程。两边都没选时，后端本来会返回「请选择会员卡或课程」，弹窗留着。改完后弹窗直接关掉，没有提示。输入框里改过、还没点「保存姓名 / 保存手机号」的内容不会写入，操作上却像保存完成。
- **建议：** 没选卡或课程时留在弹窗里，并提示「未选择要新增的会员卡或课程」。姓名和手机号继续用各自的按钮保存。

**[WARNING] 正确性：小程序接口只改了一处，人脸预览仍打本机**

- **位置：** `code/mp/src/api/http.ts` 改为 `http://59.110.11.215`；`code/mp/src/api/auth.ts` 仍是 `http://127.0.0.1:8080`
- **问题：** 登录、列表等走 `http.ts`，会打到测试云。人脸预览 `facePreviewBase64` 自己拼 `auth.ts` 里的地址，仍请求本机 8080。本机后端没开时，资料页人脸图失败，其余接口却是线上数据。`http.ts` 上面的注释还写着「开发者工具仍走本机」，和改动不一致。线上 profile 是 `prod`，验证码 `246810`、模拟支付、测试人脸在这台云上不会生效。手机预览、真机调试仍校验合法域名，`http://59.110.11.215` 发不出去；勾选「不校验合法域名」只对电脑上的模拟器生效。
- **建议：** 日常调试保持两处都是 `http://127.0.0.1:8080`。要在模拟器里看线上时，两处一起改成 `http://59.110.11.215`（不要加 `:8080`），并改掉那句注释。真机等备案后的 `https://api.xz100w.com`。

**[WARNING] 配置：根上的 appid 填成了微信小程序 AppID**

- **位置：** `code/mp/src/manifest.json` 根字段 `appid`，以及 `mp-weixin.appid`
- **问题：** 微信开发者工具认的是 `mp-weixin.appid`。根上的 `appid` 是 DCloud 应用标识。把 `wxa4a1a49fdee50f7f` 填到根上，微信编译用不到，以后用 HBuilderX 会把它当成 DCloud AppID。AppID 可以进仓库，它不是密钥；这份 patch 没有写入 AppSecret，这一点是对的。服务器还要另配 `WX_MP_APPID` 和 `WX_MP_SECRET`，否则真实微信登录换 openid 会失败。
- **建议：** 只填 `mp-weixin.appid`。根上的 `appid` 保持空，或填 DCloud 申请到的应用标识。

**[SUGGESTION] 结构：闸机对象只补了一个字段，以后还可能漏**

- **位置：** `AdminMemberService.gatesHolding`
- **问题：** 这里手工 new 出一个 `GateDevice`，以前没设 `storeId`。`resumePerson` → `pushNow` → `validUntil(long storeId)` 会把 `null` 拆箱，恢复会员时抛空指针；停用写到一半失败、再把人脸写回闸机时同样会炸。补上 `store_id` 后这条路径能算出到期时间。`gate_device.store_id` 是 `NOT NULL`，`rs.getLong` 可用。`ensureOnline` 会按 id 重新查整行，在线判断不依赖这个字段；真正用到门店的是后面的 `pushNow` / `pushValidity`。
- **建议：** 这次补字段可以合。若还要动这段，改成按 id 查出完整设备再下发，避免下一个要用的字段又是 null。

### 做得好的地方

- `gatesHolding` 的 SQL 条件没变，只是把已有设备的 `store_id` 带到下发对象上，停用/恢复会联系哪些闸机保持原样。
- 日志文档补了 `--since`、关键字和「未处理异常」堆栈，和 `GlobalExceptionHandler` 里的日志文案一致，方便在测试云上对 500。
- AppSecret、支付私钥没有出现在 diff 里。

### 建议先做这些

1. 合入 `gatesHolding` 的 `store_id`。这是恢复会员和停用回写能算到期时间的前提。
2. 会员弹窗：没选卡或课程时不要静默关掉，留在弹窗里并提示未选择。
3. `http.ts` 的公网 IP 不要作为仓库里的默认地址。若模拟器临时连云，`auth.ts` 的人脸预览地址一起改。
4. `manifest.json` 只保留 `mp-weixin.appid`。根字段不要填这个微信 AppID。
