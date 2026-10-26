VoiceParser（语音登记解析）的 JVM 测试：`V.kt` 含一张小型拼音表与典型口语用例
（同音字、口音混淆、中文数字、连读学号、“一百零三”不误拆等）。
编译方式同 `tools/paper-sim/run.sh`，源文件换成：
Stubs.kt Student.kt V.kt ../../app/src/main/java/com/deepshawn/hwcounter/recog/{Matcher,VoiceParser}.kt
