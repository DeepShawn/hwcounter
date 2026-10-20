package com.deepshawn.hwcounter.export

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 极简 XLSX 生成器（无第三方依赖），支持多 Sheet、内联字符串、数字、几种填充色、列宽、冻结首行。
 */
class XlsxWriter {
    enum class Style(val id: Int) { NORMAL(0), HEADER(1), GREEN(2), RED(3), YELLOW(4), BLUE(5), GOLD(6), TITLE(7) }

    data class Cell(val value: Any?, val style: Style = Style.NORMAL)
    class Sheet(val name: String) {
        val rows = ArrayList<List<Cell>>()
        var widths: List<Double> = emptyList()
        var freezeRows = 1
        var freezeCols = 0
        fun row(vararg cells: Any?) {
            rows += cells.map { if (it is Cell) it else Cell(it) }
        }
        fun rowCells(cells: List<Cell>) { rows += cells }
    }

    private val sheets = ArrayList<Sheet>()
    fun sheet(name: String, block: Sheet.() -> Unit) { sheets += Sheet(name).apply(block) }

    private fun esc(s: String) = buildString {
        for (ch in s) when (ch) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            else -> if (ch.code < 0x20 && ch != '\n' && ch != '\t') append(' ') else append(ch)
        }
    }

    private fun colName(i: Int): String {
        var n = i + 1
        val sb = StringBuilder()
        while (n > 0) {
            val r = (n - 1) % 26
            sb.insert(0, ('A' + r))
            n = (n - 1) / 26
        }
        return sb.toString()
    }

    fun write(out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            fun put(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("[Content_Types].xml", buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
                sheets.indices.forEach { append("""<Override PartName="/xl/worksheets/sheet${it + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""") }
                append("</Types>")
            })
            put("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            put("xl/workbook.xml", buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
                sheets.forEachIndexed { i, s ->
                    val nm = esc(s.name.replace(Regex("[\\\\/?*\\[\\]:]"), " ").take(31))
                    append("""<sheet name="$nm" sheetId="${i + 1}" r:id="rId${i + 1}"/>""")
                }
                append("</sheets></workbook>")
            })
            put("xl/_rels/workbook.xml.rels", buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
                sheets.indices.forEach { append("""<Relationship Id="rId${it + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${it + 1}.xml"/>""") }
                append("""<Relationship Id="rId${sheets.size + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""")
            })
            put("xl/styles.xml", STYLES)
            sheets.forEachIndexed { i, s -> put("xl/worksheets/sheet${i + 1}.xml", sheetXml(s)) }
        }
    }

    private fun sheetXml(s: Sheet): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        if (s.freezeRows > 0 || s.freezeCols > 0) {
            val cell = colName(s.freezeCols) + (s.freezeRows + 1)
            append("<sheetViews><sheetView workbookViewId=\"0\"><pane")
            if (s.freezeCols > 0) append(" xSplit=\"${s.freezeCols}\"")
            if (s.freezeRows > 0) append(" ySplit=\"${s.freezeRows}\"")
            append(" topLeftCell=\"$cell\" activePane=\"bottomRight\" state=\"frozen\"/></sheetView></sheetViews>")
        }
        if (s.widths.isNotEmpty()) {
            append("<cols>")
            s.widths.forEachIndexed { i, w -> append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""") }
            append("</cols>")
        }
        append("<sheetData>")
        s.rows.forEachIndexed { r, cells ->
            append("<row r=\"${r + 1}\">")
            cells.forEachIndexed { c, cell ->
                val ref = colName(c) + (r + 1)
                val st = if (cell.style.id != 0) " s=\"${cell.style.id}\"" else ""
                when (val v = cell.value) {
                    null -> if (st.isNotEmpty()) append("<c r=\"$ref\"$st/>")
                    is Number -> append("<c r=\"$ref\"$st><v>${v}</v></c>")
                    else -> append("<c r=\"$ref\"$st t=\"inlineStr\"><is><t xml:space=\"preserve\">${esc(v.toString())}</t></is></c>")
                }
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    companion object {
        private fun fill(rgb: String) = """<fill><patternFill patternType="solid"><fgColor rgb="FF$rgb"/><bgColor indexed="64"/></patternFill></fill>"""
        private val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="3"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font><font><b/><sz val="14"/><name val="Calibri"/></font></fonts><fills count="8"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill>${fill("0FA38A")}${fill("D9F7EC")}${fill("FFE0E0")}${fill("FFF3C4")}${fill("DCE8FF")}${fill("FFE9A8")}</fills><borders count="2"><border><left/><right/><top/><bottom/><diagonal/></border><border><left style="thin"><color rgb="FFDDDDDD"/></left><right style="thin"><color rgb="FFDDDDDD"/></right><top style="thin"><color rgb="FFDDDDDD"/></top><bottom style="thin"><color rgb="FFDDDDDD"/></bottom><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="8"><xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1"/><xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf><xf numFmtId="0" fontId="0" fillId="3" borderId="1" xfId="0" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center"/></xf><xf numFmtId="0" fontId="0" fillId="4" borderId="1" xfId="0" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center"/></xf><xf numFmtId="0" fontId="0" fillId="5" borderId="1" xfId="0" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center"/></xf><xf numFmtId="0" fontId="0" fillId="6" borderId="1" xfId="0" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center"/></xf><xf numFmtId="0" fontId="0" fillId="7" borderId="1" xfId="0" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center"/></xf><xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>"""
    }
}
