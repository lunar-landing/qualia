package cn.lunarlanding.qualia.code.tool;

import cn.lunarlanding.qualia.code.service.MaterialService;
import cn.lunarlanding.qualia.core.tool.Parameter;

import java.util.Map;

/**
 * material_edit：更新资料库的名称/描述（未提供的字段保持不变）。
 *
 * <p>写侧工具（isReadOnly=false，只读问答模式自动拦截）。仅改库元信息，不动库内文章；
 * 与 material_create_kb 对应，补齐库生命周期中段的管理能力。</p>
 */
public class MaterialEditTool extends MaterialToolSupport {

    public MaterialEditTool(MaterialService materials) {
        super(materials);
        this.setName("material_edit");
        this.setDescription("更新已有资料库的名称或描述（两者至少提供一个，未提供的保持不变）。"
                + "当用户要求修改资料库名称、修正库的描述或用途说明时使用；不影响库内文章。");
        this.setParameters(new Parameter[]{
                new Parameter("kb", "目标资料库的 id 或名称（先用 material_list 发现）", "string", true),
                new Parameter("name", "新名称（可选）", "string", false),
                new Parameter("description", "新描述（可选，传空字符串表示清空）", "string", false),
        });
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        MaterialService.MaterialInfo kb = resolveKb(arguments);
        if (kb == null) {
            return kbMissMessage(arguments.get("kb"));
        }
        String name = argString(arguments, "name");
        // description 区分「未提供」（保持不变）与「显式空串」（清空）
        String description = arguments.get("description") == null
                ? null : String.valueOf(arguments.get("description"));
        if (name.isEmpty() && description == null) {
            return "更新失败：name 与 description 至少提供一个。";
        }
        try {
            MaterialService.MaterialInfo r = materials.update(kb.id(),
                    name.isEmpty() ? null : name, description);
            String desc = r.description() == null || r.description().isBlank()
                    ? "（无）" : r.description();
            return "已更新资料库：「" + r.name() + "」，描述：" + desc + "。";
        } catch (Exception e) {
            return "更新失败：" + e.getMessage();
        }
    }

    /** 写操作：问答只读模式不可用 */
    @Override
    public boolean isReadOnly() {
        return false;
    }
}
