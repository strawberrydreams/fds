package hyun.boot.project.controller;

import hyun.boot.project.dto.ClientDTO;
import hyun.boot.project.entity.Client;
import hyun.boot.project.service.ClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.logging.Logger;

@Controller
@RequiredArgsConstructor
public class ClientController {

    private static final Logger logger = Logger.getLogger(ClientController.class.getName());
    private final ClientService clientService;

    @GetMapping("/")
    public String root() {
        return "redirect:/ClientSelect";
    }

    // =================== SELECT ===================

    @GetMapping("/ClientSelect")
    public String ClientSelect(Model model) {
        model.addAttribute("list", clientService.findAllClients());
        return "client/client_select_view";
    }

    @GetMapping("/ClientSelectDetail")
    public String ClientSelectDetail(@RequestParam("clientNo") Long clientNo, Model model) {
        Client client = clientService.findByClientNo(clientNo);

        ClientDTO dto = new ClientDTO();
        dto.setClientNo(client.getClientNo());
        dto.setUserId(client.getUserId());
        dto.setName(client.getName());
        dto.setEmail(client.getEmail());
        dto.setBirth(client.getBirth());

        model.addAttribute("clientDTO", dto);
        return "client/client_select_detail_view";
    }

    // =================== INSERT ===================

    @GetMapping("/ClientInsert")
    public String ClientInsert(Model model) {
        model.addAttribute("clientDTO", new ClientDTO());
        return "client/client_insert";
    }

    @PostMapping("/ClientInsert")
    public String ClientInsert(ClientDTO dto, Model model) {

        // ================== 아이디 중복 방지 ==================
        if (clientService.existsByUserId(dto.getUserId())) {
            model.addAttribute("error", "duplicate");
            model.addAttribute("clientDTO", dto);
            return "client/client_insert";
        }

        Client client = new Client();
        client.setUserId(dto.getUserId());
        client.setUserPw(dto.getUserPw());
        client.setName(dto.getName());
        client.setEmail(dto.getEmail());
        client.setGender(dto.getGender());
        client.setBirth(dto.getBirth());

        clientService.saveClient(client);

        return "redirect:/ClientSelect";
    }

    // =================== UPDATE ===================

    @GetMapping("/ClientUpdate")
    public String ClientUpdate(@RequestParam("clientNo") Long clientNo, Model model) {

        Client client = clientService.findByClientNo(clientNo);

        ClientDTO dto = new ClientDTO();
        dto.setClientNo(client.getClientNo());
        dto.setUserId(client.getUserId());
        dto.setName(client.getName());
        dto.setEmail(client.getEmail());
        dto.setGender(client.getGender());
        dto.setBirth(client.getBirth());

        model.addAttribute("clientDTO", dto);
        return "client/client_update";
    }

    @PostMapping("/ClientUpdate")
    public String ClientUpdate(ClientDTO dto, Model model) {

        // ================== 수정시 아이디 중복 방지 ==================
        if (clientService.existsByUserIdExceptMe(dto.getUserId(), dto.getClientNo())) {
            model.addAttribute("error", "duplicate");
            model.addAttribute("clientDTO", dto);
            return "client/client_update";
        }

        Client client = clientService.findByClientNo(dto.getClientNo());

        client.setUserId(dto.getUserId());
        client.setName(dto.getName());
        client.setEmail(dto.getEmail());
        client.setGender(dto.getGender());

        if (dto.getUserPw() != null && !dto.getUserPw().isEmpty()) {
            client.setUserPw(dto.getUserPw());
        }

        if (dto.getBirth() != null) {
            client.setBirth(dto.getBirth());
        }

        clientService.saveClient(client);

        return "redirect:/ClientSelect";
    }

    // =================== DELETE ===================

    @GetMapping("/ClientDelete")
    public String ClientDelete(@RequestParam("clientNo") Long clientNo, Model model) {

        Client client = clientService.findByClientNo(clientNo);

        ClientDTO dto = new ClientDTO();
        dto.setClientNo(client.getClientNo());
        dto.setUserId(client.getUserId());

        model.addAttribute("clientDTO", dto);
        return "client/client_delete";
    }

    @PostMapping("/ClientDelete")
    public String ClientDelete(ClientDTO dto) {

        clientService.delete(dto.getClientNo());

        return "client/client_delete_view";
    }

    // =================== 아이디 중복 체크 API ===================

    @ResponseBody
    @GetMapping("/ClientCheckUserId")
    public boolean checkUserId(@RequestParam("userId") String userId,
                               @RequestParam(value = "clientNo", required = false) Long clientNo) {

        if (clientNo == null) {
            return clientService.existsByUserId(userId); // insert
        }

        return clientService.existsByUserIdExceptMe(userId, clientNo); // update
    }
}