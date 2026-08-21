package cn.lunarlanding.qualia.core.skill;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 技能类，采用渐进式披露设计
 *
 * <ul>
 *   <li>content: skill.md 的完整内容</li>
 *   <li>references: references/ 目录下的附属文档</li>
 *   <li>scripts: script/ 目录下的脚本元数据</li>
 * </ul>
 */
public class Skill {
    private String name;
    private String description;
    private String content;
    private Map<String, String> references = new LinkedHashMap<>();
    private List<SkillScript> scripts = new ArrayList<>();

    public Skill(String name, String description) {
        this.name = name;
        this.description = description;
    }

    // === 构建方法（Fluent API） ===

    public Skill withContent(String content) {
        this.content = content;
        return this;
    }

    public Skill withReference(String name, String content) {
        this.references.put(name, content);
        return this;
    }

    public Skill addScript(SkillScript script) {
        this.scripts.add(script);
        return this;
    }

    // === 查询方法 ===

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getContent() {
        return content;
    }

    public List<SkillScript> getScripts() {
        return scripts;
    }

    public SkillScript getScript(String name) {
        return scripts.stream()
                .filter(s -> s.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    public String getReference(String name) {
        return references.get(name);
    }

    public List<String> getReferenceNames() {
        return new ArrayList<>(references.keySet());
    }

    /**
     * 生成该技能在系统 prompt 中的描述文本
     */
    public String toPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("### ").append(name).append("\n");
        sb.append("描述: ").append(description).append("\n");
        if (!scripts.isEmpty()) {
            sb.append("包含脚本: ");
            for (int j = 0; j < scripts.size(); j++) {
                if (j > 0) sb.append(", ");
                sb.append(scripts.get(j).getName());
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
