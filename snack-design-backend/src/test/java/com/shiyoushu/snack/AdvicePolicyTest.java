package com.shiyoushu.snack;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static com.shiyoushu.snack.ResearchModels.*;
import static org.junit.jupiter.api.Assertions.*;

class AdvicePolicyTest {
    private final ObjectMapper json = new ObjectMapper();
    private static final String URL = "https://www.nhc.gov.cn/test-advice.html";
    @Test void unsupportedSafetyAssurancesAreNeverAccepted() throws Exception {
        var input = json.readTree("{\"action\":\"safe\",\"audience\":\"所有人\",\"reason\":\"吃一点没事\",\"sourceUrls\":[]}");
        assertEquals("check_first", AdvicePolicy.validate(input, List.of()).action());
    }
    @Test void avoidanceRequiresSpecificPopulationAndVerifiedSource() throws Exception {
        var input = json.readTree("{\"action\":\"avoid_for_group\",\"audience\":\"对该成分过敏的人群\",\"reason\":\"该人群需避开相应过敏原\",\"sourceUrls\":[\""+URL+"\"]}");
        assertEquals("check_first", AdvicePolicy.validate(input,List.of(new Source("测试",URL,"测试引用",false))).action());
        var advice = AdvicePolicy.validate(input,List.of(new Source("测试",URL,"测试引用",true)));
        assertEquals("avoid_for_group",advice.action());
        assertTrue(advice.title().contains("相关人群"));
        assertFalse(advice.title().equals("不能吃"));
        ((com.fasterxml.jackson.databind.node.ObjectNode)input).put("audience","所有人");
        assertEquals("check_first",AdvicePolicy.validate(input,List.of(new Source("测试",URL,"测试引用",true))).action());
    }
    @Test void oldReportsAndFailuresCannotBecomeSafeToEatVerdicts() {
        var finding = new Finding("测试配料","测试用途","测试","测试","测试",null,List.of(),"未审核");
        var result = AdvicePolicy.overall(List.of(finding),"done");
        assertEquals("check_first",result.action());
        assertTrue(result.conditions().contains("不能"));
        assertNull(AdvicePolicy.overall(List.of(),"failed"));
    }
}
