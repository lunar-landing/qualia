package cn.lunarlanding.qualia.core.tool.impl.skill;

import cn.lunarlanding.qualia.core.agent.ReActAgent;
import cn.lunarlanding.qualia.core.skill.Skill;
import cn.lunarlanding.qualia.core.tool.FunctionTool;
import cn.lunarlanding.qualia.core.tool.Parameter;

import java.util.Map;

/**
 * 技能附属文档读取器
 */
public class SkillReferenceReader extends FunctionTool {

    private ReActAgent agent;

    public SkillReferenceReader(ReActAgent agent) {
        this.agent = agent;
        this.setName("skill-reference-reader");
        this.setDescription("读取技能的附属文档（如 api.md、examples.md 等）");
        this.setParameters(new Parameter[]{
                new Parameter("skill_name", "技能名称", "string", true),
                new Parameter("file_name", "文档文件名", "string", true)
        });
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        String skillName = (String) arguments.get("skill_name");
        String fileName = (String) arguments.get("file_name");

        Skill skill = agent.findSkill(skillName);
        if (skill == null) {
            return "错误：找不到技能 '" + skillName + "'";
        }

        String content = skill.getReference(fileName);
        if (content == null) {
            return "错误：文档 '" + fileName + "' 不存在。可用文档: " +
                   String.join(", ", skill.getReferenceNames());
        }

        return content;
    }
}
