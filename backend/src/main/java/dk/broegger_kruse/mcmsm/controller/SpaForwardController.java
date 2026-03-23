package dk.broegger_kruse.mcmsm.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {

    // This is a path mapping for the UI, so the backend can serve the frontend app as well as the API
    @GetMapping({"/", "/{path:^(?!api$)[^.]*}"})
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}

