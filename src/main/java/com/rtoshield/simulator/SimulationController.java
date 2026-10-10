package com.rtoshield.simulator;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * STUB (M11a, Person A).
 * TODO(A): DataGenerator makes N fake orders with hidden true outcomes (plus random noise that no factor
 * explains). engine=OFF ships everything; engine=ON uses OrderService.createAndProcess and
 * DeliveryStateService.handle. Report RTO % before vs after, action costs, and NET rupees saved
 * AFTER subtracting margin lost from genuine customers who dropped off.
 */
@RestController
public class SimulationController {

    @PostMapping("/simulator/run")
    public Map<String, Object> run(@RequestParam(defaultValue = "500") int orders,
                                   @RequestParam(defaultValue = "ON") String engine) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "NOT_IMPLEMENTED");
        out.put("module", "M11a, Person A");
        out.put("orders", orders);
        out.put("engine", engine);
        return out;
    }

    @PostMapping("/simulator/reset")
    public Map<String, Object> reset() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "NOT_IMPLEMENTED");
        out.put("module", "M11a, Person A");
        return out;
    }
}
