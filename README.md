# PydroidCN

Pydroid 3 界面汉化模块（LSPosed / Xposed）。

作用域：`ru.iiec.pydroid3`
实测版本：Pydroid 3 `8.4_arm64`（versionCode 1131）

---

## 它到底改了什么

Pydroid 3 的界面文字分散在三个互不相通的管线上，所以本模块同时挂三条：

| 层 | 拦截点 | 覆盖量 |
|---|---|---|
| 资源层 | `Resources.getString` / `getText` / `getQuantityString` / `getQuantityText` | 594 条 |
| 文本层 | `TextView.setText(CharSequence)` / `(CharSequence, BufferType)` / `(char[],int,int)` | 523 条 |
| 对话框层 | `AlertDialog.Builder.setMessage/setTitle`、`Toast.makeText` | 复用文本层字典 |
| assets 层 | `AssetManager.open(String)` 对 `.txt/.json/.html/.kv/.cfg/.ini` 做流内替换 | 按命中 |

设计上有两条硬约束：

1. **资源层不看 App 传进来的 `Resources` 实例**。Pydroid 大量使用 `activity.getResources().newTheme()`，拿到的实例可能是 theme 包装过的；这里固定用宿主 `ctx.getResources()` 反查 `getResourceName(id)`，再去字典里找中文。稳定且不受 theme 影响。
2. **`TextView.setText(int)` 直接放行**。它必然落到 `Resources.getText()`，由资源层统一处理。两边都改会造成二次格式化。

## 目录

```
app/
├── AndroidManifest.xml          模块入口 + xposedscope 声明
├── assets/xposed_init          com.hsqs.pydroidcn.MainHook
├── res/xml/scope.xml           ru.iiec.pydroid3
└── src/com/hsqs/pydroidcn/
    ├── MainHook.java           IXposedHookLoadPackage 入口
    ├── ResHook.java            资源层
    ├── TextHook.java           文本层
    ├── DialogHook.java         对话框 / Toast
    ├── AssetsHook.java         assets 文本流
    ├── Dict.java               字典门面（长串优先匹配）
    ├── DictData.java           自动生成，594 名 + 523 串
    ├── AppCtx.java             宿主 Context 弱引用
    └── Logx.java               日志
build.sh                        无 Gradle 构建（aapt2 + javac + d8 + apksigner）
pack.py                         打包 + 4 字节对齐
```

## 构建

不需要 Gradle。依赖只有系统的 `aapt2`、`javac`、`d8`、`apksigner`。

```bash
bash build.sh
# → /storage/emulated/0/java66666666/PydroidCN.apk
```

脚本会自动拉 `de.robv.android.xposed:api:82` 作为 compileOnly 依赖，并复用本地 keystore（首次运行时生成）。

## 安装

1. 装 `PydroidCN.apk`
2. LSPosed 里启用模块，作用域勾 **Pydroid 3**
3. **强制停止** Pydroid 3 再重新打开（必须冷启动，`Application.attach` 只在进程创建时跑）

## 验证

```bash
adb logcat -s PydroidCN
```

正常应看到：

```
PydroidCN: loaded into ru.iiec.pydroid3
PydroidCN: dict loaded name=594 text=523
PydroidCN: attach pkg=ru.iiec.pydroid3
PydroidCN: hook getString/1 ok
PydroidCN: hook getText/1 ok
PydroidCN: res hook installed count=6 dict=594
PydroidCN: text hook installed, text dict=523
```

## 词表怎么来的

不是拍的，是从 APK 里抠出来逐条翻译的：

```bash
# 资源名 + 英文原文
aapt2 dump resources base.apk > res_dump.txt

# 按 name -> 中文 逐条对齐，生成 DictData.java
python3 gen/gen_dict.py
```

`gen/zh.tsv` 是人工对齐的中间产物（594 行），`gen/gen_dict.py` 负责把它和 `res_dump.txt` 合成 Java 常量。想加词就改 `zh.tsv` 重跑脚本，不用碰任何 Hook 逻辑。

## 已知边界

- Pydroid 的 Python 运行环境（`assets/finalpackage3_arm64.tar.xz`，56MB）是二进制包，不处理。里面的 Kivy 库文案属于第三方库，不在汉化范围内。
- `assets/` 只在 `open()` 时做流内替换，不落盘，APK 原文件永远干净。
- 文本层遇到已含中文的字符串直接放行，避免反复翻译。
- 长串优先替换：`Open project` 不会被打成 `打开 project`。
