#!/usr/bin/env bash
# 试卷计数算法（PaperCounter）离线仿真测试：合成不同厚度/噪声/模糊/倾斜的纸堆侧面图，统计计数准确率。
# 依赖：JDK 17+；首次运行会从 Maven Central 下载 Kotlin 编译器。
set -euo pipefail
cd "$(dirname "$0")"
M=https://repo1.maven.org/maven2
mkdir -p lib
for a in \
  org/jetbrains/kotlin/kotlin-compiler-embeddable/2.0.20/kotlin-compiler-embeddable-2.0.20.jar \
  org/jetbrains/kotlin/kotlin-stdlib/2.0.20/kotlin-stdlib-2.0.20.jar \
  org/jetbrains/kotlin/kotlin-script-runtime/2.0.20/kotlin-script-runtime-2.0.20.jar \
  org/jetbrains/kotlin/kotlin-reflect/1.6.10/kotlin-reflect-1.6.10.jar \
  org/jetbrains/kotlin/kotlin-daemon-embeddable/2.0.20/kotlin-daemon-embeddable-2.0.20.jar \
  org/jetbrains/intellij/deps/trove4j/1.0.20200330/trove4j-1.0.20200330.jar \
  org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.6.4/kotlinx-coroutines-core-jvm-1.6.4.jar \
  org/jetbrains/annotations/13.0/annotations-13.0.jar; do
  f=lib/$(basename "$a"); [ -f "$f" ] || curl -sSfL -o "$f" "$M/$a"
done
CP=$(ls lib/*.jar | tr '\n' ':')
rm -rf out
java -cp "$CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -nowarn -cp lib/kotlin-stdlib-2.0.20.jar -d out \
  src/Bitmap.kt src/Test.kt src/Sweep3.kt ../../app/src/main/java/com/deepshawn/hwcounter/recog/PaperCounter.kt
java -cp out:lib/kotlin-stdlib-2.0.20.jar TestKt
java -cp out:lib/kotlin-stdlib-2.0.20.jar Sweep3Kt
