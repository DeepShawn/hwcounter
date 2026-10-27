VoiceParser（语音登记解析）的 JVM 测试，含一张小型拼音表与典型口语/识别错误用例：
同音字、口音混淆（z/zh、n/l、in/ing、f/h…）、多音字姓氏（曾、单、解、朴…）、漏字（欧阳娜→欧阳娜娜）、
只叫名（子涵→李子涵）、音近数字（是十四号→44）、中文数字与连读学号，以及“不应误配”的反例（王伟≠张伟）。
`V2.kt` 的 main 依次跑：模糊匹配用例 → 第一版回归（`V.kt`）→ 拼音首字母用例（`V3.kt`，含首字母撞车、口头禅不误配、关闭开关）。

编译方式同 `tools/paper-sim/run.sh`，源文件换成：
Stubs.kt Student.kt V.kt V2.kt V3.kt ../../app/src/main/java/com/deepshawn/hwcounter/recog/{Matcher,VoiceParser,PinyinSim}.kt
