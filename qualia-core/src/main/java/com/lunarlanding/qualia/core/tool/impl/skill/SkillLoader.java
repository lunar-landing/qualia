package com.lunarlanding.qualia.core.tool.impl.skill;

import com.lunarlanding.qualia.core.agent.ReActAgent;
import com.lunarlanding.qualia.core.skill.Skill;
import com.lunarlanding.qualia.core.skill.SkillScript;
import com.lunarlanding.qualia.core.tool.FunctionTool;
import com.lunarlanding.qualia.core.tool.Parameter;

import java.util.Map;

/**
 * 技能加载器，加载指定技能并返回 skill.md 内容、脚本列表和附属文档列表
 */
public class SkillLoader extends FunctionTool {

    private ReActAgent agent;

    public SkillLoader(ReActAgent agent) {
        this.agent = agent;
        this.setName("skill-loader");
        this.setDescription("加载指定技能，返回技能说明、脚本列表和附属文档列表");
        this.setParameters(new Parameter[]{
                new Parameter("skill_name", "要加载的技能名称", "string", true)
        });
    }

    @Override
    public String execute(Map<String, Object> arguments) {

        String skillName = (String) arguments.get("skill_name");
        Skill skill = agent.findSkill(skillName);

        if (skill == null) {
            return "错误：找不到技能 '" + skillName + "'";
        }

        StringBuilder result = new StringBuilder();
        result.append(String.format("技能 '%s' 已加载\n\n", skillName));

        // 返回 skill.md 内容
        if (skill.getContent() != null && !skill.getContent().isEmpty()) {
            result.append("【技能说明】\n");
            result.append(skill.getContent());
            result.append("\n\n");
        }

        // 返回脚本列表
        if (!skill.getScripts().isEmpty()) {
            result.append("【可用脚本】\n");
            for (SkillScript script : skill.getScripts()) {
                result.append(String.format("- %s: %s", script.getName(), script.getDescription()));
                result.append("\n");
            }
            result.append("如需执行脚本，请调用 skill-script-runner\n\n");
        }

        // 返回附属文档列表
        if (!skill.getReferenceNames().isEmpty()) {
            result.append("【附属文档】\n");
            for (String refName : skill.getReferenceNames()) {
                result.append(String.format("- %s\n", refName));
            }
            result.append("如需查看文档，请调用 skill-reference-reader\n");
        }

        return result.toString();
    }
}
