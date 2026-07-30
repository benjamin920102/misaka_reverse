# Allatori 反混淆工具 (Allatori De-obfuscator)

一个轻量级的 Java 反混淆工具，能够从被混淆的 JAR 包中完全移除对 `ALLATORIxDEMO` 辅助类的所有引用，确保反编译器（如 CFR、JADX 等）不再生成任何 `import com.allatori.ALLATORIxDEMO;` 导入语句。

---

## 🎯 核心目标

* **完全擦除** 字节码中所有 `ALLATORIxDEMO` 类的定义。


* **彻底删除** 所有注入混淆字符串的静态解密辅助方法。


* **替换指令** 将解密方法的 `invokestatic` 调用替换为解密后明文串的直接 `ldc` 常量加载。


* **排除导出** 在最终生成的 JAR 包中排除辅助类文件，防止出现多余的 import 引用。



---

## 📦 核心优化点

| 优化特性 | 为什么重要 | 具体实现细节 |
| --- | --- | --- |
| **预先收集类名**<br> | 避免在写入 JAR 时重复进行正则或字符串扫描。

 | 首遍扫描时，构建包含所有 `ALLATORIxDEMO` 内部类名的 `Set<String>`。

 |
| **跳过类文件导出**<br> | 减少无用 I/O 操作，防止辅助类打包进入目标文件。

 | 遍历条目时，若文件名匹配已知辅助类，则直接忽略写入。

 |
| **单次 ASM 转换**<br> | 可以在单个 Visitor 中完成内部类清理、方法体删除和 `INVOKESTATIC` 指令替换。

 | `AllatoriStringTransformer` 顺序执行这三项字节码变更。

 |
| **`COMPUTE_FRAMES` 标志**<br> | 自动重新计算 StackMapTable/Frames，保证字节码合法有效。

 | 转换类时统一传入 `ClassWriter(cr, ClassWriter.COMPUTE_FRAMES)`。

 |
| **零内存分配修剪**<br> | 识别到解密方法时直接在 `visitMethod` 中返回 `null`，不再写入方法体字节码。

 | `if (isDecryptMethod(methodKey)) return null;`<br> |
| **精确解密识别**<br> | 通过 `(String) -> String` 签名与修饰符快速定位解密函数。

 | `isDecryptionHelper(Method)` 辅助识别逻辑。

 |
| **并行处理支持**<br> | 采用固定大小线程池并行处理包含成千上万个 Class 的大 JAR。

 | `ExecutorService` 配合 `processAndDumpJar` 方法实现并发转换。

 |

---

## 🚀 快速上手

### 1. 项目目录结构

```text
allatori_deobfuscator/
├─ AllatoriStringTransformer.java  // ASM 字节码转换逻辑
├─ JarDeobfuscatorEngine.java     // 多线程 JAR 解包与写出引擎
├─ Main.java                       // CLI 入口与参数解析
└─ README_zh.md

```

### 2. 编译项目（无需 Maven/Gradle，仅需 JDK）

```bash
javac -d . *.java

```

### 3. 运行工具

```bash
java Main <输入混淆JAR> <输出干净JAR>

```

**示例：**

```bash
java Main C:\data\obfuscated.jar C:\data\clean.jar

```

处理完成后生成的 `clean.jar` 将不再包含任何 `ALLATORIxDEMO` 引用。

---

## 🛠️ 工作原理

1. **扫描与收集**：首先通过 ClassLoader 加载 JAR 中所有静态解密方法 `(String) -> String` 并记录辅助类名。


2. **遍历条目**：使用线程池并发处理源 JAR 包中的每一个文件 entry：


* 若 entry 为 `ALLATORIxDEMO` 辅助类本身，则跳过并丢弃。


* 若 entry 为普通的 `.class` 文件，则交给 ASM 进行字节码重构。




3. **字节码转换 (`AllatoriStringTransformer`)**：
* 移除包含 `ALLATORIxDEMO` 的内部类声明（`visitInnerClass`）。


* 过滤删除解密辅助方法主体。


* 拦截 `INVOKESTATIC` 指令：读取先前的 `LDC` 混淆参数，在内存中直接反射执行解密，将指令替换为解密后明文的 `LDC` 指令（若包含 `"ALLATORIxDEMO"` 水印后缀则自动切除）。




4. **输出导出**：将修改后的字节码写回目标 JAR 文件。



---

## 📜 命令行说明

```bash
java Main <input-jar> <output-jar>

```

* `<input-jar>`：待处理的被 Allatori 混淆的 JAR 文件路径。


* `<output-jar>`：反混淆完成后输出的 JAR 文件路径。



---

## 📌 常见注意事项与局限性

1. **非标准解密签名**：若解密函数并非单参数 `(String) -> String`，需调整 `JarDeobfuscatorEngine.isDecryptionHelper` 方法。


2. **类加载环境**：解密过程依赖反射调用（`Method.invoke`），若混淆字符串依赖特定的运行期类加载环境，反射执行解密时可能会抛出异常并保留原指令。


3. **数字签名**：修改字节码会破坏原 JAR 包的签名，导出的 JAR 为未签名状态。若程序运行依赖签名，需自行重新签名。
