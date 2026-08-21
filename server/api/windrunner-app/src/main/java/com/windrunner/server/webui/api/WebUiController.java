package com.windrunner.server.webui.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebUiController {

    @GetMapping({"/", "/login", "/app", "/app/**", "/workspace", "/workspace/**"})
    public String forwardWebUi() {
        return "forward:/index.html";
    }
}
