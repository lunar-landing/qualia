package com.lunarlanding.qualia.core.tool.impl.skill;

import com.alibaba.fastjson.JSON;
import com.lunarlanding.qualia.core.agent.ReActAgent;
import com.lunarlanding.qualia.core.skill.engine.NodeJsScriptEngine;
import com.lunarlanding.qualia.core.skill.engine.PythonScriptEngine;
import com.lunarlanding.qualia.core.skill.engine.ScriptEngine;
import com.lunarlanding.qualia.core.skill.Skill;
import com.lunarlanding.qualia.core.skill.SkillScript;
import com.lunarlanding.qualia.core.tool.FunctionTool;
import com.lunarlanding.qualia.core.tool.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 技能脚本执行器
 * 按脚本扩展名分发到对应的 ScriptEngine 执行（.py → Python，.js → Node）
 */
public class SkillScriptRunner extends FunctionTool {

    private static final Logger logger = LoggerFactory.getLogger(SkillScriptRunner.class);

    private final ReActAgent agent;
    private final ScriptEngine pythonEngine;
    private final ScriptEngine nodeEngine;

    public SkillScriptRunner(ReActAgent agent) {
        this.agent = agent;
        this.pythonEngine = new PythonScriptEngine();
        this.nodeEngine = new NodeJsScriptEngine();
        this.setName("skill-script-runner");
        this.setDescription("执行技能的脚本工具");
        this.setParameters(new Parameter[]{
                new Parameter("skill_name", "技能名称", "string", true),
                new Parameter("script_name", "脚本名称", "string", true),
                new Parameter("arguments", "脚本参数(JSON格式)", "string", false)
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public String execute(Map<String, Object> arguments) {
        String skillName = (String) arguments.get("skill_name");
        String scriptName = (String) arguments.get("script_name");
        String argsJson = (String) arguments.get("arguments");

        Skill skill = agent.findSkill(skillName);
        if (skill == null) {
            return "错误：找不到技能 '" + skillName + "'";
        }

        // 查找脚本
        SkillScript script = skill.getScript(scriptName);
        if (script == null) {
            return "错误：找不到脚本 '" + scriptName + "'。可用脚本: " +
                    skill.getScripts().stream()
                            .map(SkillScript::getName)
                            .reduce((a, b) -> a + ", " + b)
                            .orElse("无");
        }

        // 解析参数
        Map<String, Object> scriptArgs = new HashMap<>();
        if (argsJson != null && !argsJson.isEmpty()) {
            try {
                scriptArgs = JSON.parseObject(argsJson, Map.class);
            } catch (Exception e) {
                return "错误：参数格式无效，需要 JSON 格式";
            }
        }

        // 执行脚本（按扩展名选择引擎）
        ScriptEngine engine = engineFor(script);
        ScriptEngine.ScriptResult result = engine.execute(script.getScriptPath(), scriptArgs);
        if (result.success()) {
            return result.output();
        } else {
            String errorMsg = result.error() != null ? result.error() : "未知错误";
            logger.error("脚本执行失败 [{}]: {}", scriptName, errorMsg);
            return errorMsg;
        }
    }

    /**
     * 根据脚本文件扩展名选择执行引擎，默认 Python
     */
    private ScriptEngine engineFor(SkillScript script) {
        String path = script.getScriptPath().toString().toLowerCase();
        if (path.endsWith(".js")) {
            return nodeEngine;
        }
        return pythonEngine;
    }
}
