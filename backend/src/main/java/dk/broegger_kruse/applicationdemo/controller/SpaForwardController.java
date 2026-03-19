package dk.broegger_kruse.applicationdemo.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {

    @GetMapping({"/", "/{path:^(?!api$)[^.]*}"})
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}

