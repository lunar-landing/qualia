package cn.lunarlanding.qualia.code.tool;

import cn.lunarlanding.qualia.code.service.MaterialService;
import cn.lunarlanding.qualia.core.tool.Parameter;

import java.util.Map;

/**
 * material_create_kb：创建新的资料库。
 *
 * <p>写侧工具（isReadOnly=false，只读问答模式自动拦截）。名称查重在前——
 * 重名库会让后续按名称引用产生歧义。</p>
 */
public class MaterialCreateKbTool extends MaterialToolSupport {

    public MaterialCreateKbTool(MaterialService materials) {
        super(materials);
        this.setName("material_create_kb");
        this.setDescription("创建一个新的资料库（团队/项目文档库）。当用户要求把文档沉淀成资料库、"
                + "建立新的知识库时使用；创建后用 material_write 向其中写入文章。");
        this.setParameters(new Parameter[]{
                new Parameter("name", "资料库名称（如「产品文档」「团队规范」）", "string", true),
                new Parameter("description", "资料库描述（可选，一句话说明用途）", "string", false),
        });
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        String name = argString(arguments, "name");
        if (name.isEmpty()) {
            return "创建失败：name 不能为空。";
        }
        try {
            // 名称查重：重名会让 resolveKb 按名称匹配时产生歧义
            for (MaterialService.MaterialInfo info : materials.list()) {
                if (info.name().equals(name)) {
                    return "创建失败：已存在同名资料库「" + name + "」（id: " + info.id() + "）。可直接向其写入，或换一个名称。";
                }
            }
            MaterialService.MaterialInfo kb = materials.create(name, argString(arguments, "description"));
            return "已创建资料库「" + kb.name() + "」（id: " + kb.id() + "）。\n"
                    + "可用 material_write 向其写入文章（path 支持 guide/xxx.md 形式的子目录）。";
        } catch (Exception e) {
            return "创建资料库失败：" + e.getMessage();
        }
    }

    /** 写操作：问答只读模式不可用 */
    @Override
    public boolean isReadOnly() {
        return false;
    }
}
