package cn.lunarlanding.qualia.core.parser;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Word 文件解析器：基于 Apache POI 提取 .docx 正文。
 *
 * <p>按文档 body 元素顺序提取段落与表格（表格按行拼接单元格文本，制表符分隔），
 * 产出单个 Document。老格式 .doc（二进制 OLE）不支持，后缀不匹配时由调用方提示。</p>
 */
public class WordDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String fileName) {
        return DocumentParser.hasExtension(fileName, ".docx");
    }

    @Override
    public List<Document> parse(byte[] data) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(data))) {
            // 正文按 body 元素顺序提取，保留段落与表格次序
            StringBuilder text = new StringBuilder();
            List<IBodyElement> bodyElements = document.getBodyElements();
            for (IBodyElement element : bodyElements) {
                if (element instanceof XWPFParagraph paragraph) {
                    appendParagraph(text, paragraph.getText());
                } else if (element instanceof XWPFTable table) {
                    appendTable(text, table);
                }
            }

            if (text.toString().isBlank()) {
                throw new DocumentParseException("Word 文档无正文内容");
            }
            return List.of(new Document(text.toString(), Map.of("type", "docx")));
        } catch (IOException e) {
            throw new DocumentParseException("Word 解析失败: " + e.getMessage(), e);
        }
    }

    /** 追加段落文本，空段落跳过，段间换行 */
    private void appendParagraph(StringBuilder text, String paragraphText) {
        if (paragraphText != null && !paragraphText.isBlank()) {
            text.append(paragraphText.strip()).append("\n");
        }
    }

    /** 追加表格：每行一行文本，单元格以制表符分隔 */
    private void appendTable(StringBuilder text, XWPFTable table) {
        for (XWPFTableRow row : table.getRows()) {
            StringBuilder line = new StringBuilder();
            for (XWPFTableCell cell : row.getTableCells()) {
                if (line.length() > 0) {
                    line.append("\t");
                }
                line.append(cell.getText() == null ? "" : cell.getText().strip());
            }
            if (!line.toString().isBlank()) {
                text.append(line).append("\n");
            }
        }
    }
}
