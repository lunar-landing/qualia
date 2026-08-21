package cn.lunarlanding.qualia.claw;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 配置管理类
 *
 * 与 qualia-code 的差异：
 * 1. 配置目录为 ~/.qualia/claw/（产品目录隔离，不与 qualia-code 混用），
 *    含 config.json 模型/MCP/智能体配置与 skills/ 全局技能
 * 2. 配置不绑定单一工作区（工作区归属各智能体定义）
 * 3. 新增 agents 数组，持久化所有智能体定义
 */
public class ClawConfig {

    private static final Logger logger = LoggerFactory.getLogger(ClawConfig.class);

    /** 环境变量正则：${ENV_VAR} */
    private static final Pattern ENV_VAR_PATTERN = Pattern.compile("\\$\\{([a-zA-Z_][a-zA-Z0-9_]*)}");

    /** Qualia 主目录（旧版共享资源的路径根，仅用于迁移与种子复制） */
    public static final Path QUALIA_HOME = Path.of(System.getProperty("user.home"), ".qualia");

    /** 产品配置目录 */
    public static final Path GLOBAL_CONFIG_DIR = QUALIA_HOME.resolve("claw");

    /** 产品配置文件 */
    public static final Path GLOBAL_CONFIG_FILE = GLOBAL_CONFIG_DIR.resolve("config.json");

    /** 产品全局技能目录（本产品所有智能体共享） */
    public static final Path GLOBAL_SKILLS_DIR = GLOBAL_CONFIG_DIR.resolve("skills");

    /** 旧版配置文件（产品目录隔离前的路径，首启自动迁移） */
    private static final Path LEGACY_CONFIG_FILE = QUALIA_HOME.resolve("qualia-claw.json");

    /** qualia-code 配置文件（首启种子复制来源，优先新路径，回退旧路径） */
    private static final Path CODE_CONFIG_FILE = QUALIA_HOME.resolve("code").resolve("config.json");
    private static final Path LEGACY_CODE_CONFIG_FILE = QUALIA_HOME.resolve("qualia-code.json");

    private final String defaultModel;
    private final List<ClawModelConfig> models;
    private final List<ClawMcpServerConfig> mcpServers;
    private final List<String> disabledSkills;
    private final List<ClawAgentDefinition> agents;

    public ClawConfig(String defaultModel, List<ClawModelConfig> models, List<ClawMcpServerConfig> mcpServers,
                      List<String> disabledSkills, List<ClawAgentDefinition> agents) {
        this.defaultModel = defaultModel;
        this.models = models != null ? models : new ArrayList<>();
        this.mcpServers = mcpServers != null ? mcpServers : new ArrayList<>();
        this.disabledSkills = disabledSkills != null ? disabledSkills : new ArrayList<>();
        this.agents = agents != null ? agents : new ArrayList<>();
    }

    /**
     * 获取当前模型配置
     */
    public ClawModelConfig getCurrentModel() {
        if (models.isEmpty()) {
            return null;
        }
        if (defaultModel != null && !defaultModel.isEmpty()) {
            for (ClawModelConfig model : models) {
                if (defaultModel.equals(model.getName())) {
                    return model;
                }
            }
        }
        return models.get(0);
    }

    /**
     * 获取当前模型的 API Key
     *
     * @throws IllegalStateException 配置文件不存在或未配置apiKey时抛出
     */
    public String getApiKey() {
        ClawModelConfig model = getCurrentModel();
        if (model == null) {
            throw new IllegalStateException("配置文件不存在或未配置模型，请先运行 init 命令初始化配置");
        }
        if (model.getApiKey() == null || model.getApiKey().isEmpty()) {
            throw new IllegalStateException("模型 " + model.getName() + " 未配置 apiKey");
        }
        return model.getApiKey();
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public List<ClawModelConfig> getModels() {
        return models;
    }

    public List<ClawMcpServerConfig> getMcpServers() {
        return mcpServers;
    }

    public List<String> getDisabledSkills() {
        return disabledSkills;
    }

    public List<ClawAgentDefinition> getAgents() {
        return agents;
    }

    /**
     * 按模型名称获取配置
     *
     * @param modelName 模型名称
     * @return 模型配置，如果不存在返回null
     */
    public ClawModelConfig getModelByName(String modelName) {
        if (modelName == null || modelName.isEmpty()) {
            return null;
        }
        return models.stream()
                .filter(m -> modelName.equals(m.getName()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取所有模型名称列表
     */
    public List<String> getModelNames() {
        return models.stream()
                .map(ClawModelConfig::getName)
                .collect(Collectors.toList());
    }

    // ========== 配置加载方法 ==========

    /**
     * 从全局配置文件加载配置（含智能体定义列表）
     */
    public static ClawConfig load() {
        // 旧路径配置先迁移到产品目录，再走首启种子逻辑
        migrateLegacyConfigIfNeeded();

        // 首启种子：无 claw 配置但存在 qualia-code 配置时，复用其模型/MCP 配置，避免重复录入
        seedFromCodeConfigIfNeeded();

        JSONObject config = loadJsonFile(GLOBAL_CONFIG_FILE);

        String defaultModel = config.getString("defaultModel");
        List<ClawModelConfig> models = parseModels(config.getJSONArray("models"));
        List<ClawMcpServerConfig> mcpServers = parseMcpServers(config.getJSONArray("mcpServers"));
        List<String> disabledSkills = parseStringList(config.getJSONArray("disabledSkills"));
        List<ClawAgentDefinition> agents = parseAgents(config.getJSONArray("agents"));

        // 解析环境变量
        resolveEnvVars(models, mcpServers);

        return new ClawConfig(defaultModel, models, mcpServers, disabledSkills, agents);
    }

    /**
     * 持久化智能体定义列表（只改 agents 字段，保留其余配置原样）
     */
    public static synchronized void saveAgents(List<ClawAgentDefinition> agents) {
        try {
            JSONObject config = loadJsonFile(GLOBAL_CONFIG_FILE);
            JSONArray arr = new JSONArray();
            for (ClawAgentDefinition def : agents) {
                JSONObject obj = new JSONObject();
                obj.put("id", def.getId());
                obj.put("name", def.getName());
                obj.put("emoji", def.getEmoji());
                obj.put("role", def.getRole());
                obj.put("workspacePath", def.getWorkspacePath());
                obj.put("model", def.getModel());
                // 白名单字段：仅非 null 时写入（null = 引用全部，保持字段缺失以兼容存量语义）
                if (def.getSkills() != null) {
                    obj.put("skills", def.getSkills());
                }
                if (def.getMcpServers() != null) {
                    obj.put("mcpServers", def.getMcpServers());
                }
                // 智能体级工具禁用名单：仅非 null 时写入（null = 不禁用，保持字段缺失）
                if (def.getDisabledTools() != null) {
                    obj.put("disabledTools", def.getDisabledTools());
                }
                obj.put("createdAt", def.getCreatedAt());
                arr.add(obj);
            }
            config.put("agents", arr);
            Files.createDirectories(GLOBAL_CONFIG_DIR);
            Files.writeString(GLOBAL_CONFIG_FILE, JSON.toJSONString(config, true), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("保存智能体定义失败: {}", e.getMessage());
        }
    }

    /**
     * 旧版资源迁移到产品目录（新路径已存在时跳过，原文件保留）：
     * qualia-claw.json 配置、全局技能目录
     */
    public static void migrateLegacyConfigIfNeeded() {
        try {
            // 配置文件
            if (!Files.exists(GLOBAL_CONFIG_FILE) && Files.exists(LEGACY_CONFIG_FILE)) {
                Files.createDirectories(GLOBAL_CONFIG_DIR);
                Files.copy(LEGACY_CONFIG_FILE, GLOBAL_CONFIG_FILE);
                logger.info("已将旧配置迁移到: {}", GLOBAL_CONFIG_FILE);
            }
            // 全局技能目录（旧版两产品共享，各产品各自复制一份）
            Path legacySkills = QUALIA_HOME.resolve("skills");
            if (!Files.exists(GLOBAL_SKILLS_DIR) && Files.isDirectory(legacySkills)) {
                copyDirectory(legacySkills, GLOBAL_SKILLS_DIR);
                logger.info("已将全局技能迁移到: {}", GLOBAL_SKILLS_DIR);
            }
        } catch (IOException e) {
            logger.warn("迁移旧版资源失败: {}", e.getMessage());
        }
    }

    /**
     * 递归复制目录
     */
    private static void copyDirectory(Path source, Path target) throws IOException {
        Files.createDirectories(target);
        try (var stream = Files.walk(source)) {
            for (Path src : (Iterable<Path>) stream::iterator) {
                Path dest = target.resolve(source.relativize(src).toString());
                if (Files.isDirectory(src)) {
                    Files.createDirectories(dest);
                } else {
                    Files.copy(src, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /**
     * 无 claw 配置但存在 qualia-code 配置时，种子复制模型/MCP/禁用项（不带 agents）
     */
    private static void seedFromCodeConfigIfNeeded() {
        if (Files.exists(GLOBAL_CONFIG_FILE) || Files.exists(LEGACY_CONFIG_FILE)) {
            return;
        }
        Path source = Files.exists(CODE_CONFIG_FILE) ? CODE_CONFIG_FILE
                : Files.exists(LEGACY_CODE_CONFIG_FILE) ? LEGACY_CODE_CONFIG_FILE : null;
        if (source == null) {
            return;
        }
        try {
            JSONObject code = loadJsonFile(source);
            JSONObject seed = new JSONObject();
            for (String key : List.of("version", "defaultModel", "models", "mcpServers", "disabledSkills")) {
                if (code.containsKey(key)) {
                    seed.put(key, code.get(key));
                }
            }
            seed.put("agents", new JSONArray());
            Files.createDirectories(GLOBAL_CONFIG_DIR);
            Files.writeString(GLOBAL_CONFIG_FILE, JSON.toJSONString(seed, true), StandardCharsets.UTF_8);
            logger.info("已从 {} 种子复制模型配置到 {}", source, GLOBAL_CONFIG_FILE);
        } catch (IOException e) {
            logger.warn("种子复制 qualia-code 配置失败: {}", e.getMessage());
        }
    }

    /**
     * 加载 JSON 文件
     */
    private static JSONObject loadJsonFile(Path filePath) {
        if (!Files.exists(filePath)) {
            return new JSONObject();
        }
        try {
            String content = Files.readString(filePath, StandardCharsets.UTF_8);
            return JSON.parseObject(content);
        } catch (IOException e) {
            System.err.println("读取配置文件失败: " + filePath + " - " + e.getMessage());
            return new JSONObject();
        }
    }

    /**
     * 解析模型配置列表
     */
    private static List<ClawModelConfig> parseModels(JSONArray modelsArray) {
        List<ClawModelConfig> models = new ArrayList<>();
        if (modelsArray == null) {
            return models;
        }
        for (int i = 0; i < modelsArray.size(); i++) {
            JSONObject obj = modelsArray.getJSONObject(i);
            ClawModelConfig model = new ClawModelConfig();
            model.setName(obj.getString("name"));
            model.setProvider(obj.getString("provider"));
            model.setType(obj.getString("type"));
            model.setApiKey(obj.getString("apiKey"));
            model.setModel(obj.getString("model"));
            model.setBaseUrl(obj.getString("baseUrl"));
            models.add(model);
        }
        return models;
    }

    /**
     * 解析 MCP 服务器配置列表
     */
    private static List<ClawMcpServerConfig> parseMcpServers(JSONArray mcpArray) {
        List<ClawMcpServerConfig> servers = new ArrayList<>();
        if (mcpArray == null) {
            return servers;
        }
        for (int i = 0; i < mcpArray.size(); i++) {
            JSONObject obj = mcpArray.getJSONObject(i);
            ClawMcpServerConfig server = new ClawMcpServerConfig();
            server.setName(obj.getString("name"));
            server.setTransport(obj.getString("transport"));
            server.setEnabled(obj.getBooleanValue("enabled"));
            server.setUrl(obj.getString("url"));

            // 解析 headers
            JSONObject headersObj = obj.getJSONObject("headers");
            if (headersObj != null) {
                Map<String, String> headers = new HashMap<>();
                headersObj.forEach((key, value) -> headers.put(key, String.valueOf(value)));
                server.setHeaders(headers);
            }

            servers.add(server);
        }
        return servers;
    }

    /**
     * 解析智能体定义列表
     */
    private static List<ClawAgentDefinition> parseAgents(JSONArray agentsArray) {
        List<ClawAgentDefinition> agents = new ArrayList<>();
        if (agentsArray == null) {
            return agents;
        }
        for (int i = 0; i < agentsArray.size(); i++) {
            JSONObject obj = agentsArray.getJSONObject(i);
            ClawAgentDefinition def = new ClawAgentDefinition();
            def.setId(obj.getString("id"));
            def.setName(obj.getString("name"));
            def.setEmoji(obj.getString("emoji"));
            def.setRole(obj.getString("role"));
            def.setWorkspacePath(obj.getString("workspacePath"));
            def.setModel(obj.getString("model"));
            // 白名单字段缺失保持 null（引用全部），不可归一为空列表
            JSONArray skillsArr = obj.getJSONArray("skills");
            def.setSkills(skillsArr != null ? skillsArr.toJavaList(String.class) : null);
            JSONArray mcpArr = obj.getJSONArray("mcpServers");
            def.setMcpServers(mcpArr != null ? mcpArr.toJavaList(String.class) : null);
            // 工具禁用名单缺失保持 null（不禁用），不可归一为空列表
            JSONArray toolsArr = obj.getJSONArray("disabledTools");
            def.setDisabledTools(toolsArr != null ? toolsArr.toJavaList(String.class) : null);
            def.setCreatedAt(obj.getLongValue("createdAt"));
            agents.add(def);
        }
        return agents;
    }

    /**
     * 解析字符串列表（禁用技能）
     */
    private static List<String> parseStringList(JSONArray array) {
        List<String> list = new ArrayList<>();
        if (array == null) {
            return list;
        }
        for (int i = 0; i < array.size(); i++) {
            list.add(array.getString(i));
        }
        return list;
    }

    /**
     * 解析环境变量替换 ${ENV_VAR}
     */
    private static void resolveEnvVars(List<ClawModelConfig> models, List<ClawMcpServerConfig> mcpServers) {
        // 解析模型配置中的环境变量
        for (ClawModelConfig model : models) {
            if (model.getApiKey() != null) {
                model.setApiKey(resolveEnvVar(model.getApiKey()));
            }
        }

        // 解析 MCP 服务器配置中的环境变量
        for (ClawMcpServerConfig server : mcpServers) {
            if (server.getUrl() != null) {
                server.setUrl(resolveEnvVar(server.getUrl()));
            }
            Map<String, String> headers = server.getHeaders();
            if (headers != null) {
                Map<String, String> resolvedHeaders = new HashMap<>();
                headers.forEach((key, value) -> resolvedHeaders.put(key, resolveEnvVar(value)));
                server.setHeaders(resolvedHeaders);
            }
        }
    }

    /**
     * 解析单个环境变量
     */
    private static String resolveEnvVar(String value) {
        if (value == null) {
            return null;
        }
        Matcher matcher = ENV_VAR_PATTERN.matcher(value);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String envVarName = matcher.group(1);
            String envValue = System.getenv(envVarName);
            if (envValue != null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(envValue));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
