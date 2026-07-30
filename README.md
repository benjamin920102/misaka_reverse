# Allatori 反混淆解密工具

## 破解原理

本工具利用 Java Agent + ASM 技术，**动态拦截并移除 Allatori 混淆器注入的 `ALLATORIxDEMO` 解密类别与方法**，彻底清除所有 import 与调用痕迹。

### 核心特点

- **自动扫描 & 删除 ALLATORIxDEMO.class**
- **抹除所有位元组码层级的引用**（包括常数池、内部类、方法调用等）
- **还原干净无痕的明文字串**

### 破解流程

1. 利用 Java Agent 挂载目标 Jar，拦截加载过程。
2. 动态定位 ALLATORIxDEMO 解密类别与方法。
3. ASM 重写所有 class 档案：
- 替换 invokestatic 为 LDC 明文常数
- 移除多余的 import / class 属性 / 常数池引用
- 直接删除 ALLATORIxDEMO.class 本体
4. 输出全新干净 Jar 包，**彻底抹去一切混淆痕迹！ **

---

## 破解效果对比

| 处理阶段 | 反编译后效果 |
| --- | --- |
| 原始混淆档 | `import com.allatori.ALLATORIxDEMO;`<br>`String s = ALLATORIxDEMO.a("x89\x12...");` |
| 一般解密工具 | `import com.allatori.ALLATORIxDEMO;` *(遗留无用 import)*<br>`String s = "Hello World";` |
| 本工具破解后 | `// 无任何 ALLATORIxDEMO 相关 import`<br>`String s = "Hello World";` |

---

## 使用方式

1. 编译本专案并产生 agent jar
2. 执行：`java -javaagent:allatori-decryptor.jar -jar target-app.jar`
3. 取得已完全解密且无痕的新 Jar 包！
