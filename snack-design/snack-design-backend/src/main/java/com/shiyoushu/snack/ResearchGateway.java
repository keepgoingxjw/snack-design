package com.shiyoushu.snack;

import java.util.List;
import static com.shiyoushu.snack.ResearchModels.*;

public interface ResearchGateway {
    boolean configured();
    AiResult research(List<String> names, String foodCategory);
    String model();
    default String configurationError() { return "AI服务未配置，请检查后端设置"; }
}
