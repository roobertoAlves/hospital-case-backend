package controllers;

import database.Sql;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import models.Agenda;
import models.AgendaExames;
import models.ConsultaAuditoria;
import models.Notificacao;
import models.PedidoMedico;
import models.Portal;
import models.Receita;
import models.Vacina;
import utils.JsonResponse;
import utils.RateLimiter;
import utils.RequestHelper;
import utils.Sanitize;

/**
 * ZTF_J2EE - PortalController (portal do paciente)
 *
 * Publico (sem login): catalogo e cadastro. Paciente logado (grupo paciente + portal.paciente,
 * strictGroup): os proprios agendamentos, exames, pedidos, vacinas e receitas. Toda acao
 * resolve a ficha pela SESSAO (pacientes.id_usuario), nunca por um id vindo do navegador; um
 * id de consulta/exame de outra pessoa responde "nao encontrado" (RN-POR-03).
 */
@WebServlet(name = "PortalController", urlPatterns = {"/api/publico/*", "/api/portal/*"})
public class PortalController extends Controller {

    private static final long serialVersionUID = 1L;
    private static final RateLimiter LIMITE_CADASTRO = new RateLimiter(5, 15 * 60 * 1000L);
    private static final RateLimiter LIMITE_CODIGO = new RateLimiter(10, 15 * 60 * 1000L);

    // ------------------------------------------------------------------ publico

    /** GET /api/publico/catalogo */
    public void catalogo(HttpServletRequest request, HttpServletResponse response) throws IOException {
        jsonSuccess(response, new Portal().catalogo(), null);
    }

    /** POST /api/portal/cadastro */
    public void cadastro(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!LIMITE_CADASTRO.tryAcquire(request.getRemoteAddr())) {
            jsonError(response, 429, "ERR_TOO_MANY_REQUESTS", "Muitas tentativas. Aguarde alguns minutos.");
            return;
        }
        Portal.Cadastro c = new Portal().cadastrar(getPayload(request));
        if (!c.resultado().ok()) {
            JsonResponse.error(response, 422, "ERR_VALIDATION", c.resultado().erro(), c.resultado().erros());
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        if (c.codigo() != null) {
            Notificacao.codigoVinculo(c.emailFicha(), c.nome(), c.codigo(), (int) c.resultado().id());
            data.put("vinculo_pendente", true);
            data.put("email_ficha", ConsultaAuditoria.emailMascarado(c.emailFicha()));
            jsonSuccess(response, data, "Conta criada. Seu CPF já tinha cadastro: enviamos um código para o e-mail da ficha.");
            return;
        }
        data.put("vinculo_pendente", false);
        jsonSuccess(response, data, "Conta criada. Entre com o seu e-mail e senha.");
    }

    // ------------------------------------------------------------------ paciente: conta

    /** GET /api/portal/resumo */
    public void resumo(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int idUsuario = RequestHelper.getSessionUserId(request);
        Integer idPaciente = Portal.pacienteDoUsuario(idUsuario);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("vinculado", idPaciente != null);
        if (idPaciente == null) {
            Map<String, Object> v = Sql.um("SELECT p.email FROM vinculos_portal v JOIN pacientes p ON p.id_paciente = v.id_paciente "
                    + "WHERE v.id_usuario = ? ORDER BY v.id_vinculo DESC LIMIT 1", idUsuario);
            data.put("email_ficha", v == null ? null : ConsultaAuditoria.emailMascarado(v.get("email")));
            jsonSuccess(response, data, null);
            return;
        }
        data.putAll(new Portal().resumo(idUsuario, idPaciente));
        jsonSuccess(response, data, null);
    }

    /** POST /api/portal/vincular { codigo } */
    public void vincular(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int idUsuario = RequestHelper.getSessionUserId(request);
        if (!LIMITE_CODIGO.tryAcquire("u" + idUsuario)) {
            jsonError(response, 429, "ERR_TOO_MANY_REQUESTS", "Muitas tentativas. Aguarde alguns minutos.");
            return;
        }
        responder(response, new Portal().vincular(idUsuario, RequestHelper.getParam(request, "codigo", "")), "Pronto! Sua conta está ligada à sua ficha.");
    }

    /** POST /api/portal/vincular/reenviar */
    public void reenviar(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int idUsuario = RequestHelper.getSessionUserId(request);
        String[] r = new Portal().reenviarCodigo(idUsuario);
        if (r == null) {
            jsonError(response, 409, "ERR_SEM_VINCULO", "Não há vínculo pendente para esta conta.");
            return;
        }
        if (r[0] == null) {
            jsonError(response, 429, "ERR_TOO_MANY_REQUESTS", "Você já pediu vários códigos. Tente de novo em uma hora.");
            return;
        }
        Notificacao.codigoVinculo(r[1], String.valueOf(RequestHelper.getSessionUser(request).get("nome")), r[0], idUsuario);
        jsonSuccess(response, Map.of("email_ficha", ConsultaAuditoria.emailMascarado(r[1])), "Enviamos um novo código.");
    }

    // ------------------------------------------------------------------ paciente: consultas

    /** GET /api/portal/consultas */
    public void consultas(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer pac = paciente(request, response);
        if (pac == null) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("consultas", new Portal().consultas(pac));
        data.put("especialidades", new Agenda().especialidadesAtivas());
        data.put("hoje", Agenda.agora().toLocalDate());
        jsonSuccess(response, data, null);
    }

    /** POST /api/portal/consultas { id_especialidade, id_medico, data, hora } */
    public void agendarConsulta(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer pac = paciente(request, response);
        if (pac == null) {
            return;
        }
        int idUsuario = RequestHelper.getSessionUserId(request);
        LocalDateTime quando = AgendaController.dataHora(request);
        int esp = Sanitize.intVal(RequestHelper.getParam(request, "id_especialidade", "0"), 0);
        int med = Sanitize.intVal(RequestHelper.getParam(request, "id_medico", "0"), 0);
        if (esp <= 0 || med <= 0 || quando == null) {
            jsonError(response, 400, "ERR_VALIDATION", "Escolha especialidade, médico, data e horário.");
            return;
        }
        Portal.Resultado r = new Portal().agendarConsulta(pac, med, esp, quando, idUsuario);
        if (r.ok()) {
            Notificacao.consultaAgendada(new Agenda().resumoParaAviso(r.id()), r.id(), idUsuario);
        }
        responder(response, r, "Consulta marcada. Você recebe a confirmação por e-mail.");
    }

    /** POST /api/portal/consultas/{id}/confirmar */
    public void confirmarConsulta(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer pac = paciente(request, response);
        if (pac == null) {
            return;
        }
        responder(response, new Portal().confirmarConsulta(PacienteController.idDaRota(request), pac, RequestHelper.getSessionUserId(request)),
                "Presença confirmada.");
    }

    /** POST /api/portal/consultas/{id}/cancelar { motivo } */
    public void cancelarConsulta(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer pac = paciente(request, response);
        if (pac == null) {
            return;
        }
        int id = PacienteController.idDaRota(request);
        int idUsuario = RequestHelper.getSessionUserId(request);
        if (!Portal.consultaDoPaciente(id, pac) || Sql.um("SELECT 1 FROM consultas WHERE id_consulta = ? AND status = 'agendada'", id) == null) {
            jsonError(response, 404, "ERR_NOT_FOUND", "Consulta não encontrada ou já em andamento.");
            return;
        }
        Agenda.Resultado r = new Agenda().cancelar(id, RequestHelper.getParam(request, "motivo", ""), idUsuario);
        if (r.ok()) {
            Notificacao.consultaCancelada(new Agenda().resumoParaAviso(id), id, idUsuario);
        }
        responderAgenda(response, r, "Consulta cancelada.");
    }

    /** POST /api/portal/consultas/{id}/remarcar { id_medico, data, hora, motivo } */
    public void remarcarConsulta(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer pac = paciente(request, response);
        if (pac == null) {
            return;
        }
        int id = PacienteController.idDaRota(request);
        int idUsuario = RequestHelper.getSessionUserId(request);
        LocalDateTime quando = AgendaController.dataHora(request);
        int med = Sanitize.intVal(RequestHelper.getParam(request, "id_medico", "0"), 0);
        if (!Portal.consultaDoPaciente(id, pac)) {
            jsonError(response, 404, "ERR_NOT_FOUND", "Consulta não encontrada.");
            return;
        }
        if (med <= 0 || quando == null) {
            jsonError(response, 400, "ERR_VALIDATION", "Escolha o médico, a nova data e o horário.");
            return;
        }
        Agenda.Resultado r = new Agenda().remarcar(id, med, quando, RequestHelper.getParam(request, "motivo", "pelo portal"), idUsuario);
        if (r.ok()) {
            Sql.executar("UPDATE consultas SET origem = 'portal' WHERE id_consulta = ?", r.id());
            Notificacao.consultaRemarcada(new Agenda().resumoParaAviso(r.id()), r.id(), idUsuario);
        }
        responderAgenda(response, r, "Consulta remarcada.");
    }

    // ------------------------------------------------------------------ paciente: exames e pedidos

    /** GET /api/portal/exames */
    public void exames(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer pac = paciente(request, response);
        if (pac == null) {
            return;
        }
        Portal p = new Portal();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("exames", p.exames(pac));
        data.put("solicitacoes", p.solicitacoesAbertas(pac));
        data.put("pedidos", new PedidoMedico().doPaciente(pac));
        data.put("tipos", Sql.lista("SELECT t.id_tipo_exame, t.nome, t.duracao_min, t.preparo, t.exige_pedido, l.nome AS local "
                + "FROM tipos_exame t JOIN locais_exame l ON l.id_local = t.id_local WHERE t.ativo = 1 AND l.ativo = 1 ORDER BY t.nome"));
        data.put("hoje", Agenda.agora().toLocalDate());
        jsonSuccess(response, data, null);
    }

    /** POST /api/portal/exames { id_tipo_exame, data, hora, id_solicitacao?, id_pedido? } */
    public void agendarExame(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer pac = paciente(request, response);
        if (pac == null) {
            return;
        }
        int idUsuario = RequestHelper.getSessionUserId(request);
        int tipo = Sanitize.intVal(RequestHelper.getParam(request, "id_tipo_exame", "0"), 0);
        int sol = Sanitize.intVal(RequestHelper.getParam(request, "id_solicitacao", "0"), 0);
        int ped = Sanitize.intVal(RequestHelper.getParam(request, "id_pedido", "0"), 0);
        LocalDateTime quando = AgendaController.dataHora(request);
        if (tipo <= 0 || quando == null) {
            jsonError(response, 400, "ERR_VALIDATION", "Escolha o exame, a data e o horário.");
            return;
        }
        Portal.Resultado r = new Portal().agendarExame(pac, tipo, quando, sol > 0 ? sol : null, ped > 0 ? ped : null, idUsuario);
        if (r.ok()) {
            Notificacao.exameAgendado(new AgendaExames().resumoParaAviso(r.id()), idUsuario);
        }
        responder(response, r, ped > 0 ? "Exame marcado. A recepção vai conferir o pedido anexado." : "Exame marcado.");
    }

    /** POST /api/portal/exames/{id}/cancelar { motivo } */
    public void cancelarExame(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer pac = paciente(request, response);
        if (pac == null) {
            return;
        }
        int id = PacienteController.idDaRota(request);
        if (!Portal.exameDoPaciente(id, pac)) {
            jsonError(response, 404, "ERR_NOT_FOUND", "Exame não encontrado.");
            return;
        }
        responderAgenda(response, new AgendaExames().cancelar(id, RequestHelper.getParam(request, "motivo", ""),
                RequestHelper.getSessionUserId(request)), "Exame cancelado.");
    }

    /** POST /api/portal/pedidos { nome_arquivo, conteudo (data URI), id_tipo_exame?, observacao? } */
    public void enviarPedido(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer pac = paciente(request, response);
        if (pac == null) {
            return;
        }
        Portal.Resultado r = new PedidoMedico().enviar(pac, getPayload(request), RequestHelper.getSessionUserId(request));
        if (!r.ok()) {
            JsonResponse.error(response, 422, "ERR_VALIDATION", r.erro(), r.erros());
            return;
        }
        jsonSuccess(response, Map.of("id_pedido", r.id()), "Pedido anexado. A recepção confere em seguida.");
    }

    /** GET /api/portal/pedidos/{id}/arquivo */
    public void arquivoPedido(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer pac = paciente(request, response);
        if (pac == null) {
            return;
        }
        enviarArquivo(response, new PedidoMedico().arquivo(PacienteController.idDaRota(request), pac));
    }

    /** Entrega o anexo como download, sem deixar o navegador interpretar nada (nosniff + sandbox). */
    static void enviarArquivo(HttpServletResponse response, PedidoMedico.Arquivo a) throws IOException {
        if (a == null) {
            JsonResponse.error(response, 404, "ERR_NOT_FOUND", "Arquivo não encontrado.", null);
            return;
        }
        response.setContentType(a.mime());
        response.setHeader("Content-Disposition", "attachment; filename=\"pedido\"; filename*=UTF-8''"
                + java.net.URLEncoder.encode(a.nome(), StandardCharsets.UTF_8).replace("+", "%20"));
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Security-Policy", "sandbox; default-src 'none'");
        response.setHeader("Cache-Control", "private, no-store");
        response.setContentLengthLong(a.arquivo().length());
        try (OutputStream out = response.getOutputStream()) {
            Files.copy(a.arquivo().toPath(), out);
        }
    }

    // ------------------------------------------------------------------ paciente: vacinas e receitas

    /** GET /api/portal/vacinas */
    public void vacinas(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer pac = paciente(request, response);
        if (pac == null) {
            return;
        }
        Vacina v = new Vacina();
        Map<String, Object> data = new LinkedHashMap<>(v.cartao(pac));
        data.put("catalogo", v.catalogo());
        data.put("hoje", Agenda.agora().toLocalDate());
        jsonSuccess(response, data, null);
    }

    /** POST /api/portal/vacinas { id_vacina, data, hora } */
    public void agendarVacina(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer pac = paciente(request, response);
        if (pac == null) {
            return;
        }
        int vacina = Sanitize.intVal(RequestHelper.getParam(request, "id_vacina", "0"), 0);
        responderAgenda(response, new Vacina().agendar(pac, vacina, AgendaController.dataHora(request), RequestHelper.getSessionUserId(request), "portal"),
                "Vacina agendada. Leve um documento com foto.");
    }

    /** POST /api/portal/vacinas/{id}/cancelar { motivo } */
    public void cancelarVacina(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer pac = paciente(request, response);
        if (pac == null) {
            return;
        }
        responderAgenda(response, new Vacina().cancelar(PacienteController.idDaRota(request), RequestHelper.getParam(request, "motivo", ""),
                RequestHelper.getSessionUserId(request), pac), "Agendamento cancelado.");
    }

    /** GET /api/portal/receitas */
    public void receitas(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer pac = paciente(request, response);
        if (pac == null) {
            return;
        }
        auditar(request, "portal.receitas_visualizadas", "pacientes", pac, pac, null);
        jsonSuccess(response, Map.of("receitas", new Receita().doPaciente(pac)), null);
    }

    /** GET /api/portal/receitas/{id} */
    public void receita(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer pac = paciente(request, response);
        if (pac == null) {
            return;
        }
        Map<String, Object> r = new Receita().paraImpressaoDoPaciente(PacienteController.idDaRota(request), pac);
        if (r == null) {
            jsonError(response, 404, "ERR_NOT_FOUND", "Receita não encontrada.");
            return;
        }
        auditar(request, "portal.receita_visualizada", "receitas", r.get("id_receita"), pac, null);
        jsonSuccess(response, Map.of("receita", r), null);
    }

    // ------------------------------------------------------------------ recepcao

    /** POST /api/pacientes/{id}/conta { email } (ficha sem e-mail: a recepcao liga a conta). */
    public void vincularNaRecepcao(HttpServletRequest request, HttpServletResponse response) throws IOException {
        responder(response, new Portal().vincularNaRecepcao(PacienteController.idDaRota(request), RequestHelper.getParam(request, "email", ""),
                RequestHelper.getSessionUserId(request)), "Conta do portal ligada à ficha.");
    }

    // ------------------------------------------------------------------ auxiliares

    /** Ficha ligada a sessao; sem ficha responde 409 e devolve null. */
    private Integer paciente(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer pac = Portal.pacienteDoUsuario(RequestHelper.getSessionUserId(request));
        if (pac == null) {
            jsonError(response, 409, "ERR_SEM_FICHA", "Sua conta ainda não está ligada a uma ficha. Confirme o código enviado por e-mail ou procure a recepção.");
        }
        return pac;
    }

    private void responder(HttpServletResponse response, Portal.Resultado r, String ok) throws IOException {
        if (r.ok()) {
            jsonSuccess(response, Map.of("id", r.id()), ok);
        } else if (!r.erros().isEmpty()) {
            JsonResponse.error(response, 422, "ERR_VALIDATION", r.erro(), r.erros());
        } else {
            jsonError(response, 409, "ERR_PORTAL", r.erro());
        }
    }

    private void responderAgenda(HttpServletResponse response, Agenda.Resultado r, String ok) throws IOException {
        if (r.ok()) {
            jsonSuccess(response, Map.of("id", r.id()), ok);
        } else {
            jsonError(response, 409, "ERR_AGENDA", r.erro());
        }
    }
}
