package cn.lunarlanding.qualia.code.tool;

import cn.lunarlanding.qualia.code.service.MaterialService;
import cn.lunarlanding.qualia.core.tool.Parameter;

import java.util.List;
import java.util.Map;

/**
 * material_list：列出全部资料库（名称/描述/文章数/总字数）。
 *
 * <p>资料发现的唯一入口——系统提示词只注入使用指引、不含库清单快照，
 * 库的增删即时生效，避免提示词过期误导模型。</p>
 */
public class MaterialListTool extends MaterialToolSupport {

    public MaterialListTool(MaterialService materials) {
        super(materials);
        this.setName("material_list");
        this.setDescription("列出本机全部资料库（团队/项目文档库）。当用户提及「资料」「文档」「wiki」，"
                + "或任务需要既有约定、背景知识时，先用此工具发现可用资料库，"
                + "再用 material_tree 浏览目录、material_read 整篇阅读。");
        this.setParameters(new Parameter[]{});
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        try {
            List<MaterialService.MaterialInfo> list = materials.list();
            if (list.isEmpty()) {
                return "当前没有任何资料库。可提示用户在「资料」页面新建并上传后再试。";
            }
            StringBuilder sb = new StringBuilder("共 ").append(list.size()).append(" 个资料库：\n");
            for (MaterialService.MaterialInfo kb : list) {
                sb.append("- ").append(kb.name())
                        .append("（id: ").append(kb.id())
                        .append("，").append(kb.articleCount()).append(" 篇文章");
                if (kb.description() != null && !kb.description().isBlank()) {
                    sb.append("，").append(kb.description());
                }
                sb.append("）\n");
            }
            sb.append("\n后续 material_tree / material_read 的 kb 参数可传 id 或名称。");
            return sb.toString();
        } catch (Exception e) {
            return "读取资料库列表失败：" + e.getMessage();
        }
    }

    /** 只读工具：问答模式可用 */
    @Override
    public boolean isReadOnly() {
        return true;
    }
}
