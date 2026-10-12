package cn.lunarlanding.qualia.code.tool;

import cn.lunarlanding.qualia.code.service.MaterialService;
import cn.lunarlanding.qualia.core.tool.FunctionTool;

import java.util.List;
import java.util.Map;

/**
 * 资料库工具公共基类：持有 MaterialService 单例，提供 kb 参数解析（id 或名称精确匹配）。
 *
 * <p>路径契约与一期接口一致：kb 传库 id 或库名；文章 path 为库内相对路径
 * （material_tree 输出即 path），防越界由 MaterialService.resolveNode 兜底。</p>
 */
abstract class MaterialToolSupport extends FunctionTool {

    protected final MaterialService materials;

    protected MaterialToolSupport(MaterialService materials) {
        this.materials = materials;
    }

    /** 解析 kb 参数（id 或名称精确匹配），未命中或参数缺失返回 null */
    protected MaterialService.MaterialInfo resolveKb(Map<String, Object> arguments) {
        Object kb = arguments.get("kb");
        if (kb == null) {
            return null;
        }
        String key = String.valueOf(kb).trim();
        if (key.isEmpty()) {
            return null;
        }
        try {
            for (MaterialService.MaterialInfo info : materials.list()) {
                if (info.id().equals(key) || info.name().equals(key)) {
                    return info;
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    /** kb 未命中时的纠错文案：列出全部可用库（id + 名称），引导模型用 material_list 重新发现 */
    protected String kbMissMessage(Object kb) {
        StringBuilder sb = new StringBuilder("资料库不存在：").append(kb).append("。可用资料库：\n");
        try {
            List<MaterialService.MaterialInfo> list = materials.list();
            if (list.isEmpty()) {
                sb.append("（当前没有任何资料库，可提示用户在「资料」页面新建）");
            }
            for (MaterialService.MaterialInfo info : list) {
                sb.append("- ").append(info.name()).append("（id: ").append(info.id()).append("）\n");
            }
        } catch (Exception e) {
            return "读取资料库列表失败：" + e.getMessage();
        }
        return sb.toString();
    }

    /** 取字符串参数（null 安全） */
    protected String argString(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }
}
