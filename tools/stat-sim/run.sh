#!/bin/sh
# 用 Gradle 自带的 Kotlin 编译器在 JVM 上跑 StatTable 测试：sh tools/stat-sim/run.sh
set -e
cd "$(dirname "$0")"
L=${GRADLE_LIB:-/opt/gradle/lib}
CP="$L/kotlin-compiler-embeddable-2.0.21.jar:$L/kotlin-stdlib-2.0.21.jar:$L/kotlin-reflect-2.0.21.jar:$L/kotlin-script-runtime-2.0.21.jar:$(ls $L/kotlinx-coroutines-core-jvm-*.jar | head -1):$(ls $L/annotations-*.jar | head -1):$(ls $L/trove4j-*.jar | head -1)"
SRC=../../app/src/main/java/com/deepshawn/hwcounter
OUT=${OUT:-/tmp/stat-sim}
mkdir -p "$OUT"
java -cp "$CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -cp "$L/kotlin-stdlib-2.0.21.jar" -d "$OUT" \
  SerStub.kt StatTest.kt $SRC/data/Models.kt $SRC/data/Drafts.kt $SRC/recog/SeatingLogic.kt $SRC/recog/StatTable.kt 2>&1 | grep -v "^warning" || true
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$OUT:$L/kotlin-stdlib-2.0.21.jar" StatTestKt
