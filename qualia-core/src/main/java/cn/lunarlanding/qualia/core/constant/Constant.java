package cn.lunarlanding.qualia.core.constant;

public class Constant {

    public static String REWRITE_QUERY_PROMPT = """

        给定一个问题，生成 1-2 个不同角度但相关的查询。请严格按照以下JSON格式返回，不要有任何其他文字：

        ```json
        {
            "queries": [
                "查询1",
                "查询2",
                "查询3"
            ]
        }
        ```

        原始问题: %s

        请按照上述JSON格式返回你的答案：
    """;

    public static String REACT_PROMPT_NO_SKILLS = """
    
            你能够使用工具来回答用户的问题。你通过迭代的思考-行动-观察过程来解决问题。
            
            ## 工作流程
            
            1. 先分析用户问题;
            2. 使用全局工具来处理当前问题;
            3. 调用工具或直接生成最终回答;
            
            ## 输出格式

            你的输出必须严格遵循以下 JSON 格式 ！！！

            当需要调用工具时（支持批量调用多个工具）：

            ```json
            {
                "type": "action",
                "thought": "你的思考过程，分析当前问题并决定使用哪些工具",
                "actions": [
                    {
                        "name": "工具名1",
                        "arguments": {"参数名": "参数值"}
                    },
                    {
                        "name": "工具名2",
                        "arguments": {"参数名": "参数值"}
                    }
                ]
            }
            ```

            当满足回答条件时：

            ```json
            {
                "type": "answer",
                "thought": "你的思考过程，总结是否满足回答条件"
            }
            ```

            ## 注意事项
            
            - 必须是有效的 JSON 格式,不包含其他解释性文本;
            - 严格输出 JSON 格式数据，不需要额外任何说明信息 ！！！
            - thought 字段必须和问题的语言完全一致, 中文需要细分简体中文和繁体中文;
            - 思考 thought 字段长度控制在 100 个字符以内;
            - 必须确保工具调用的参数正确;
            
            """;

    public static String LANGUAGE_DETECT_PROMPT = """
            判断以下文本的主要语言，只需要输出语言，如果是中文需要明确区分简体中文还是繁体中文；
            
            %s
            """;

    public static String SUGGESTIONS_PROMPT = """

            基于以下对话，生成 3 个用户可能下一步会问的后续问题。
            
            %s
            
            用户问题：%s
            
            助手回答：%s
            
            若适合延伸，严格按以下 JSON 数组格式返回 3 个问题，不要有任何其他内容：

            ["问题1", "问题2", "问题3"]

            若不适合延伸，返回空数组：
            
            []
            
            """;

}
