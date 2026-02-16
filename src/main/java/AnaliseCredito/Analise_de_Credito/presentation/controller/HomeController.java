package AnaliseCredito.Analise_de_Credito.presentation.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class HomeController {

    @GetMapping("/")
    public String index() {
        return "index"; // index.html template
    }

    @PostMapping("/perfil/selecionar")
    public String selecionarPerfil(@RequestParam("perfil") String perfil,
                                   HttpSession session) {
        // Save in session
        session.setAttribute("perfil", perfil); // "FINANCEIRO" or "COMERCIAL"

        return "redirect:/analise/kanban";
    }
}
