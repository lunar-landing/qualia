package cn.lunarlanding.qualia.core.parser;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PDF 文件解析器：基于 Apache PDFBox 提取文字层。
 *
 * <p>按页产出 Document（{@code metadata.page} 为从 1 开始的页码）。
 * PDFBox 抽取的是 PDF 内嵌文字对象，因此仅支持文字版 PDF；
 * 扫描件只有整页位图、无文字对象，全部页面均提取不到文字时判定为扫描版并抛出异常。</p>
 */
public class PdfDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String fileName) {
        return DocumentParser.hasExtension(fileName, ".pdf");
    }

    @Override
    public List<Document> parse(byte[] data) {
        try (PDDocument document = Loader.loadPDF(data)) {
            PDFTextStripper stripper = new PDFTextStripper();
            int pageCount = document.getNumberOfPages();
            List<Document> pages = new ArrayList<>(pageCount);
            boolean anyText = false;

            for (int page = 1; page <= pageCount; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(document);
                if (!text.isBlank()) {
                    anyText = true;
                }
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("type", "pdf");
                metadata.put("page", page);
                pages.add(new Document(text, metadata));
            }

            if (!anyText) {
                throw new DocumentParseException("疑似扫描版 PDF（全部页面均无文字层），仅支持文字层提取");
            }
            return pages;
        } catch (InvalidPasswordException e) {
            throw new DocumentParseException("PDF 已加密，无法解析", e);
        } catch (IOException e) {
            throw new DocumentParseException("PDF 解析失败: " + e.getMessage(), e);
        }
    }
}
